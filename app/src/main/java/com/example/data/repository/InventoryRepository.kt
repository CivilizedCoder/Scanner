package com.example.data.repository

import android.content.Context
import com.example.data.dao.InventoryDao
import com.example.data.dao.OrderDao
import com.example.data.dao.OrderWithItems
import com.example.data.dao.StockLogDao
import com.example.data.model.InventoryItem
import com.example.data.model.Order
import com.example.data.model.OrderItem
import com.example.data.model.StockLog
import com.example.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class InventoryRepository(
    private val context: Context,
    private val inventoryDao: InventoryDao,
    private val orderDao: OrderDao,
    private val stockLogDao: StockLogDao
) {
    val allItems: Flow<List<InventoryItem>> = inventoryDao.getAllItems()
    val lowStockItems: Flow<List<InventoryItem>> = inventoryDao.getLowStockItems()
    val lowStockCount: Flow<Int> = inventoryDao.getLowStockCountFlow()
    val allOrders: Flow<List<OrderWithItems>> = orderDao.getAllOrders()
    val allLogs: Flow<List<StockLog>> = stockLogDao.getAllLogs()
    val allLocations: Flow<List<String>> = inventoryDao.getAllLocations()

    fun searchItems(query: String): Flow<List<InventoryItem>> = inventoryDao.searchItems(query)

    fun getItemByIdFlow(id: Long): Flow<InventoryItem?> = inventoryDao.getItemByIdFlow(id)

    suspend fun getItemByBarcode(barcode: String): InventoryItem? = withContext(Dispatchers.IO) {
        inventoryDao.getItemByCode(barcode.trim())
    }

    suspend fun insertItem(item: InventoryItem): Long = withContext(Dispatchers.IO) {
        val id = inventoryDao.insertItem(item)
        stockLogDao.insertLog(
            StockLog(
                inventoryItemId = id,
                itemName = item.commonName,
                barcode = item.newCode,
                actionType = "INITIAL_ENTRY",
                quantityChanged = item.quantity,
                resultingQuantity = item.quantity,
                notes = "Added new stock item to ${item.location}"
            )
        )
        if (item.isLowStock) {
            NotificationHelper.sendLowStockNotification(context, item.copy(id = id))
        }
        id
    }

    suspend fun updateItem(item: InventoryItem) = withContext(Dispatchers.IO) {
        val existing = inventoryDao.getItemById(item.id)
        inventoryDao.updateItem(item)
        if (existing != null && existing.quantity != item.quantity) {
            val diff = item.quantity - existing.quantity
            stockLogDao.insertLog(
                StockLog(
                    inventoryItemId = item.id,
                    itemName = item.commonName,
                    barcode = item.newCode,
                    actionType = if (diff > 0) "RESTOCK" else "MANUAL_ADJUST",
                    quantityChanged = diff,
                    resultingQuantity = item.quantity,
                    notes = "Manual inventory adjustment"
                )
            )
        }
        if (item.isLowStock) {
            NotificationHelper.sendLowStockNotification(context, item)
        }
    }

    suspend fun adjustQuantity(itemId: Long, change: Int, reason: String = "Manual Adjustment"): Boolean = withContext(Dispatchers.IO) {
        val item = inventoryDao.getItemById(itemId) ?: return@withContext false
        val newQty = (item.quantity + change).coerceAtLeast(0)
        inventoryDao.updateQuantity(itemId, newQty)

        stockLogDao.insertLog(
            StockLog(
                inventoryItemId = item.id,
                itemName = item.commonName,
                barcode = item.newCode,
                actionType = if (change >= 0) "RESTOCK" else "MANUAL_ADJUST",
                quantityChanged = change,
                resultingQuantity = newQty,
                notes = reason
            )
        )

        val updatedItem = item.copy(quantity = newQty)
        if (updatedItem.isLowStock) {
            NotificationHelper.sendLowStockNotification(context, updatedItem)
        }
        true
    }

    suspend fun deleteItem(item: InventoryItem) = withContext(Dispatchers.IO) {
        inventoryDao.deleteItem(item)
    }

    fun getOrderFlow(orderId: Long): Flow<OrderWithItems?> = orderDao.getOrderWithItemsFlow(orderId)

    suspend fun createOrder(
        orderNumber: String,
        customerName: String,
        destination: String,
        itemsToPull: List<Pair<InventoryItem, Int>>
    ): Long = withContext(Dispatchers.IO) {
        val order = Order(
            orderNumber = orderNumber,
            customerName = customerName,
            destination = destination,
            status = "PENDING"
        )
        val orderId = orderDao.insertOrder(order)
        val orderItems = itemsToPull.map { (item, qtyReq) ->
            OrderItem(
                orderId = orderId,
                inventoryItemId = item.id,
                commonName = item.commonName,
                barcode = item.newCode,
                location = item.location,
                quantityRequired = qtyReq,
                quantityPulled = 0
            )
        }
        orderDao.insertOrderItems(orderItems)
        orderId
    }

    /**
     * Pulls item by barcode scan or manual action during an active order picking session.
     * Decrements inventory quantity in real-time, updates pulled count, checks low-stock alert.
     */
    suspend fun pullItemForOrder(
        orderId: Long,
        scannedBarcode: String,
        pullCount: Int = 1
    ): PullResult = withContext(Dispatchers.IO) {
        val orderWithItems = orderDao.getOrderWithItems(orderId)
            ?: return@withContext PullResult.Error("Order #$orderId not found")

        // Find matching item in order by barcode (newCode) or oldCode
        val targetOrderItem = orderWithItems.items.find {
            it.barcode.equals(scannedBarcode.trim(), ignoreCase = true)
        } ?: run {
            // Also check if barcode matches an item's newCode or oldCode
            val invItem = inventoryDao.getItemByCode(scannedBarcode.trim())
            if (invItem != null) {
                orderWithItems.items.find { it.inventoryItemId == invItem.id }
            } else null
        } ?: return@withContext PullResult.NotInOrder(scannedBarcode)

        if (targetOrderItem.isFulfilled) {
            return@withContext PullResult.AlreadyFulfilled(targetOrderItem)
        }

        val needed = targetOrderItem.quantityRequired - targetOrderItem.quantityPulled
        val toPull = pullCount.coerceAtMost(needed)

        val inventoryItem = inventoryDao.getItemById(targetOrderItem.inventoryItemId)
            ?: return@withContext PullResult.Error("Inventory item not found in database")

        if (inventoryItem.quantity < toPull) {
            return@withContext PullResult.InsufficientStock(inventoryItem, targetOrderItem)
        }

        // 1. Update OrderItem pulled count
        val newPulled = targetOrderItem.quantityPulled + toPull
        orderDao.updatePulledQuantity(targetOrderItem.orderItemId, newPulled)

        // 2. Decrement real inventory
        val updatedStock = (inventoryItem.quantity - toPull).coerceAtLeast(0)
        inventoryDao.updateQuantity(inventoryItem.id, updatedStock)

        // 3. Log Stock Movement
        stockLogDao.insertLog(
            StockLog(
                inventoryItemId = inventoryItem.id,
                itemName = inventoryItem.commonName,
                barcode = inventoryItem.newCode,
                actionType = "ORDER_PULL",
                quantityChanged = -toPull,
                resultingQuantity = updatedStock,
                referenceOrder = orderWithItems.order.orderNumber,
                notes = "Pulled $toPull ${inventoryItem.unit} for Order ${orderWithItems.order.orderNumber}"
            )
        )

        // 4. Low stock notification trigger
        val itemAfterPull = inventoryItem.copy(quantity = updatedStock)
        if (itemAfterPull.isLowStock) {
            NotificationHelper.sendLowStockNotification(context, itemAfterPull)
        }

        // 5. Update Order Status
        val updatedOrderWithItems = orderDao.getOrderWithItems(orderId)
        val allFulfilled = updatedOrderWithItems?.items?.all { it.isFulfilled } == true
        if (allFulfilled) {
            orderDao.updateOrder(
                orderWithItems.order.copy(
                    status = "COMPLETED",
                    completedAt = System.currentTimeMillis()
                )
            )
        } else if (orderWithItems.order.status == "PENDING") {
            orderDao.updateOrder(orderWithItems.order.copy(status = "IN_PROGRESS"))
        }

        PullResult.Success(
            item = itemAfterPull,
            orderItem = targetOrderItem.copy(quantityPulled = newPulled),
            isOrderComplete = allFulfilled
        )
    }

    suspend fun cancelOrResetOrderPull(orderId: Long, orderItemId: Long) = withContext(Dispatchers.IO) {
        val orderWithItems = orderDao.getOrderWithItems(orderId) ?: return@withContext
        val targetItem = orderWithItems.items.find { it.orderItemId == orderItemId } ?: return@withContext
        val pulled = targetItem.quantityPulled
        if (pulled > 0) {
            // Restore inventory
            val inventoryItem = inventoryDao.getItemById(targetItem.inventoryItemId)
            if (inventoryItem != null) {
                val newQty = inventoryItem.quantity + pulled
                inventoryDao.updateQuantity(inventoryItem.id, newQty)
                stockLogDao.insertLog(
                    StockLog(
                        inventoryItemId = inventoryItem.id,
                        itemName = inventoryItem.commonName,
                        barcode = inventoryItem.newCode,
                        actionType = "RESTOCK",
                        quantityChanged = pulled,
                        resultingQuantity = newQty,
                        referenceOrder = orderWithItems.order.orderNumber,
                        notes = "Returned $pulled items to shelf from Order ${orderWithItems.order.orderNumber}"
                    )
                )
            }
            orderDao.updatePulledQuantity(orderItemId, 0)
            orderDao.updateOrder(orderWithItems.order.copy(status = "IN_PROGRESS", completedAt = null))
        }
    }

    suspend fun deleteOrder(order: Order) = withContext(Dispatchers.IO) {
        orderDao.deleteOrder(order)
    }
}

sealed class PullResult {
    data class Success(val item: InventoryItem, val orderItem: OrderItem, val isOrderComplete: Boolean) : PullResult()
    data class NotInOrder(val barcode: String) : PullResult()
    data class AlreadyFulfilled(val orderItem: OrderItem) : PullResult()
    data class InsufficientStock(val item: InventoryItem, val orderItem: OrderItem) : PullResult()
    data class Error(val message: String) : PullResult()
}

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

    /**
     * Check-in / Inbound Stock Receiving for incoming inventory shipments.
     * Updates quantity, put-away shelf location, logs audit entry, and sends alert if still low stock.
     */
    suspend fun receiveInventoryItem(
        itemId: Long,
        quantityReceived: Int,
        updatedLocation: String? = null,
        referenceDoc: String? = null,
        notes: String = ""
    ): InventoryItem? = withContext(Dispatchers.IO) {
        val item = inventoryDao.getItemById(itemId) ?: return@withContext null
        val newQty = (item.quantity + quantityReceived).coerceAtLeast(0)
        val finalLocation = if (!updatedLocation.isNullOrBlank()) updatedLocation.trim() else item.location
        val updatedItem = item.copy(
            quantity = newQty,
            location = finalLocation,
            lastUpdated = System.currentTimeMillis()
        )
        inventoryDao.updateItem(updatedItem)

        stockLogDao.insertLog(
            StockLog(
                inventoryItemId = item.id,
                itemName = item.commonName,
                barcode = item.newCode,
                actionType = "RECEIVING",
                quantityChanged = quantityReceived,
                resultingQuantity = newQty,
                referenceOrder = referenceDoc?.trim()?.ifEmpty { null },
                notes = if (notes.isNotBlank()) notes.trim() else "Inbound delivery check-in"
            )
        )

        if (updatedItem.isLowStock) {
            NotificationHelper.sendLowStockNotification(context, updatedItem)
        }
        updatedItem
    }

    suspend fun deleteItem(item: InventoryItem) = withContext(Dispatchers.IO) {
        inventoryDao.deleteItem(item)
    }

    val allPullOrders: Flow<List<OrderWithItems>> = orderDao.getAllPullOrders()
    val allPurchaseOrders: Flow<List<OrderWithItems>> = orderDao.getAllPurchaseOrders()
    val activePurchaseOrders: Flow<List<OrderWithItems>> = orderDao.getActivePurchaseOrders()

    fun getOrderFlow(orderId: Long): Flow<OrderWithItems?> = orderDao.getOrderWithItemsFlow(orderId)

    suspend fun getNextOrderNumber(orderType: String = "PULL"): String = withContext(Dispatchers.IO) {
        val orders = orderDao.getAllOrdersList()
        val numbers = orders.mapNotNull {
            Regex("\\d+").find(it.orderNumber)?.value?.toIntOrNull()
        }
        val maxNumber = numbers.maxOrNull() ?: 33099
        val nextNumber = maxOf(33100, maxNumber + 1)
        if (orderType == "PURCHASE") "PO-$nextNumber" else "ORD-$nextNumber"
    }

    suspend fun createOrder(
        orderNumber: String,
        customerName: String,
        destination: String,
        orderType: String = "PULL",
        expectedDeliveryDate: String? = null,
        notes: String? = null,
        itemsToPull: List<Pair<InventoryItem, Int>>
    ): Long = withContext(Dispatchers.IO) {
        val order = Order(
            orderNumber = orderNumber.trim(),
            customerName = customerName.trim(),
            destination = destination.trim(),
            orderType = orderType,
            status = "PENDING",
            expectedDeliveryDate = expectedDeliveryDate?.trim()?.ifEmpty { null },
            notes = notes?.trim()?.ifEmpty { null }
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
     * Registers an Inbound Purchase / Replenishment Order.
     * Can include both existing inventory items and newly defined catalog items with 0 initial stock.
     */
    suspend fun createPurchaseOrder(
        orderNumber: String,
        supplierName: String,
        receivingBay: String,
        expectedDeliveryDate: String? = null,
        notes: String? = null,
        existingItems: List<Pair<InventoryItem, Int>>,
        newItems: List<NewItemOrderRequest>
    ): Long = withContext(Dispatchers.IO) {
        val cleanOrderNum = orderNumber.trim().ifEmpty { getNextOrderNumber("PURCHASE") }
        val order = Order(
            orderNumber = cleanOrderNum,
            customerName = supplierName.trim().ifEmpty { "General Supplier" },
            destination = receivingBay.trim().ifEmpty { "Dock 2 - Receiving Bay" },
            orderType = "PURCHASE",
            status = "IN_TRANSIT",
            expectedDeliveryDate = expectedDeliveryDate?.trim()?.ifEmpty { null },
            notes = notes?.trim()?.ifEmpty { null }
        )
        val orderId = orderDao.insertOrder(order)
        val itemsList = mutableListOf<OrderItem>()

        // 1. Add existing inventory items to the PO
        for ((item, qtyReq) in existingItems) {
            itemsList.add(
                OrderItem(
                    orderId = orderId,
                    inventoryItemId = item.id,
                    commonName = item.commonName,
                    barcode = item.newCode,
                    location = item.location,
                    quantityRequired = qtyReq,
                    quantityPulled = 0
                )
            )
        }

        // 2. Create newly registered catalog items and add to the PO
        for (newItemReq in newItems) {
            val generatedBarcode = if (newItemReq.newCode.isNotBlank()) {
                newItemReq.newCode.trim()
            } else {
                "${System.currentTimeMillis().toString().takeLast(10)}${(10..99).random()}"
            }
            val newItem = InventoryItem(
                commonName = newItemReq.commonName.trim(),
                oldCode = newItemReq.oldCode.trim(),
                newCode = generatedBarcode,
                quantity = 0, // Initial on-hand is 0, on-the-way is incoming
                location = newItemReq.location.trim().ifEmpty { "Dock 2 - Receiving Bay" },
                minThreshold = newItemReq.minThreshold,
                unit = newItemReq.unit.trim().ifEmpty { "pcs" },
                category = newItemReq.category.trim().ifEmpty { "General" }
            )
            val newId = inventoryDao.insertItem(newItem)
            stockLogDao.insertLog(
                StockLog(
                    inventoryItemId = newId,
                    itemName = newItem.commonName,
                    barcode = newItem.newCode,
                    actionType = "INITIAL_ENTRY",
                    quantityChanged = 0,
                    resultingQuantity = 0,
                    referenceOrder = cleanOrderNum,
                    notes = "New item cataloged for incoming purchase order $cleanOrderNum"
                )
            )
            itemsList.add(
                OrderItem(
                    orderId = orderId,
                    inventoryItemId = newId,
                    commonName = newItem.commonName,
                    barcode = newItem.newCode,
                    location = newItem.location,
                    quantityRequired = newItemReq.quantityOrdered,
                    quantityPulled = 0
                )
            )
        }

        if (itemsList.isNotEmpty()) {
            orderDao.insertOrderItems(itemsList)
        }
        orderId
    }

    /**
     * Receives a specific quantity for a purchase order item, incrementing inventory stock.
     */
    suspend fun receivePurchaseOrderItem(
        orderId: Long,
        orderItemId: Long,
        quantityReceived: Int,
        updatedLocation: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val orderWithItems = orderDao.getOrderWithItems(orderId) ?: return@withContext false
        val orderItem = orderWithItems.items.find { it.orderItemId == orderItemId } ?: return@withContext false
        if (quantityReceived <= 0) return@withContext false

        val itemInDb = inventoryDao.getItemById(orderItem.inventoryItemId)
            ?: inventoryDao.getItemByCode(orderItem.barcode)
            ?: return@withContext false

        // Update inventory quantity
        val newStock = itemInDb.quantity + quantityReceived
        val newLoc = updatedLocation?.trim()?.ifEmpty { itemInDb.location } ?: itemInDb.location
        val updatedItem = itemInDb.copy(
            quantity = newStock,
            location = newLoc,
            lastUpdated = System.currentTimeMillis()
        )
        inventoryDao.updateItem(updatedItem)

        // Update received quantity on OrderItem
        val newPulled = orderItem.quantityPulled + quantityReceived
        orderDao.updatePulledQuantity(orderItemId, newPulled)

        // Log Stock Change
        stockLogDao.insertLog(
            StockLog(
                inventoryItemId = updatedItem.id,
                itemName = updatedItem.commonName,
                barcode = updatedItem.newCode,
                actionType = "CHECK_IN",
                quantityChanged = quantityReceived,
                resultingQuantity = newStock,
                referenceOrder = orderWithItems.order.orderNumber,
                notes = "Received from Purchase Order ${orderWithItems.order.orderNumber}"
            )
        )

        // Check if full order is completed
        val refreshed = orderDao.getOrderWithItems(orderId)
        if (refreshed != null && refreshed.items.all { it.isFulfilled }) {
            orderDao.updateOrder(
                refreshed.order.copy(
                    status = "COMPLETED",
                    completedAt = System.currentTimeMillis()
                )
            )
        }
        true
    }

    /**
     * Receives all pending items in a purchase order in a single tap.
     */
    suspend fun receiveEntirePurchaseOrder(orderId: Long): Boolean = withContext(Dispatchers.IO) {
        val orderWithItems = orderDao.getOrderWithItems(orderId) ?: return@withContext false
        for (orderItem in orderWithItems.items) {
            val remaining = orderItem.quantityRequired - orderItem.quantityPulled
            if (remaining > 0) {
                receivePurchaseOrderItem(orderId, orderItem.orderItemId, remaining)
            }
        }
        orderDao.updateOrder(
            orderWithItems.order.copy(
                status = "COMPLETED",
                completedAt = System.currentTimeMillis()
            )
        )
        true
    }

    /**
     * Identifies a scanned barcode in the context of an order picking session.
     */
    suspend fun evaluateOrderScan(
        orderId: Long,
        scannedBarcode: String
    ): ScanTargetResult = withContext(Dispatchers.IO) {
        val cleanBarcode = scannedBarcode.trim()
        val orderWithItems = orderDao.getOrderWithItems(orderId)
            ?: return@withContext ScanTargetResult.Error("Order #$orderId not found")

        // 1. Check if barcode directly matches an OrderItem barcode
        var targetOrderItem = orderWithItems.items.find {
            it.barcode.equals(cleanBarcode, ignoreCase = true)
        }

        // 2. If not found directly, check if it matches an item's newCode or oldCode in DB
        val invItem = inventoryDao.getItemByCode(cleanBarcode)
        if (targetOrderItem == null && invItem != null) {
            targetOrderItem = orderWithItems.items.find { it.inventoryItemId == invItem.id }
        }

        if (targetOrderItem != null) {
            val itemInDb = inventoryDao.getItemById(targetOrderItem.inventoryItemId)
                ?: return@withContext ScanTargetResult.Error("Item record not found")
            val needed = (targetOrderItem.quantityRequired - targetOrderItem.quantityPulled).coerceAtLeast(0)
            return@withContext ScanTargetResult.MatchedOrderItem(
                orderItem = targetOrderItem,
                inventoryItem = itemInDb,
                neededQty = needed,
                availableStock = itemInDb.quantity
            )
        }

        // 3. Item is in warehouse inventory, but NOT on this order (Wrong Item!)
        if (invItem != null) {
            return@withContext ScanTargetResult.WrongItemInInventory(invItem)
        }

        // 4. Barcode is completely unrecognized
        ScanTargetResult.UnregisteredBarcode(cleanBarcode)
    }

    /**
     * Pulls item by specified quantity during an active order picking session.
     * Decrements inventory quantity in real-time, updates pulled count, checks low-stock alert.
     */
    suspend fun pullOrderItemQuantity(
        orderId: Long,
        orderItemId: Long,
        quantityToPull: Int
    ): PullResult = withContext(Dispatchers.IO) {
        val orderWithItems = orderDao.getOrderWithItems(orderId)
            ?: return@withContext PullResult.Error("Order #$orderId not found")

        val targetOrderItem = orderWithItems.items.find { it.orderItemId == orderItemId }
            ?: return@withContext PullResult.Error("Line item not found in order")

        if (targetOrderItem.isFulfilled) {
            return@withContext PullResult.AlreadyFulfilled(targetOrderItem)
        }

        if (quantityToPull <= 0) {
            return@withContext PullResult.Error("Pull quantity must be greater than 0")
        }

        val needed = targetOrderItem.quantityRequired - targetOrderItem.quantityPulled
        val toPull = quantityToPull.coerceAtMost(needed)

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

    /**
     * Pulls item by barcode scan or manual action (default 1) during an active order picking session.
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
            val invItem = inventoryDao.getItemByCode(scannedBarcode.trim())
            if (invItem != null) {
                orderWithItems.items.find { it.inventoryItemId == invItem.id }
            } else null
        } ?: return@withContext PullResult.NotInOrder(scannedBarcode)

        pullOrderItemQuantity(orderId, targetOrderItem.orderItemId, pullCount)
    }

    /**
     * Substitutes an existing line item in an order with a replacement inventory item.
     * Restores previously pulled units to original inventory if needed.
     */
    suspend fun substituteOrderItem(
        orderId: Long,
        orderItemId: Long,
        newInventoryItem: InventoryItem,
        newRequiredQty: Int? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val orderWithItems = orderDao.getOrderWithItems(orderId) ?: return@withContext false
        val oldOrderItem = orderWithItems.items.find { it.orderItemId == orderItemId } ?: return@withContext false

        // 1. If units were already pulled for old item, restore them to shelf
        if (oldOrderItem.quantityPulled > 0) {
            val oldInvItem = inventoryDao.getItemById(oldOrderItem.inventoryItemId)
            if (oldInvItem != null) {
                val restoredQty = oldInvItem.quantity + oldOrderItem.quantityPulled
                inventoryDao.updateQuantity(oldInvItem.id, restoredQty)
                stockLogDao.insertLog(
                    StockLog(
                        inventoryItemId = oldInvItem.id,
                        itemName = oldInvItem.commonName,
                        barcode = oldInvItem.newCode,
                        actionType = "RESTOCK",
                        quantityChanged = oldOrderItem.quantityPulled,
                        resultingQuantity = restoredQty,
                        referenceOrder = orderWithItems.order.orderNumber,
                        notes = "Restocked due to substitution with ${newInventoryItem.commonName}"
                    )
                )
            }
        }

        // 2. Update OrderItem entity with new item details
        val targetReq = newRequiredQty ?: oldOrderItem.quantityRequired
        val updatedOrderItem = oldOrderItem.copy(
            inventoryItemId = newInventoryItem.id,
            commonName = newInventoryItem.commonName,
            barcode = newInventoryItem.newCode,
            location = newInventoryItem.location,
            quantityRequired = targetReq,
            quantityPulled = 0
        )
        orderDao.updateOrderItem(updatedOrderItem)

        // 3. Reset order status to IN_PROGRESS if was completed
        orderDao.updateOrder(orderWithItems.order.copy(status = "IN_PROGRESS", completedAt = null))
        true
    }

    /**
     * Adds an extra item to an existing order.
     */
    suspend fun addItemToExistingOrder(
        orderId: Long,
        item: InventoryItem,
        quantityRequired: Int
    ): Long = withContext(Dispatchers.IO) {
        val orderWithItems = orderDao.getOrderWithItems(orderId) ?: return@withContext 0L
        val orderItem = OrderItem(
            orderId = orderId,
            inventoryItemId = item.id,
            commonName = item.commonName,
            barcode = item.newCode,
            location = item.location,
            quantityRequired = quantityRequired,
            quantityPulled = 0
        )
        val id = orderDao.insertOrderItem(orderItem)
        orderDao.updateOrder(orderWithItems.order.copy(status = "IN_PROGRESS", completedAt = null))
        id
    }

    /**
     * Removes a line item from an order, restoring any pulled units.
     */
    suspend fun removeOrderItem(orderId: Long, orderItemId: Long) = withContext(Dispatchers.IO) {
        val orderWithItems = orderDao.getOrderWithItems(orderId) ?: return@withContext
        val targetItem = orderWithItems.items.find { it.orderItemId == orderItemId } ?: return@withContext

        if (targetItem.quantityPulled > 0) {
            val inv = inventoryDao.getItemById(targetItem.inventoryItemId)
            if (inv != null) {
                val restored = inv.quantity + targetItem.quantityPulled
                inventoryDao.updateQuantity(inv.id, restored)
                stockLogDao.insertLog(
                    StockLog(
                        inventoryItemId = inv.id,
                        itemName = inv.commonName,
                        barcode = inv.newCode,
                        actionType = "RESTOCK",
                        quantityChanged = targetItem.quantityPulled,
                        resultingQuantity = restored,
                        referenceOrder = orderWithItems.order.orderNumber,
                        notes = "Restocked: Item removed from order"
                    )
                )
            }
        }
        orderDao.deleteOrderItemById(orderItemId)

        val remaining = orderDao.getOrderWithItems(orderId)
        if (remaining != null && remaining.items.isNotEmpty() && remaining.items.all { it.isFulfilled }) {
            orderDao.updateOrder(remaining.order.copy(status = "COMPLETED", completedAt = System.currentTimeMillis()))
        }
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

sealed class ScanTargetResult {
    data class MatchedOrderItem(
        val orderItem: OrderItem,
        val inventoryItem: InventoryItem,
        val neededQty: Int,
        val availableStock: Int
    ) : ScanTargetResult()

    data class WrongItemInInventory(
        val scannedItem: InventoryItem
    ) : ScanTargetResult()

    data class UnregisteredBarcode(
        val barcode: String
    ) : ScanTargetResult()

    data class Error(val message: String) : ScanTargetResult()
}

data class NewItemOrderRequest(
    val commonName: String,
    val oldCode: String = "",
    val newCode: String = "",
    val location: String = "Receiving Bay / Staging",
    val minThreshold: Int = 10,
    val unit: String = "pcs",
    val category: String = "General",
    val quantityOrdered: Int
)


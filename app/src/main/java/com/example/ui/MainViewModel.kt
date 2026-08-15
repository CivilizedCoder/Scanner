package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.dao.OrderWithItems
import com.example.data.db.AppDatabase
import com.example.data.model.InventoryItem
import com.example.data.model.Order
import com.example.data.model.OrderItem
import com.example.data.model.StockLog
import com.example.data.repository.InventoryRepository
import com.example.data.repository.NewItemOrderRequest
import com.example.data.repository.PullResult
import com.example.data.repository.ScanTargetResult
import com.example.util.ExportHelper
import com.example.util.ItemSearchMatcher
import com.example.util.NotificationHelper
import com.example.util.SubstituteSuggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ScreenTab {
    INVENTORY,
    ORDER_PULLING,
    LIVE_SCANNER,
    REPORTS
}

enum class ScannerMode {
    CHECK_IN,   // Inbound receiving mode for incoming shipments
    LOOKUP      // Quick search, stock inspection and manual actions
}

data class ScanFeedback(
    val barcode: String,
    val item: InventoryItem? = null,
    val message: String,
    val isSuccess: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class CheckInPrompt(
    val item: InventoryItem,
    val initialQty: Int = 1,
    val defaultLocation: String = item.location
)

data class SessionReceivedItem(
    val id: Long = System.currentTimeMillis(),
    val itemId: Long,
    val itemName: String,
    val barcode: String,
    val quantityReceived: Int,
    val resultingQuantity: Int,
    val location: String,
    val unit: String,
    val reference: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class PullQuantityPrompt(
    val orderId: Long,
    val orderItem: OrderItem,
    val inventoryItem: InventoryItem,
    val neededQty: Int,
    val availableStock: Int,
    val defaultQty: Int
)

data class WrongItemAlert(
    val orderId: Long,
    val scannedBarcode: String,
    val scannedItem: InventoryItem?, // null if unregistered barcode
    val message: String
)

data class SubstituteItemPrompt(
    val orderId: Long,
    val targetOrderItem: OrderItem,
    val preselectedSubstitute: InventoryItem? = null
)

data class ItemReplenishmentInfo(
    val onTheWayQuantity: Int = 0,
    val pendingOrderNumbers: List<String> = emptyList(),
    val isNeedsPurchase: Boolean = false, // quantity < minThreshold && quantity + onTheWayQuantity < minThreshold
    val isOnTheWayCovered: Boolean = false // quantity < minThreshold && quantity + onTheWayQuantity >= minThreshold
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository: InventoryRepository

    init {
        NotificationHelper.createNotificationChannel(application)
        val database = AppDatabase.getDatabase(application, viewModelScope)
        repository = InventoryRepository(
            application,
            database.inventoryDao(),
            database.orderDao(),
            database.stockLogDao()
        )
    }

    private val _selectedTab = MutableStateFlow(ScreenTab.INVENTORY)
    val selectedTab: StateFlow<ScreenTab> = _selectedTab.asStateFlow()

    fun setTab(tab: ScreenTab) {
        _selectedTab.value = tab
    }

    // Search and Filter State
    val searchQuery = MutableStateFlow("")
    val filterLowStockOnly = MutableStateFlow(false)
    val filterNeedsPurchaseOnly = MutableStateFlow(false)
    val filterOnTheWayOnly = MutableStateFlow(false)
    val selectedLocationFilter = MutableStateFlow<String?>(null)

    val allLocations: StateFlow<List<String>> = repository.allLocations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockCount: StateFlow<Int> = repository.lowStockCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allOrders: StateFlow<List<OrderWithItems>> = repository.allOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPullOrders: StateFlow<List<OrderWithItems>> = repository.allPullOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPurchaseOrders: StateFlow<List<OrderWithItems>> = repository.allPurchaseOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePurchaseOrders: StateFlow<List<OrderWithItems>> = repository.activePurchaseOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Map of Item ID -> Replenishment information (incoming on the way stock from pending Purchase Orders)
    val replenishmentInfoMap: StateFlow<Map<Long, ItemReplenishmentInfo>> = combine(
        repository.allItems,
        activePurchaseOrders
    ) { items, purchaseOrders ->
        items.associate { item ->
            val matchingItems = purchaseOrders.flatMap { po ->
                po.items.filter { it.inventoryItemId == item.id || it.barcode.equals(item.newCode, ignoreCase = true) }
                    .map { it to po.order.orderNumber }
            }
            val onTheWayQty = matchingItems.sumOf { (orderItem, _) -> orderItem.remainingQuantity }
            val poNumbers = matchingItems.map { it.second }.distinct()
            val isLow = item.quantity < item.minThreshold
            val totalEffective = item.quantity + onTheWayQty

            val isNeedsPurchase = isLow && totalEffective < item.minThreshold
            val isOnTheWayCovered = isLow && totalEffective >= item.minThreshold

            item.id to ItemReplenishmentInfo(
                onTheWayQuantity = onTheWayQty,
                pendingOrderNumbers = poNumbers,
                isNeedsPurchase = isNeedsPurchase,
                isOnTheWayCovered = isOnTheWayCovered
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Counts of items needing purchases vs covered on the way
    val needsPurchaseCount: StateFlow<Int> = replenishmentInfoMap.flatMapLatest { map ->
        MutableStateFlow(map.values.count { it.isNeedsPurchase })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val onTheWayCount: StateFlow<Int> = replenishmentInfoMap.flatMapLatest { map ->
        MutableStateFlow(map.values.count { it.onTheWayQuantity > 0 })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val inventoryItems: StateFlow<List<InventoryItem>> = combine(
        searchQuery.flatMapLatest { query ->
            if (query.isBlank()) repository.allItems else repository.searchItems(query)
        },
        filterLowStockOnly,
        filterNeedsPurchaseOnly,
        filterOnTheWayOnly,
        selectedLocationFilter,
        replenishmentInfoMap
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val items = args[0] as List<InventoryItem>
        val lowStockOnly = args[1] as Boolean
        val needsPurchaseOnly = args[2] as Boolean
        val onTheWayOnly = args[3] as Boolean
        val location = args[4] as? String
        @Suppress("UNCHECKED_CAST")
        val replMap = args[5] as Map<Long, ItemReplenishmentInfo>

        items.filter { item ->
            val repl = replMap[item.id]
            val passesLowStock = !lowStockOnly || item.isLowStock
            val passesNeedsPurchase = !needsPurchaseOnly || (repl?.isNeedsPurchase == true)
            val passesOnTheWay = !onTheWayOnly || ((repl?.onTheWayQuantity ?: 0) > 0)
            val passesLocation = location == null || item.location == location

            passesLowStock && passesNeedsPurchase && passesOnTheWay && passesLocation
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getNextOrderNumber(orderType: String = "PULL"): String {
        return repository.getNextOrderNumber(orderType)
    }

    val allLogs: StateFlow<List<StockLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Order Picking Session State
    private val _activeOrderId = MutableStateFlow<Long?>(null)
    val activeOrderId: StateFlow<Long?> = _activeOrderId.asStateFlow()

    val activeOrder: StateFlow<OrderWithItems?> = _activeOrderId.flatMapLatest { id ->
        if (id != null) repository.getOrderFlow(id) else MutableStateFlow(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setActiveOrder(orderId: Long?) {
        _activeOrderId.value = orderId
        _pullPrompt.value = null
        _wrongItemAlert.value = null
        _substitutePrompt.value = null
    }

    // Pull Quantity Reminder / Prompt State
    private val _pullPrompt = MutableStateFlow<PullQuantityPrompt?>(null)
    val pullPrompt: StateFlow<PullQuantityPrompt?> = _pullPrompt.asStateFlow()

    // Wrong Item Alert State (when scanned item does not belong to active order)
    private val _wrongItemAlert = MutableStateFlow<WrongItemAlert?>(null)
    val wrongItemAlert: StateFlow<WrongItemAlert?> = _wrongItemAlert.asStateFlow()

    // Substitute Item Dialog State
    private val _substitutePrompt = MutableStateFlow<SubstituteItemPrompt?>(null)
    val substitutePrompt: StateFlow<SubstituteItemPrompt?> = _substitutePrompt.asStateFlow()

    fun closePullPrompt() {
        _pullPrompt.value = null
    }

    fun closeWrongItemAlert() {
        _wrongItemAlert.value = null
    }

    fun openSubstituteDialog(orderId: Long, orderItem: OrderItem, preselectedSubstitute: InventoryItem? = null) {
        _substitutePrompt.value = SubstituteItemPrompt(orderId, orderItem, preselectedSubstitute)
    }

    fun closeSubstituteDialog() {
        _substitutePrompt.value = null
    }

    // Scanner Mode State (CHECK_IN for Inbound Receiving, LOOKUP for Quick Search)
    private val _scannerMode = MutableStateFlow(ScannerMode.CHECK_IN)
    val scannerMode: StateFlow<ScannerMode> = _scannerMode.asStateFlow()

    fun setScannerMode(mode: ScannerMode) {
        _scannerMode.value = mode
    }

    // Inbound Check-In / Stock Receiving Prompt State
    private val _checkInPrompt = MutableStateFlow<CheckInPrompt?>(null)
    val checkInPrompt: StateFlow<CheckInPrompt?> = _checkInPrompt.asStateFlow()

    // Real-time Check-In Session Log (Track what has been received this shift)
    private val _sessionReceivedItems = MutableStateFlow<List<SessionReceivedItem>>(emptyList())
    val sessionReceivedItems: StateFlow<List<SessionReceivedItem>> = _sessionReceivedItems.asStateFlow()

    fun openCheckInForItem(item: InventoryItem, initialQty: Int = 1) {
        _checkInPrompt.value = CheckInPrompt(
            item = item,
            initialQty = initialQty,
            defaultLocation = item.location
        )
    }

    fun closeCheckInPrompt() {
        _checkInPrompt.value = null
    }

    fun confirmCheckIn(
        itemId: Long,
        quantityReceived: Int,
        updatedLocation: String? = null,
        referenceDoc: String? = null,
        notes: String = ""
    ) {
        viewModelScope.launch {
            val updated = repository.receiveInventoryItem(
                itemId = itemId,
                quantityReceived = quantityReceived,
                updatedLocation = updatedLocation,
                referenceDoc = referenceDoc,
                notes = notes
            )
            _checkInPrompt.value = null
            if (updated != null) {
                val record = SessionReceivedItem(
                    itemId = updated.id,
                    itemName = updated.commonName,
                    barcode = updated.newCode,
                    quantityReceived = quantityReceived,
                    resultingQuantity = updated.quantity,
                    location = updated.location,
                    unit = updated.unit,
                    reference = referenceDoc
                )
                _sessionReceivedItems.value = listOf(record) + _sessionReceivedItems.value
                _lastScanFeedback.value = ScanFeedback(
                    barcode = updated.newCode,
                    item = updated,
                    message = "📥 Checked in +$quantityReceived ${updated.unit} of ${updated.commonName}. Total stock: ${updated.quantity} @ ${updated.location}",
                    isSuccess = true
                )
            } else {
                _lastScanFeedback.value = ScanFeedback(
                    barcode = "",
                    message = "❌ Failed to check in inventory item.",
                    isSuccess = false
                )
            }
        }
    }

    fun undoSessionReceivedItem(record: SessionReceivedItem) {
        viewModelScope.launch {
            repository.adjustQuantity(record.itemId, -record.quantityReceived, "Undo Inbound Check-In")
            _sessionReceivedItems.value = _sessionReceivedItems.value.filter { it.id != record.id }
            _lastScanFeedback.value = ScanFeedback(
                barcode = record.barcode,
                message = "↩️ Undid check-in of ${record.quantityReceived} ${record.unit} for ${record.itemName}",
                isSuccess = true
            )
        }
    }

    fun clearCheckInSession() {
        _sessionReceivedItems.value = emptyList()
    }

    // Live Scanner Last Result & Feedback
    private val _lastScanFeedback = MutableStateFlow<ScanFeedback?>(null)
    val lastScanFeedback: StateFlow<ScanFeedback?> = _lastScanFeedback.asStateFlow()

    fun clearScanFeedback() {
        _lastScanFeedback.value = null
    }

    // Export State
    private val _exportingState = MutableStateFlow<String?>(null)
    val exportingState: StateFlow<String?> = _exportingState.asStateFlow()

    // Add / Edit Item Dialog State
    private val _editingItem = MutableStateFlow<InventoryItem?>(null)
    val editingItem: StateFlow<InventoryItem?> = _editingItem.asStateFlow()

    private val _showAddEditSheet = MutableStateFlow(false)
    val showAddEditSheet: StateFlow<Boolean> = _showAddEditSheet.asStateFlow()

    // Quick Camera Ingest State (Auto prefill barcode when scanned)
    private val _cameraPrefillBarcode = MutableStateFlow<String?>(null)
    val cameraPrefillBarcode: StateFlow<String?> = _cameraPrefillBarcode.asStateFlow()

    fun openAddItem(initialBarcode: String? = null) {
        _editingItem.value = null
        _cameraPrefillBarcode.value = initialBarcode
        _showAddEditSheet.value = true
    }

    fun openEditItem(item: InventoryItem) {
        _editingItem.value = item
        _cameraPrefillBarcode.value = null
        _showAddEditSheet.value = true
    }

    fun closeAddEditSheet() {
        _showAddEditSheet.value = false
        _editingItem.value = null
        _cameraPrefillBarcode.value = null
    }

    fun saveInventoryItem(
        commonName: String,
        oldCode: String,
        newCode: String,
        photoUri: String?,
        quantity: Int,
        location: String,
        minThreshold: Int,
        unit: String,
        category: String
    ) {
        viewModelScope.launch {
            val existing = _editingItem.value
            if (existing != null) {
                repository.updateItem(
                    existing.copy(
                        commonName = commonName.trim(),
                        oldCode = oldCode.trim(),
                        newCode = newCode.trim(),
                        photoUri = photoUri,
                        quantity = quantity,
                        location = location.trim(),
                        minThreshold = minThreshold,
                        unit = unit.trim().ifEmpty { "pcs" },
                        category = category.trim().ifEmpty { "General" },
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            } else {
                repository.insertItem(
                    InventoryItem(
                        commonName = commonName.trim(),
                        oldCode = oldCode.trim(),
                        newCode = newCode.trim(),
                        photoUri = photoUri,
                        quantity = quantity,
                        location = location.trim(),
                        minThreshold = minThreshold,
                        unit = unit.trim().ifEmpty { "pcs" },
                        category = category.trim().ifEmpty { "General" },
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            }
            closeAddEditSheet()
        }
    }

    fun deleteItem(item: InventoryItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun adjustItemStock(item: InventoryItem, delta: Int, reason: String = "Manual Quick Adjust") {
        viewModelScope.launch {
            repository.adjustQuantity(item.id, delta, reason)
        }
    }

    /**
     * Process barcode scan in Order Pulling mode.
     * Evaluates whether it is the right item, wrong item, or unrecognized barcode,
     * and prompts the user with the desired quantity reminder.
     */
    fun processOrderPullScan(orderId: Long, barcode: String) {
        viewModelScope.launch {
            when (val scanTarget = repository.evaluateOrderScan(orderId, barcode)) {
                is ScanTargetResult.MatchedOrderItem -> {
                    if (scanTarget.orderItem.isFulfilled) {
                        _lastScanFeedback.value = ScanFeedback(
                            barcode = barcode,
                            item = scanTarget.inventoryItem,
                            message = "⚠️ Item '${scanTarget.orderItem.commonName}' is already fully pulled (${scanTarget.orderItem.quantityPulled}/${scanTarget.orderItem.quantityRequired})",
                            isSuccess = false
                        )
                    } else {
                        // Open pull reminder & quantity prompt with target default
                        val defaultQty = scanTarget.neededQty.coerceAtMost(scanTarget.availableStock).coerceAtLeast(1)
                        _pullPrompt.value = PullQuantityPrompt(
                            orderId = orderId,
                            orderItem = scanTarget.orderItem,
                            inventoryItem = scanTarget.inventoryItem,
                            neededQty = scanTarget.neededQty,
                            availableStock = scanTarget.availableStock,
                            defaultQty = defaultQty
                        )
                    }
                }
                is ScanTargetResult.WrongItemInInventory -> {
                    // Alert the user that they scanned the wrong item!
                    _wrongItemAlert.value = WrongItemAlert(
                        orderId = orderId,
                        scannedBarcode = barcode,
                        scannedItem = scanTarget.scannedItem,
                        message = "Scanned item '${scanTarget.scannedItem.commonName}' is not part of this order pick list."
                    )
                }
                is ScanTargetResult.UnregisteredBarcode -> {
                    _wrongItemAlert.value = WrongItemAlert(
                        orderId = orderId,
                        scannedBarcode = barcode,
                        scannedItem = null,
                        message = "Barcode '$barcode' was not found in the warehouse catalog or this order."
                    )
                }
                is ScanTargetResult.Error -> {
                    _lastScanFeedback.value = ScanFeedback(
                        barcode = barcode,
                        message = "❌ ${scanTarget.message}",
                        isSuccess = false
                    )
                }
            }
        }
    }

    /**
     * Open manual pull prompt for a specific order item from the pick list
     */
    fun openPullPromptForOrderItem(orderId: Long, orderItem: OrderItem) {
        viewModelScope.launch {
            val invItem = repository.getItemByBarcode(orderItem.barcode)
                ?: repository.allItems.stateIn(viewModelScope).value.find { it.id == orderItem.inventoryItemId }
            if (invItem != null) {
                val needed = (orderItem.quantityRequired - orderItem.quantityPulled).coerceAtLeast(0)
                val defaultQty = needed.coerceAtMost(invItem.quantity).coerceAtLeast(1)
                _pullPrompt.value = PullQuantityPrompt(
                    orderId = orderId,
                    orderItem = orderItem,
                    inventoryItem = invItem,
                    neededQty = needed,
                    availableStock = invItem.quantity,
                    defaultQty = defaultQty
                )
            } else {
                _lastScanFeedback.value = ScanFeedback(
                    barcode = orderItem.barcode,
                    message = "Item record not found in inventory catalog",
                    isSuccess = false
                )
            }
        }
    }

    /**
     * Confirm pull quantity from the reminder/prompt
     */
    fun confirmPullQuantity(orderId: Long, orderItemId: Long, quantity: Int) {
        viewModelScope.launch {
            _pullPrompt.value = null
            val result = repository.pullOrderItemQuantity(orderId, orderItemId, quantity)
            when (result) {
                is PullResult.Success -> {
                    _lastScanFeedback.value = ScanFeedback(
                        barcode = result.item.newCode,
                        item = result.item,
                        message = "✅ Pulled $quantity ${result.item.unit} of ${result.item.commonName} (${result.orderItem.quantityPulled}/${result.orderItem.quantityRequired})" +
                                if (result.isOrderComplete) " 🎉 ORDER COMPLETED!" else "",
                        isSuccess = true
                    )
                }
                is PullResult.AlreadyFulfilled -> {
                    _lastScanFeedback.value = ScanFeedback(
                        barcode = result.orderItem.barcode,
                        message = "⚠️ Item '${result.orderItem.commonName}' is already fully pulled (${result.orderItem.quantityPulled}/${result.orderItem.quantityRequired})",
                        isSuccess = false
                    )
                }
                is PullResult.InsufficientStock -> {
                    _lastScanFeedback.value = ScanFeedback(
                        barcode = result.item.newCode,
                        item = result.item,
                        message = "❌ Insufficient stock! Shelf only has ${result.item.quantity} ${result.item.unit} available.",
                        isSuccess = false
                    )
                }
                is PullResult.Error -> {
                    _lastScanFeedback.value = ScanFeedback(
                        barcode = "",
                        message = "❌ Error: ${result.message}",
                        isSuccess = false
                    )
                }
                is PullResult.NotInOrder -> {
                    _lastScanFeedback.value = ScanFeedback(
                        barcode = result.barcode,
                        message = "❌ Item is not in this order",
                        isSuccess = false
                    )
                }
            }
        }
    }

    /**
     * Substitute an order item with a replacement item
     */
    fun substituteOrderItem(
        orderId: Long,
        orderItemId: Long,
        newInventoryItem: InventoryItem,
        newRequiredQty: Int? = null
    ) {
        viewModelScope.launch {
            val success = repository.substituteOrderItem(orderId, orderItemId, newInventoryItem, newRequiredQty)
            _substitutePrompt.value = null
            _wrongItemAlert.value = null
            if (success) {
                _lastScanFeedback.value = ScanFeedback(
                    barcode = newInventoryItem.newCode,
                    item = newInventoryItem,
                    message = "🔄 Substituted with ${newInventoryItem.commonName} (@ ${newInventoryItem.location})",
                    isSuccess = true
                )
            } else {
                _lastScanFeedback.value = ScanFeedback(
                    barcode = newInventoryItem.newCode,
                    message = "❌ Failed to substitute order item",
                    isSuccess = false
                )
            }
        }
    }

    /**
     * Add an extra item to the current order
     */
    fun addItemToOrder(orderId: Long, item: InventoryItem, quantityRequired: Int) {
        viewModelScope.launch {
            repository.addItemToExistingOrder(orderId, item, quantityRequired)
            _wrongItemAlert.value = null
            _lastScanFeedback.value = ScanFeedback(
                barcode = item.newCode,
                item = item,
                message = "➕ Added ${item.commonName} ($quantityRequired ${item.unit}) to order pick list",
                isSuccess = true
            )
        }
    }

    /**
     * Remove an item from the current order
     */
    fun removeOrderItem(orderId: Long, orderItemId: Long) {
        viewModelScope.launch {
            repository.removeOrderItem(orderId, orderItemId)
        }
    }

    /**
     * Process barcode scan in Standalone Live Scanner tab
     */
    fun processStandaloneScan(barcode: String) {
        viewModelScope.launch {
            val item = repository.getItemByBarcode(barcode)
            if (item != null) {
                if (_scannerMode.value == ScannerMode.CHECK_IN) {
                    // Automatically trigger Inbound Check-In sheet
                    openCheckInForItem(item, 1)
                } else {
                    _lastScanFeedback.value = ScanFeedback(
                        barcode = barcode,
                        item = item,
                        message = "Found: ${item.commonName} • Qty: ${item.quantity} ${item.unit} @ ${item.location}",
                        isSuccess = true
                    )
                }
            } else {
                _lastScanFeedback.value = ScanFeedback(
                    barcode = barcode,
                    item = null,
                    message = "Unregistered Barcode ($barcode). Tap 'Quick Ingest' to catalog and check in this new product.",
                    isSuccess = false
                )
            }
        }
    }

    fun manualPullOrderItem(orderId: Long, orderItemId: Long, orderItem: OrderItem) {
        viewModelScope.launch {
            val item = repository.getItemByBarcode(orderItem.barcode)
            if (item != null) {
                processOrderPullScan(orderId, orderItem.barcode)
            }
        }
    }

    fun resetOrderItemPull(orderId: Long, orderItemId: Long) {
        viewModelScope.launch {
            repository.cancelOrResetOrderPull(orderId, orderItemId)
        }
    }

    fun createNewOrder(
        orderNumber: String,
        customerName: String,
        destination: String,
        selectedItems: List<Pair<InventoryItem, Int>>
    ) {
        viewModelScope.launch {
            val orderId = repository.createOrder(
                orderNumber = orderNumber.trim(),
                customerName = customerName.trim(),
                destination = destination.trim(),
                orderType = "PULL",
                itemsToPull = selectedItems
            )
            setActiveOrder(orderId)
            setTab(ScreenTab.ORDER_PULLING)
        }
    }

    fun createNewPurchaseOrder(
        orderNumber: String,
        supplierName: String,
        receivingBay: String,
        expectedDeliveryDate: String?,
        notes: String?,
        existingItems: List<Pair<InventoryItem, Int>>,
        newItems: List<NewItemOrderRequest>
    ) {
        viewModelScope.launch {
            repository.createPurchaseOrder(
                orderNumber = orderNumber,
                supplierName = supplierName,
                receivingBay = receivingBay,
                expectedDeliveryDate = expectedDeliveryDate,
                notes = notes,
                existingItems = existingItems,
                newItems = newItems
            )
        }
    }

    fun receivePurchaseOrderItem(
        orderId: Long,
        orderItemId: Long,
        quantityReceived: Int,
        updatedLocation: String? = null
    ) {
        viewModelScope.launch {
            repository.receivePurchaseOrderItem(orderId, orderItemId, quantityReceived, updatedLocation)
        }
    }

    fun receiveEntirePurchaseOrder(orderId: Long) {
        viewModelScope.launch {
            repository.receiveEntirePurchaseOrder(orderId)
        }
    }

    fun deleteOrder(order: Order) {
        viewModelScope.launch {
            if (_activeOrderId.value == order.orderId) {
                _activeOrderId.value = null
            }
            repository.deleteOrder(order)
        }
    }

    // Export Actions
    fun exportCsv() {
        viewModelScope.launch {
            _exportingState.value = "Generating CSV..."
            val items = inventoryItems.value
            val logs = allLogs.value
            val uri = ExportHelper.exportInventoryToCsv(getApplication(), items, logs)
            _exportingState.value = null
            if (uri != null) {
                ExportHelper.shareExportedFile(
                    getApplication(),
                    uri,
                    "text/csv",
                    "Warehouse Inventory CSV Export"
                )
            }
        }
    }

    fun exportPdf() {
        viewModelScope.launch {
            _exportingState.value = "Generating PDF..."
            val items = inventoryItems.value
            val uri = ExportHelper.exportInventoryToPdf(getApplication(), items)
            _exportingState.value = null
            if (uri != null) {
                ExportHelper.shareExportedFile(
                    getApplication(),
                    uri,
                    "application/pdf",
                    "Warehouse Inventory PDF Report"
                )
            }
        }
    }
}

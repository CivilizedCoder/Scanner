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
import com.example.data.repository.PullResult
import com.example.util.ExportHelper
import com.example.util.NotificationHelper
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

data class ScanFeedback(
    val barcode: String,
    val item: InventoryItem? = null,
    val message: String,
    val isSuccess: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: InventoryRepository

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
    val selectedLocationFilter = MutableStateFlow<String?>(null)

    val allLocations: StateFlow<List<String>> = repository.allLocations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockCount: StateFlow<Int> = repository.lowStockCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val inventoryItems: StateFlow<List<InventoryItem>> = combine(
        searchQuery.flatMapLatest { query ->
            if (query.isBlank()) repository.allItems else repository.searchItems(query)
        },
        filterLowStockOnly,
        selectedLocationFilter
    ) { items, lowStockOnly, location ->
        items.filter { item ->
            (!lowStockOnly || item.isLowStock) &&
            (location == null || item.location == location)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allOrders: StateFlow<List<OrderWithItems>> = repository.allOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
     * Process barcode scan in Order Pulling mode
     */
    fun processOrderPullScan(orderId: Long, barcode: String) {
        viewModelScope.launch {
            val result = repository.pullItemForOrder(orderId, barcode)
            when (result) {
                is PullResult.Success -> {
                    _lastScanFeedback.value = ScanFeedback(
                        barcode = barcode,
                        item = result.item,
                        message = "✅ Pulled 1 ${result.item.unit}: ${result.item.commonName} (${result.orderItem.quantityPulled}/${result.orderItem.quantityRequired})" +
                                if (result.isOrderComplete) " 🎉 ORDER COMPLETED!" else "",
                        isSuccess = true
                    )
                }
                is PullResult.AlreadyFulfilled -> {
                    _lastScanFeedback.value = ScanFeedback(
                        barcode = barcode,
                        message = "⚠️ Item '${result.orderItem.commonName}' is already fully pulled (${result.orderItem.quantityPulled}/${result.orderItem.quantityRequired})",
                        isSuccess = false
                    )
                }
                is PullResult.NotInOrder -> {
                    _lastScanFeedback.value = ScanFeedback(
                        barcode = barcode,
                        message = "❌ Barcode $barcode is NOT in this order pick list!",
                        isSuccess = false
                    )
                }
                is PullResult.InsufficientStock -> {
                    _lastScanFeedback.value = ScanFeedback(
                        barcode = barcode,
                        item = result.item,
                        message = "❌ Out of stock! Shelf only has ${result.item.quantity} available.",
                        isSuccess = false
                    )
                }
                is PullResult.Error -> {
                    _lastScanFeedback.value = ScanFeedback(
                        barcode = barcode,
                        message = "❌ Error: ${result.message}",
                        isSuccess = false
                    )
                }
            }
        }
    }

    /**
     * Process barcode scan in Standalone Live Scanner tab
     */
    fun processStandaloneScan(barcode: String) {
        viewModelScope.launch {
            val item = repository.getItemByBarcode(barcode)
            if (item != null) {
                _lastScanFeedback.value = ScanFeedback(
                    barcode = barcode,
                    item = item,
                    message = "Found: ${item.commonName} • Qty: ${item.quantity} ${item.unit} @ ${item.location}",
                    isSuccess = true
                )
            } else {
                _lastScanFeedback.value = ScanFeedback(
                    barcode = barcode,
                    item = null,
                    message = "Unregistered Barcode ($barcode). Tap 'Quick Ingest' to add to warehouse stock.",
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
                itemsToPull = selectedItems
            )
            setActiveOrder(orderId)
            setTab(ScreenTab.ORDER_PULLING)
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

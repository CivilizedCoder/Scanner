package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.dao.OrderWithItems
import com.example.data.model.InventoryItem
import com.example.data.model.OrderItem
import com.example.data.repository.NewItemOrderRequest
import com.example.ui.MainViewModel
import com.example.ui.PullQuantityPrompt
import com.example.ui.SubstituteItemPrompt
import com.example.ui.WrongItemAlert
import com.example.ui.components.BarcodeScannerComposable
import com.example.ui.components.PurchaseOrderCard
import com.example.ui.theme.LowStockRed
import com.example.ui.theme.StockGreen
import com.example.ui.theme.VibrantGreen
import com.example.ui.theme.VibrantGreenContainer
import com.example.ui.theme.VibrantOnGreenContainer
import com.example.ui.theme.VibrantRed
import com.example.ui.theme.VibrantSecondaryLight
import com.example.util.ItemSearchMatcher
import com.example.util.ItemSearchResult
import com.example.util.SubstituteSuggestion

@Composable
fun OrderPullingScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val activeOrderId by viewModel.activeOrderId.collectAsStateWithLifecycle()
    val activeOrderWithItems by viewModel.activeOrder.collectAsStateWithLifecycle()
    val allOrders by viewModel.allOrders.collectAsStateWithLifecycle()
    val allPullOrders by viewModel.allPullOrders.collectAsStateWithLifecycle()
    val allPurchaseOrders by viewModel.allPurchaseOrders.collectAsStateWithLifecycle()
    val inventoryItems by viewModel.inventoryItems.collectAsStateWithLifecycle()

    val pullPrompt by viewModel.pullPrompt.collectAsStateWithLifecycle()
    val wrongItemAlert by viewModel.wrongItemAlert.collectAsStateWithLifecycle()
    val substitutePrompt by viewModel.substitutePrompt.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Pull Orders (Outbound), 1: Purchase Orders (Inbound)
    var showCreateOrderSheet by remember { mutableStateOf(false) }
    var showCreatePurchaseOrderSheet by remember { mutableStateOf(false) }

    if (activeOrderId != null && activeOrderWithItems != null) {
        // Active Order Pulling Session
        ActiveOrderPickingSession(
            orderWithItems = activeOrderWithItems!!,
            inventoryItems = inventoryItems,
            viewModel = viewModel,
            onBack = { viewModel.setActiveOrder(null) }
        )
    } else {
        // Orders List
        Scaffold(
            modifier = modifier.fillMaxSize(),
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (selectedTab == 0) {
                            showCreateOrderSheet = true
                        } else {
                            showCreatePurchaseOrderSheet = true
                        }
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = {
                        Text(
                            text = if (selectedTab == 0) "New Pull Order" else "New Purchase Order",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    modifier = Modifier.testTag("create_order_fab")
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Screen Title
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Orders & Logistics",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Outbound order picking and inbound supplier purchase replenishment",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Tab Row for Outbound Picking vs Inbound Purchase Orders
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ShoppingCartCheckout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pull Orders (${allPullOrders.size})",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        modifier = Modifier.testTag("tab_pull_orders")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Purchase Orders (${allPurchaseOrders.size})",
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        modifier = Modifier.testTag("tab_purchase_orders")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // Outbound Pull Orders View
                    if (allPullOrders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCartCheckout,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No Active Pull Orders",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Create a new pull order to start barcode scanning, quantity verification, and stock picking.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { showCreateOrderSheet = true }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Create First Pull Order")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(allPullOrders, key = { it.order.orderId }) { orderWithItems ->
                                OrderCard(
                                    orderWithItems = orderWithItems,
                                    onStartPulling = {
                                        viewModel.setActiveOrder(orderWithItems.order.orderId)
                                    },
                                    onDeleteOrder = {
                                        viewModel.deleteOrder(orderWithItems.order)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Inbound Purchase Orders View
                    if (allPurchaseOrders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalShipping,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No Purchase Orders Registered",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Register inbound purchase orders for supplier shipments to track items on the way and mark low-stock items as replenished.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { showCreatePurchaseOrderSheet = true }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Register Purchase Order")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(allPurchaseOrders, key = { it.order.orderId }) { orderWithItems ->
                                PurchaseOrderCard(
                                    orderWithItems = orderWithItems,
                                    onReceiveEntirePO = {
                                        viewModel.receiveEntirePurchaseOrder(orderWithItems.order.orderId)
                                    },
                                    onReceiveItem = { orderItemId, qty ->
                                        viewModel.receivePurchaseOrderItem(orderWithItems.order.orderId, orderItemId, qty)
                                    },
                                    onDeletePO = {
                                        viewModel.deleteOrder(orderWithItems.order)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialogs & Sheets
    if (showCreateOrderSheet) {
        CreateOrderSheet(
            inventoryItems = inventoryItems,
            nextOrderNumberProvider = { viewModel.getNextOrderNumber("PULL") },
            onDismiss = { showCreateOrderSheet = false },
            onCreateOrder = { orderNum, customer, destination, selectedList ->
                viewModel.createNewOrder(orderNum, customer, destination, selectedList)
                showCreateOrderSheet = false
            }
        )
    }

    if (showCreatePurchaseOrderSheet) {
        CreatePurchaseOrderSheet(
            inventoryItems = inventoryItems,
            nextOrderNumberProvider = { viewModel.getNextOrderNumber("PURCHASE") },
            onDismiss = { showCreatePurchaseOrderSheet = false },
            onCreatePurchaseOrder = { orderNum, supplier, receivingBay, deliveryDate, notes, existingItems, newItems ->
                viewModel.createNewPurchaseOrder(
                    orderNumber = orderNum,
                    supplierName = supplier,
                    receivingBay = receivingBay,
                    expectedDeliveryDate = deliveryDate,
                    notes = notes,
                    existingItems = existingItems,
                    newItems = newItems
                )
                showCreatePurchaseOrderSheet = false
            }
        )
    }

    // Interactive Pull Quantity Reminder Dialog
    pullPrompt?.let { prompt ->
        PullQuantityReminderDialog(
            prompt = prompt,
            onConfirm = { qty ->
                viewModel.confirmPullQuantity(prompt.orderId, prompt.orderItem.orderItemId, qty)
            },
            onDismiss = { viewModel.closePullPrompt() }
        )
    }

    // Wrong Item Scanned Alert Dialog
    wrongItemAlert?.let { alert ->
        val currentOrder = activeOrderWithItems
        WrongItemScannedDialog(
            alert = alert,
            orderWithItems = currentOrder,
            onSubstitute = { targetOrderItem ->
                viewModel.openSubstituteDialog(alert.orderId, targetOrderItem, alert.scannedItem)
            },
            onAddAsExtra = { item, qty ->
                viewModel.addItemToOrder(alert.orderId, item, qty)
            },
            onDismiss = { viewModel.closeWrongItemAlert() }
        )
    }

    // Substitute Item Dialog
    substitutePrompt?.let { prompt ->
        SubstituteItemDialog(
            prompt = prompt,
            inventoryItems = inventoryItems,
            onConfirm = { newInvItem, targetQty ->
                viewModel.substituteOrderItem(
                    orderId = prompt.orderId,
                    orderItemId = prompt.targetOrderItem.orderItemId,
                    newInventoryItem = newInvItem,
                    newRequiredQty = targetQty
                )
            },
            onDismiss = { viewModel.closeSubstituteDialog() }
        )
    }
}

@Composable
fun OrderCard(
    orderWithItems: OrderWithItems,
    onStartPulling: () -> Unit,
    onDeleteOrder: () -> Unit
) {
    val totalRequired = orderWithItems.items.sumOf { it.quantityRequired }
    val totalPulled = orderWithItems.items.sumOf { it.quantityPulled }
    val progress = if (totalRequired > 0) totalPulled.toFloat() / totalRequired else 0f
    val isCompleted = orderWithItems.order.status == "COMPLETED" || (totalRequired > 0 && totalPulled >= totalRequired)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onStartPulling)
            .testTag("order_card_${orderWithItems.order.orderId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = orderWithItems.order.orderNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = orderWithItems.order.customerName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isCompleted -> VibrantGreenContainer
                        totalPulled > 0 -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = when {
                            isCompleted -> "COMPLETED"
                            totalPulled > 0 -> "IN PROGRESS"
                            else -> "PENDING"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isCompleted -> VibrantOnGreenContainer
                            totalPulled > 0 -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Destination: ${orderWithItems.order.destination}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Items Pulled: $totalPulled / $totalRequired",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCompleted) StockGreen else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (isCompleted) StockGreen else MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${orderWithItems.items.size} Line Items",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onDeleteOrder,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Button(
                        onClick = onStartPulling,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("start_picking_${orderWithItems.order.orderId}")
                    ) {
                        Icon(
                            imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isCompleted) "View Pick List" else "Scan & Pull Items", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveOrderPickingSession(
    orderWithItems: OrderWithItems,
    inventoryItems: List<InventoryItem>,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var selectedMode by remember { mutableIntStateOf(0) } // 0: Live Barcode Scanner, 1: Pick List
    val scanFeedback by viewModel.lastScanFeedback.collectAsStateWithLifecycle()
    var showAddItemSheet by remember { mutableStateOf(false) }

    val totalRequired = orderWithItems.items.sumOf { it.quantityRequired }
    val totalPulled = orderWithItems.items.sumOf { it.quantityPulled }
    val isComplete = totalRequired > 0 && totalPulled >= totalRequired

    // Sorted for route optimization by warehouse location!
    val sortedItems = remember(orderWithItems.items) {
        orderWithItems.items.sortedBy { it.location }
    }

    Scaffold(
        topBar = {
            Surface(
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pulling: ${orderWithItems.order.orderNumber}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${orderWithItems.order.customerName} • ${orderWithItems.order.destination}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Pulled status badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isComplete) StockGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "$totalPulled / $totalRequired Pulled",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isComplete) StockGreen else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    TabRow(selectedTabIndex = selectedMode) {
                        Tab(
                            selected = selectedMode == 0,
                            onClick = { selectedMode = 0 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Live Barcode Scanner")
                                }
                            }
                        )
                        Tab(
                            selected = selectedMode == 1,
                            onClick = { selectedMode = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick List (${sortedItems.count { it.isFulfilled }}/${sortedItems.size})")
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedMode == 0) {
                // Live Barcode Scanner with target item overlay
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        BarcodeScannerComposable(
                            onBarcodeDetected = { barcode ->
                                viewModel.processOrderPullScan(orderWithItems.order.orderId, barcode)
                            },
                            quickBarcodes = sortedItems.map { it.commonName to it.barcode },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Floating Scan Feedback Banner
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = scanFeedback != null,
                                enter = slideInVertically() + fadeIn(),
                                exit = slideOutVertically() + fadeOut()
                            ) {
                                scanFeedback?.let { fb ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (fb.isSuccess) VibrantGreen else VibrantRed
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = fb.message,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { viewModel.clearScanFeedback() },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom mini pick-route guide
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Route: Pick Next at Warehouse Location",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(onClick = { showAddItemSheet = true }) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Add Item", fontSize = 11.sp)
                                }
                            }
                            val nextUnfulfilled = sortedItems.firstOrNull { !it.isFulfilled }
                            if (nextUnfulfilled != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Shelf: ${nextUnfulfilled.location}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${nextUnfulfilled.commonName} • Target: ${nextUnfulfilled.quantityRequired - nextUnfulfilled.quantityPulled} needed",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            viewModel.openPullPromptForOrderItem(orderWithItems.order.orderId, nextUnfulfilled)
                                        }
                                    ) {
                                        Text("Pull Items", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Text(
                                    text = "🎉 All items in this order have been successfully pulled!",
                                    color = StockGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // Route-Optimized Order Pick List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Pick route sequenced by warehouse shelf location.",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Button(
                                    onClick = { showAddItemSheet = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Add Item", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    items(sortedItems, key = { it.orderItemId }) { item ->
                        OrderItemRow(
                            item = item,
                            onPull = {
                                viewModel.openPullPromptForOrderItem(orderWithItems.order.orderId, item)
                            },
                            onSubstitute = {
                                viewModel.openSubstituteDialog(orderWithItems.order.orderId, item)
                            },
                            onReset = {
                                viewModel.resetOrderItemPull(orderWithItems.order.orderId, item.orderItemId)
                            },
                            onRemove = {
                                viewModel.removeOrderItem(orderWithItems.order.orderId, item.orderItemId)
                            }
                        )
                    }

                    if (isComplete) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = StockGreen.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StockGreen, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Order Pulling Complete!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = StockGreen
                                    )
                                    Text(
                                        text = "All items have been verified and decremented from warehouse stock.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(onClick = onBack) {
                                        Text("Return to Orders")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Item to existing order sheet
    if (showAddItemSheet) {
        AddExtraItemToOrderSheet(
            inventoryItems = inventoryItems,
            onDismiss = { showAddItemSheet = false },
            onAddItem = { item, qty ->
                viewModel.addItemToOrder(orderWithItems.order.orderId, item, qty)
                showAddItemSheet = false
            }
        )
    }
}

@Composable
fun OrderItemRow(
    item: OrderItem,
    onPull: () -> Unit,
    onSubstitute: () -> Unit,
    onReset: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isFulfilled) StockGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Location Badge & Options Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(item.location, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isFulfilled) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = StockGreen
                        ) {
                            Text(
                                text = "FULFILLED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Substitute with Similar Item") },
                                leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onSubstitute()
                                }
                            )
                            if (item.quantityPulled > 0) {
                                DropdownMenuItem(
                                    text = { Text("Reset Pulled Quantity") },
                                    leadingIcon = { Icon(Icons.Default.Replay, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onReset()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Remove from Order", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onRemove()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.commonName,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Barcode: ${item.barcode}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Pulled count and action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Pulled: ${item.quantityPulled} / ${item.quantityRequired}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (item.isFulfilled) StockGreen else MaterialTheme.colorScheme.primary
                    )
                    if (!item.isFulfilled) {
                        Text(
                            text = "${item.quantityRequired - item.quantityPulled} remaining to pull",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onSubstitute,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Substitute", fontSize = 11.sp)
                    }

                    if (!item.isFulfilled) {
                        Button(
                            onClick = onPull,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Pull Items", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Pull Reminder Dialog with Desired Quantity Reminder and Adjustable Inputs
 */
@Composable
fun PullQuantityReminderDialog(
    prompt: PullQuantityPrompt,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var quantityText by remember { mutableStateOf(prompt.defaultQty.toString()) }
    val currentQty = quantityText.toIntOrNull() ?: 1
    val isStockShortage = prompt.availableStock < prompt.neededQty

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Inventory, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Pull Quantity Reminder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Shelf Location: ${prompt.orderItem.location}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Item Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(prompt.orderItem.commonName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Barcode: ${prompt.orderItem.barcode}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Already Pulled: ${prompt.orderItem.quantityPulled} / ${prompt.orderItem.quantityRequired}", fontSize = 12.sp)
                            Text("Available on Shelf: ${prompt.availableStock} ${prompt.inventoryItem.unit}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Prominent Reminder Callout
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Please pull ${prompt.neededQty} ${prompt.inventoryItem.unit} to fulfill this line item.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (isStockShortage) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = LowStockRed.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LowStockRed.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = LowStockRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Limited Shelf Stock! Only ${prompt.availableStock} available on shelf. Default set to available amount, or type actual quantity pulled.",
                                fontSize = 11.sp,
                                color = LowStockRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Quantity Pulled from Shelf:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))

                // Stepper + Direct input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val next = (currentQty - 1).coerceAtLeast(1)
                            quantityText = next.toString()
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.all { it.isDigit() }) {
                                quantityText = input
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(100.dp),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalIconButton(
                        onClick = {
                            val next = currentQty + 1
                            quantityText = next.toString()
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Preset Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedButton(
                        onClick = { quantityText = prompt.neededQty.toString() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Target (${prompt.neededQty})", fontSize = 11.sp)
                    }
                    if (isStockShortage && prompt.availableStock > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { quantityText = prompt.availableStock.toString() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Max Stock (${prompt.availableStock})", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalQty = quantityText.toIntOrNull() ?: prompt.defaultQty
                    if (finalQty > 0) {
                        onConfirm(finalQty)
                    }
                },
                enabled = (quantityText.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.testTag("confirm_pull_btn")
            ) {
                Text("Confirm Pull (${quantityText.ifEmpty { "0" }} ${prompt.inventoryItem.unit})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Wrong Item Scanned Alert Dialog with direct options to Substitute or Add to Order
 */
@Composable
fun WrongItemScannedDialog(
    alert: WrongItemAlert,
    orderWithItems: OrderWithItems?,
    onSubstitute: (targetOrderItem: OrderItem) -> Unit,
    onAddAsExtra: (item: InventoryItem, qty: Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = VibrantRed, modifier = Modifier.size(36.dp))
        },
        title = {
            Text(
                text = "Wrong Item Scanned!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VibrantRed,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (alert.scannedItem != null) {
                    Text(
                        text = "You scanned an item that is NOT in this order's pick list:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(alert.scannedItem.commonName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Old Code: ${alert.scannedItem.oldCode} • Barcode: ${alert.scannedItem.newCode}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Shelf Location: ${alert.scannedItem.location} • In Stock: ${alert.scannedItem.quantity} ${alert.scannedItem.unit}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "What would you like to do?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // If order has pending items, offer quick substitute
                    val pendingItems = orderWithItems?.items?.filter { !it.isFulfilled } ?: emptyList()
                    if (pendingItems.isNotEmpty()) {
                        Button(
                            onClick = {
                                onDismiss()
                                onSubstitute(pendingItems.first())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Use as Substitute for Order Item", fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onAddAsExtra(alert.scannedItem, 1)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add to this Order as Extra Item", fontSize = 12.sp)
                    }
                } else {
                    Text(
                        text = "Barcode '${alert.scannedBarcode}' was not found in the warehouse inventory database.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss / Scan Again", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Substitute Item Dialog with Smart Category & Keyword Recommendations and Fuzzy Search
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstituteItemDialog(
    prompt: SubstituteItemPrompt,
    inventoryItems: List<InventoryItem>,
    onConfirm: (newInventoryItem: InventoryItem, targetQuantity: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val originalInvItem = remember(prompt.targetOrderItem, inventoryItems) {
        inventoryItems.find { it.id == prompt.targetOrderItem.inventoryItemId }
            ?: InventoryItem(
                id = prompt.targetOrderItem.inventoryItemId,
                commonName = prompt.targetOrderItem.commonName,
                oldCode = "",
                newCode = prompt.targetOrderItem.barcode,
                quantity = 0,
                location = prompt.targetOrderItem.location
            )
    }

    val neededQty = (prompt.targetOrderItem.quantityRequired - prompt.targetOrderItem.quantityPulled).coerceAtLeast(1)
    var selectedItem by remember { mutableStateOf<InventoryItem?>(prompt.preselectedSubstitute) }
    var targetQtyText by remember { mutableStateOf(neededQty.toString()) }
    var searchQuery by remember { mutableStateOf("") }

    // Compute smart substitute recommendations
    val smartSuggestions = remember(originalInvItem, inventoryItems) {
        ItemSearchMatcher.findSubstitutes(originalInvItem, inventoryItems, limit = 6)
    }

    // Compute search matches
    val searchResults = remember(searchQuery, inventoryItems) {
        if (searchQuery.isNotBlank()) {
            ItemSearchMatcher.search(searchQuery, inventoryItems.filter { it.id != originalInvItem.id })
        } else {
            emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Substitute Line Item", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                // Original Item info
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Original Item to Replace:", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(prompt.targetOrderItem.commonName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Required: ${prompt.targetOrderItem.quantityRequired} • Location: ${prompt.targetOrderItem.location}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search field for substitute
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search substitute by name, code, category...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (searchQuery.isNotBlank()) "Search Results (${searchResults.size})" else "Recommended Substitutes (${smartSuggestions.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (searchQuery.isNotBlank()) {
                        if (searchResults.isEmpty()) {
                            item {
                                Text("No matching items found for '$searchQuery'", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                            }
                        } else {
                            items(searchResults) { result ->
                                val isSelected = selectedItem?.id == result.item.id
                                SubstituteCard(
                                    item = result.item,
                                    tag = result.matchReason,
                                    isSelected = isSelected,
                                    onSelect = { selectedItem = result.item }
                                )
                            }
                        }
                    } else {
                        if (smartSuggestions.isEmpty()) {
                            item {
                                Text("Search above to select any inventory item as substitute.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                            }
                        } else {
                            items(smartSuggestions) { suggestion ->
                                val isSelected = selectedItem?.id == suggestion.item.id
                                SubstituteCard(
                                    item = suggestion.item,
                                    tag = suggestion.matchReason,
                                    isSelected = isSelected,
                                    onSelect = { selectedItem = suggestion.item }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quantity to substitute
                selectedItem?.let { item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Selected: ${item.commonName}", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                                Text("Stock: ${item.quantity} ${item.unit} @ ${item.location}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Req Qty: ", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = targetQtyText,
                                    onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) targetQtyText = it },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.width(60.dp),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedItem?.let { item ->
                        val qty = targetQtyText.toIntOrNull() ?: neededQty
                        onConfirm(item, qty)
                    }
                },
                enabled = selectedItem != null && (targetQtyText.toIntOrNull() ?: 0) > 0
            ) {
                Text("Confirm Substitution")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SubstituteCard(
    item: InventoryItem,
    tag: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.commonName, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                Text("Stock: ${item.quantity} ${item.unit} • Shelf: ${item.location}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (tag.isNotEmpty()) {
                    Text(tag, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            } else {
                OutlinedButton(
                    onClick = onSelect,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Select", fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Add Extra Line Item directly to an in-progress order
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExtraItemToOrderSheet(
    inventoryItems: List<InventoryItem>,
    onDismiss: () -> Unit,
    onAddItem: (InventoryItem, Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<InventoryItem?>(null) }
    var quantityText by remember { mutableStateOf("1") }

    val searchResults = remember(searchQuery, inventoryItems) {
        ItemSearchMatcher.search(searchQuery, inventoryItems)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text("Add Item to Current Order", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search item name, old code, barcode, location...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(searchResults) { result ->
                    val isSelected = selectedItem?.id == result.item.id
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedItem = result.item }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(result.item.commonName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("In Stock: ${result.item.quantity} ${result.item.unit} • @ ${result.item.location}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (result.matchReason.isNotEmpty()) {
                                    Text(result.matchReason, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            selectedItem?.let { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Quantity to Add:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) quantityText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(90.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    selectedItem?.let { item ->
                        val qty = quantityText.toIntOrNull() ?: 1
                        onAddItem(item, qty)
                    }
                },
                enabled = selectedItem != null && (quantityText.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add to Order Pick List")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Enhanced Create Order Sheet with Robust Multi-Field / Typo-tolerant Search
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOrderSheet(
    inventoryItems: List<InventoryItem>,
    nextOrderNumberProvider: (suspend (String) -> String)? = null,
    onDismiss: () -> Unit,
    onCreateOrder: (orderNum: String, customer: String, destination: String, selectedItems: List<Pair<InventoryItem, Int>>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var orderNumber by remember { mutableStateOf("ORD-33100") }
    var customerName by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("Dock 1 - Staging Bay") }
    var itemSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (nextOrderNumberProvider != null) {
            val nextNum = nextOrderNumberProvider("PULL")
            if (nextNum.isNotBlank()) {
                orderNumber = nextNum
            }
        }
    }

    val selectedItems = remember { mutableStateListOf<Pair<InventoryItem, Int>>() }

    // Robust live multi-field / fuzzy search matching
    val matchedSearchResults = remember(itemSearchQuery, inventoryItems) {
        ItemSearchMatcher.search(itemSearchQuery, inventoryItems)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Create Warehouse Pull Order",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = orderNumber,
                onValueChange = { orderNumber = it },
                label = { Text("Order Number *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it },
                label = { Text("Customer / Client Name *") },
                placeholder = { Text("e.g. Apex Manufacturing") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text("Fulfillment Destination / Dock") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Robust Search for adding items
            Text(
                text = "Add Items to Order Pick List:",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = itemSearchQuery,
                onValueChange = { itemSearchQuery = it },
                placeholder = { Text("Search by name, old code, barcode, or category...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (itemSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { itemSearchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Items Catalog List with Robust Matches
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (matchedSearchResults.isEmpty()) {
                    item {
                        Text(
                            text = "No inventory items matched '$itemSearchQuery'",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    items(matchedSearchResults, key = { it.item.id }) { result ->
                        val item = result.item
                        val isSelected = selectedItems.any { it.first.id == item.id }
                        val currentQty = selectedItems.find { it.first.id == item.id }?.second ?: 0

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.commonName, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                    Text("Old Code: ${item.oldCode.ifEmpty { "N/A" }} • In Stock: ${item.quantity} ${item.unit} @ ${item.location}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (itemSearchQuery.isNotBlank() && result.matchReason.isNotEmpty()) {
                                        Text(result.matchReason, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                if (isSelected) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                val index = selectedItems.indexOfFirst { it.first.id == item.id }
                                                if (index >= 0) {
                                                    if (currentQty > 1) {
                                                        selectedItems[index] = item to (currentQty - 1)
                                                    } else {
                                                        selectedItems.removeAt(index)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                        Text("$currentQty", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                        IconButton(
                                            onClick = {
                                                val index = selectedItems.indexOfFirst { it.first.id == item.id }
                                                if (index >= 0) {
                                                    selectedItems[index] = item to (currentQty + 1)
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { selectedItems.add(item to 1) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Add", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Order Summary Chips if items selected
            if (selectedItems.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${selectedItems.size} items (${selectedItems.sumOf { it.second }} total units)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ready to Pick",
                            fontSize = 11.sp,
                            color = StockGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    if (orderNumber.isNotBlank() && customerName.isNotBlank() && selectedItems.isNotEmpty()) {
                        onCreateOrder(orderNumber, customerName, destination, selectedItems)
                    }
                },
                enabled = orderNumber.isNotBlank() && customerName.isNotBlank() && selectedItems.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("submit_create_order_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create & Start Picking (${selectedItems.size} items)")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

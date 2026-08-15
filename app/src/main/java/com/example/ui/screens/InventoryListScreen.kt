package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.InventoryItem
import com.example.ui.MainViewModel
import com.example.ui.ScannerMode
import com.example.ui.ScreenTab
import com.example.ui.components.CheckInSheet
import com.example.ui.components.InventoryItemCard
import com.example.ui.theme.LowStockRed
import com.example.ui.theme.StockGreen
import com.example.util.BarcodeGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryListScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val items by viewModel.inventoryItems.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterLowStock by viewModel.filterLowStockOnly.collectAsStateWithLifecycle()
    val filterNeedsPurchase by viewModel.filterNeedsPurchaseOnly.collectAsStateWithLifecycle()
    val filterOnTheWay by viewModel.filterOnTheWayOnly.collectAsStateWithLifecycle()
    val selectedLocation by viewModel.selectedLocationFilter.collectAsStateWithLifecycle()
    val allLocations by viewModel.allLocations.collectAsStateWithLifecycle()
    val lowStockCount by viewModel.lowStockCount.collectAsStateWithLifecycle()
    val needsPurchaseCount by viewModel.needsPurchaseCount.collectAsStateWithLifecycle()
    val onTheWayCount by viewModel.onTheWayCount.collectAsStateWithLifecycle()
    val replenishmentInfoMap by viewModel.replenishmentInfoMap.collectAsStateWithLifecycle()
    val checkInPrompt by viewModel.checkInPrompt.collectAsStateWithLifecycle()

    var showDetailItem by remember { mutableStateOf<InventoryItem?>(null) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showLocationMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddItem() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Stock", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("add_stock_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Bar with Warehouse Info & Export Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Warehouse Inventory",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${items.size} SKU${if (items.size != 1) "s" else ""} Registered",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = {
                            viewModel.setScannerMode(ScannerMode.CHECK_IN)
                            viewModel.setTab(ScreenTab.LIVE_SCANNER)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("checkin_shortcut_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Check-In", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Export Quick Menu Button
                    Box {
                        Button(
                            onClick = { showExportMenu = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("export_menu_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export CSV Spreadsheet") },
                                leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                                onClick = {
                                    showExportMenu = false
                                    viewModel.exportCsv()
                                },
                                modifier = Modifier.testTag("export_csv_action")
                            )
                            DropdownMenuItem(
                                text = { Text("Export PDF Stock Report") },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                onClick = {
                                    showExportMenu = false
                                    viewModel.exportPdf()
                                },
                                modifier = Modifier.testTag("export_pdf_action")
                            )
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Search by name, barcode, old code, location...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("inventory_search_input"),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips Row (Low Stock, Needs Purchase, On The Way, and Location Filter)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Needs Purchase Filter Chip (Critical low stock items with NO order on the way)
                item {
                    FilterChip(
                        selected = filterNeedsPurchase,
                        onClick = {
                            viewModel.filterNeedsPurchaseOnly.value = !filterNeedsPurchase
                            if (viewModel.filterNeedsPurchaseOnly.value) {
                                viewModel.filterOnTheWayOnly.value = false
                                viewModel.filterLowStockOnly.value = false
                            }
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (filterNeedsPurchase) MaterialTheme.colorScheme.onPrimary else LowStockRed
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Needs Purchase ($needsPurchaseCount)")
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LowStockRed,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_needs_purchase_chip")
                    )
                }

                // On The Way Filter Chip (Replenishment in transit from Purchase Orders)
                item {
                    FilterChip(
                        selected = filterOnTheWay,
                        onClick = {
                            viewModel.filterOnTheWayOnly.value = !filterOnTheWay
                            if (viewModel.filterOnTheWayOnly.value) {
                                viewModel.filterNeedsPurchaseOnly.value = false
                                viewModel.filterLowStockOnly.value = false
                            }
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🚚 On The Way ($onTheWayCount)",
                                    fontWeight = if (filterOnTheWay) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        modifier = Modifier.testTag("filter_on_the_way_chip")
                    )
                }

                // Low Stock Filter Chip
                item {
                    FilterChip(
                        selected = filterLowStock,
                        onClick = {
                            viewModel.filterLowStockOnly.value = !filterLowStock
                            if (viewModel.filterLowStockOnly.value) {
                                viewModel.filterNeedsPurchaseOnly.value = false
                                viewModel.filterOnTheWayOnly.value = false
                            }
                        },
                        label = {
                            Text("All Low Stock ($lowStockCount)")
                        },
                        modifier = Modifier.testTag("filter_low_stock_chip")
                    )
                }

                // Location Filter Chip
                item {
                    Box {
                        FilterChip(
                            selected = selectedLocation != null,
                            onClick = { showLocationMenu = true },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(selectedLocation ?: "All Locations")
                                }
                            },
                            modifier = Modifier.testTag("filter_location_chip")
                        )

                        DropdownMenu(
                            expanded = showLocationMenu,
                            onDismissRequest = { showLocationMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Locations") },
                                onClick = {
                                    viewModel.selectedLocationFilter.value = null
                                    showLocationMenu = false
                                }
                            )
                            allLocations.forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text(loc) },
                                    onClick = {
                                        viewModel.selectedLocationFilter.value = loc
                                        showLocationMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Inventory List
            if (items.isEmpty()) {
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
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when {
                                filterNeedsPurchase -> "No Items Needing Purchase Orders"
                                filterOnTheWay -> "No Items Currently On The Way"
                                filterLowStock -> "No Low Stock Items"
                                else -> "No Inventory Items Found"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap '+ Add Stock' or register an Inbound Purchase Order to restock warehouse items.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.openAddItem() }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Stock Item")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        val replInfo = replenishmentInfoMap[item.id]
                        InventoryItemCard(
                            item = item,
                            onClick = { showDetailItem = item },
                            onEdit = { viewModel.openEditItem(item) },
                            onAdjustStock = { delta ->
                                viewModel.adjustItemStock(item, delta)
                            },
                            onCheckIn = {
                                viewModel.openCheckInForItem(item, 1)
                            },
                            replenishmentInfo = replInfo,
                            onOrderRestock = {
                                viewModel.setTab(ScreenTab.ORDER_PULLING)
                            }
                        )
                    }
                }
            }
        }
    }

    // Detailed Item Bottom Sheet (Shows full barcode, old/new codes, location, and quick actions)
    showDetailItem?.let { item ->
        val replInfo = replenishmentInfoMap[item.id]
        ItemDetailSheet(
            item = item,
            replenishmentInfo = replInfo,
            onDismiss = { showDetailItem = null },
            onEdit = {
                showDetailItem = null
                viewModel.openEditItem(item)
            },
            onAdjust = { delta ->
                viewModel.adjustItemStock(item, delta)
            },
            onCheckIn = {
                showDetailItem = null
                viewModel.openCheckInForItem(item, 1)
            }
        )
    }

    // Modal Check-In Bottom Sheet
    checkInPrompt?.let { prompt ->
        CheckInSheet(
            item = prompt.item,
            initialQty = prompt.initialQty,
            defaultLocation = prompt.defaultLocation,
            onDismiss = { viewModel.closeCheckInPrompt() },
            onConfirmCheckIn = { qty, location, ref, notes ->
                viewModel.confirmCheckIn(
                    itemId = prompt.item.id,
                    quantityReceived = qty,
                    updatedLocation = location,
                    referenceDoc = ref,
                    notes = notes
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailSheet(
    item: InventoryItem,
    replenishmentInfo: com.example.ui.ItemReplenishmentInfo? = null,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onAdjust: (Int) -> Unit,
    onCheckIn: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var barcodeBitmap by remember(item.newCode) { mutableStateOf<Bitmap?>(null) }

    val onTheWayQty = replenishmentInfo?.onTheWayQuantity ?: 0
    val isOnTheWayCovered = replenishmentInfo?.isOnTheWayCovered == true

    LaunchedEffect(item.newCode) {
        barcodeBitmap = BarcodeGenerator.generateBarcodeBitmap(item.newCode, width = 600, height = 180)
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Stock SKU Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Photo Preview & Main info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (!item.photoUri.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.photoUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.commonName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = item.commonName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Category: ${item.category}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Inbound Shipment / Replenishment status
            if (onTheWayQty > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isOnTheWayCovered) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🚚 ON THE WAY: +$onTheWayQty ${item.unit}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isOnTheWayCovered) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val poStr = replenishmentInfo?.pendingOrderNumbers?.joinToString(", ") ?: ""
                        Text(
                            text = if (isOnTheWayCovered) {
                                "Covered by incoming Purchase Order(s): $poStr.\nNo additional purchases needed."
                            } else {
                                "Incoming Purchase Order(s): $poStr.\nRemaining deficit to reach minimum threshold."
                            },
                            fontSize = 12.sp,
                            color = if (isOnTheWayCovered) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Codes Card (Old Code & New Code)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Old Code", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.oldCode, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column {
                            Text("New Code (Barcode)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.newCode, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Location in Warehouse", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(item.location, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Barcode Graphic
            if (barcodeBitmap != null) {
                Text(
                    text = "Scannable Barcode Label:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = barcodeBitmap!!.asImageBitmap(),
                        contentDescription = "Barcode",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(
                    text = item.newCode,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stock Controls & Alerts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Current Stock: ${item.quantity} ${item.unit}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isLowStock) LowStockRed else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Threshold: Min ${item.minThreshold} ${item.unit}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onAdjust(-1) }) { Text("-1") }
                    Button(onClick = { onAdjust(+1) }) { Text("+1") }
                    Button(onClick = { onAdjust(+10) }) { Text("+10") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Check-In and Edit Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCheckIn,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("detail_sheet_checkin_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Check-In Stock", fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("detail_sheet_edit_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Details")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

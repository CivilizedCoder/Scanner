package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.InventoryItem
import com.example.ui.MainViewModel
import com.example.ui.ScannerMode
import com.example.ui.SessionReceivedItem
import com.example.ui.components.BarcodeScannerComposable
import com.example.ui.components.CheckInSheet
import com.example.ui.theme.LowStockRed
import com.example.ui.theme.VibrantAmber
import com.example.ui.theme.VibrantAmberContainer
import com.example.ui.theme.VibrantGreen
import com.example.ui.theme.VibrantGreenContainer
import com.example.ui.theme.VibrantOnAmberContainer
import com.example.ui.theme.VibrantOnGreenContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScannerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val scannerMode by viewModel.scannerMode.collectAsStateWithLifecycle()
    val scanFeedback by viewModel.lastScanFeedback.collectAsStateWithLifecycle()
    val inventoryItems by viewModel.inventoryItems.collectAsStateWithLifecycle()
    val checkInPrompt by viewModel.checkInPrompt.collectAsStateWithLifecycle()
    val sessionReceivedItems by viewModel.sessionReceivedItems.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSessionSummarySheet by remember { mutableStateOf(false) }
    var showManualCheckInSearch by remember { mutableStateOf(false) }

    val totalSessionUnits = sessionReceivedItems.sumOf { it.quantityReceived }
    val totalSessionSkus = sessionReceivedItems.map { it.itemId }.distinct().size

    Box(modifier = modifier.fillMaxSize()) {
        // Core Barcode Camera Scanner
        BarcodeScannerComposable(
            modifier = Modifier.fillMaxSize(),
            overlayTitle = if (scannerMode == ScannerMode.CHECK_IN) {
                "Scan incoming shipment / box barcode"
            } else {
                "Scan any warehouse barcode to lookup"
            },
            onBarcodeDetected = { barcode ->
                viewModel.processStandaloneScan(barcode)
            },
            quickBarcodes = inventoryItems.take(6).map { it.commonName.take(14) to it.newCode }
        )

        // Top Scanner Mode Switcher Overlay
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                TabRow(
                    selectedTabIndex = if (scannerMode == ScannerMode.CHECK_IN) 0 else 1,
                    containerColor = Color.Transparent,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[if (scannerMode == ScannerMode.CHECK_IN) 0 else 1]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Tab(
                        selected = scannerMode == ScannerMode.CHECK_IN,
                        onClick = { viewModel.setScannerMode(ScannerMode.CHECK_IN) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Inbound Check-In", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        },
                        modifier = Modifier.testTag("tab_mode_checkin")
                    )
                    Tab(
                        selected = scannerMode == ScannerMode.LOOKUP,
                        onClick = { viewModel.setScannerMode(ScannerMode.LOOKUP) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Quick Lookup", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        },
                        modifier = Modifier.testTag("tab_mode_lookup")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Mode Sub-banner / Receiving Shift Indicator
                if (scannerMode == ScannerMode.CHECK_IN) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VibrantGreenContainer.copy(alpha = 0.85f),
                            modifier = Modifier.clickable { showSessionSummarySheet = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = VibrantOnGreenContainer, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (totalSessionUnits > 0) "Received: $totalSessionUnits units ($totalSessionSkus SKUs)" else "Ready to receive stock",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VibrantOnGreenContainer
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(
                                onClick = { showManualCheckInSearch = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp).testTag("manual_checkin_search_btn")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Select Item", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            if (sessionReceivedItems.isNotEmpty()) {
                                FilledTonalIconButton(
                                    onClick = { showSessionSummarySheet = true },
                                    modifier = Modifier.size(28.dp).testTag("view_session_log_btn")
                                ) {
                                    Icon(Icons.Default.History, contentDescription = "History", modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Point camera at any barcode for instant SKU & location details",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Floating Result / Feedback Card
        AnimatedVisibility(
            visible = scanFeedback != null && checkInPrompt == null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            scanFeedback?.let { fb ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("scan_result_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (fb.isSuccess) VibrantGreenContainer else VibrantAmberContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (fb.isSuccess) Icons.Default.QrCode else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (fb.isSuccess) VibrantOnGreenContainer else VibrantOnAmberContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (fb.isSuccess) "MATCHED IN INVENTORY" else "NEW / UNREGISTERED",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (fb.isSuccess) VibrantOnGreenContainer else VibrantOnAmberContainer
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.clearScanFeedback() },
                                modifier = Modifier.size(28.dp).testTag("close_scan_feedback_btn")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (fb.item != null) {
                            val item = fb.item
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp))
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
                                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.commonName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Old: ${item.oldCode}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Barcode: ${item.newCode}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Location
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Shelf: ${item.location}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Stock status and quick actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Stock: ${item.quantity} ${item.unit}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.isLowStock) LowStockRed else VibrantGreen
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            viewModel.openCheckInForItem(item, 1)
                                            viewModel.clearScanFeedback()
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp).testTag("quick_checkin_btn")
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Check-In", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            viewModel.adjustItemStock(item, -1, "Quick Scanner Pull")
                                            viewModel.processStandaloneScan(item.newCode)
                                        },
                                        enabled = item.quantity > 0,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Pull -1", fontSize = 11.sp)
                                    }

                                    FilledTonalIconButton(
                                        onClick = {
                                            viewModel.clearScanFeedback()
                                            viewModel.openEditItem(item)
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                        } else {
                            // Unregistered Barcode Ingest CTA
                            Text(
                                text = "Barcode '${fb.barcode}' is not in the system yet.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Incoming delivery with a new item? Add and check it into inventory immediately.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    viewModel.openAddItem(initialBarcode = fb.barcode)
                                    viewModel.clearScanFeedback()
                                },
                                modifier = Modifier.fillMaxWidth().testTag("quick_ingest_scanned_barcode_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Register & Check In New Product", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
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

    // Shift Inbound Receiving Summary Bottom Sheet
    if (showSessionSummarySheet) {
        ModalBottomSheet(
            onDismissRequest = { showSessionSummarySheet = false },
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

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
                    Column {
                        Text(
                            text = "Inbound Receiving Shift Log",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Received $totalSessionUnits total units across $totalSessionSkus SKUs",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showSessionSummarySheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (sessionReceivedItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No items checked in during this shift yet.\nScan barcodes to start receiving stock.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sessionReceivedItems, key = { it.id }) { record ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(record.itemName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("Code: ${record.barcode}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                            Text("•", fontSize = 11.sp)
                                            Text("Loc: ${record.location}", fontSize = 11.sp)
                                        }
                                        if (!record.reference.isNullOrBlank()) {
                                            Text("Ref: ${record.reference}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = VibrantGreenContainer
                                            ) {
                                                Text(
                                                    text = "+${record.quantityReceived} ${record.unit}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = VibrantOnGreenContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                timeFormat.format(Date(record.timestamp)),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        IconButton(
                                            onClick = { viewModel.undoSessionReceivedItem(record) },
                                            modifier = Modifier.size(32.dp).testTag("undo_received_btn_${record.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.Undo,
                                                contentDescription = "Undo Check-In",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.clearCheckInSession()
                                showSessionSummarySheet = false
                            }
                        ) {
                            Text("Clear Session Summary")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Manual Item Search for Check-In (For unscannable / damaged barcodes)
    if (showManualCheckInSearch) {
        var query by remember { mutableStateOf("") }
        val filtered = remember(query, inventoryItems) {
            if (query.isBlank()) inventoryItems else {
                inventoryItems.filter {
                    it.commonName.contains(query, ignoreCase = true) ||
                    it.oldCode.contains(query, ignoreCase = true) ||
                    it.newCode.contains(query, ignoreCase = true) ||
                    it.location.contains(query, ignoreCase = true)
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showManualCheckInSearch = false },
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
                    Column {
                        Text(
                            text = "Manual Item Check-In",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Select item to receive stock",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showManualCheckInSearch = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search by name, code, location...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("manual_checkin_search_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showManualCheckInSearch = false
                                    viewModel.openCheckInForItem(item, 1)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.commonName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Barcode: ${item.newCode}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Text("•", fontSize = 11.sp)
                                        Text("Location: ${item.location}", fontSize = 11.sp)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${item.quantity} ${item.unit}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InventoryItem
import com.example.data.repository.NewItemOrderRequest
import com.example.ui.theme.LowStockRed
import com.example.ui.theme.StockGreen
import com.example.util.ItemSearchMatcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePurchaseOrderSheet(
    inventoryItems: List<InventoryItem>,
    nextOrderNumberProvider: suspend (String) -> String,
    onDismiss: () -> Unit,
    onCreatePurchaseOrder: (
        orderNum: String,
        supplier: String,
        receivingBay: String,
        deliveryDate: String?,
        notes: String?,
        existingItems: List<Pair<InventoryItem, Int>>,
        newItems: List<NewItemOrderRequest>
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var orderNumber by remember { mutableStateOf("PO-33100") }
    var supplierName by remember { mutableStateOf("") }
    var receivingBay by remember { mutableStateOf("Dock 2 - Inbound Staging") }
    var expectedDeliveryDate by remember { mutableStateOf("In 2 Business Days") }
    var orderNotes by remember { mutableStateOf("") }

    var activeTab by remember { mutableIntStateOf(0) } // 0: Existing Items, 1: Register New Items
    var itemSearchQuery by remember { mutableStateOf("") }
    var filterLowStockOnly by remember { mutableStateOf(false) }

    val selectedExistingItems = remember { mutableStateListOf<Pair<InventoryItem, Int>>() }
    val newCatalogItems = remember { mutableStateListOf<NewItemOrderRequest>() }

    // New Item Dialog / Form State
    var showAddNewItemDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newOldCode by remember { mutableStateOf("") }
    var newBarcode by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("Fasteners") }
    var newLocation by remember { mutableStateOf("Aisle 1, Bin 01") }
    var newUnit by remember { mutableStateOf("pcs") }
    var newMinThresholdText by remember { mutableStateOf("10") }
    var newOrderQtyText by remember { mutableStateOf("50") }

    LaunchedEffect(Unit) {
        val nextSeq = nextOrderNumberProvider("PURCHASE")
        if (nextSeq.isNotBlank()) {
            orderNumber = nextSeq
        }
    }

    val matchedExistingItems = remember(itemSearchQuery, filterLowStockOnly, inventoryItems) {
        val base = if (itemSearchQuery.isBlank()) {
            inventoryItems.map { com.example.util.ItemSearchResult(it, 100, "Catalog Listing", "Default") }
        } else {
            ItemSearchMatcher.search(itemSearchQuery, inventoryItems)
        }
        if (filterLowStockOnly) {
            base.filter { it.item.isLowStock }
        } else {
            base
        }
    }

    val totalItemsCount = selectedExistingItems.size + newCatalogItems.size
    val totalUnitsCount = selectedExistingItems.sumOf { it.second } + newCatalogItems.sumOf { it.quantityOrdered }

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
            // Title Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Register Inbound Purchase Order",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Order Header Fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = orderNumber,
                    onValueChange = { orderNumber = it },
                    label = { Text("PO Number *") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("po_number_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = supplierName,
                    onValueChange = { supplierName = it },
                    label = { Text("Supplier / Vendor *") },
                    placeholder = { Text("e.g. Grainger Supply") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f).testTag("po_supplier_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = receivingBay,
                    onValueChange = { receivingBay = it },
                    label = { Text("Receiving Bay / Staging") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = expectedDeliveryDate,
                    onValueChange = { expectedDeliveryDate = it },
                    label = { Text("Expected Delivery") },
                    placeholder = { Text("e.g. Tomorrow") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Item Selection Tabs: Existing Items vs Register New Items
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = {
                        Text(
                            text = "Restock Existing (${selectedExistingItems.size})",
                            fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = {
                        Text(
                            text = "Register New Items (${newCatalogItems.size})",
                            fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab Content 0: Existing Inventory Catalog
            if (activeTab == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = itemSearchQuery,
                        onValueChange = { itemSearchQuery = it },
                        placeholder = { Text("Search catalog items...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (itemSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { itemSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    FilterChip(
                        selected = filterLowStockOnly,
                        onClick = { filterLowStockOnly = !filterLowStockOnly },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (filterLowStockOnly) MaterialTheme.colorScheme.onPrimary else LowStockRed
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Low Stock", fontSize = 11.sp)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (matchedExistingItems.isEmpty()) {
                        item {
                            Text(
                                text = "No items matched filter.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        items(matchedExistingItems, key = { it.item.id }) { result ->
                            val item = result.item
                            val isSelected = selectedExistingItems.any { it.first.id == item.id }
                            val currentQty = selectedExistingItems.find { it.first.id == item.id }?.second ?: 0

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
                                        Text(
                                            text = "Stock: ${item.quantity} ${item.unit} (Min: ${item.minThreshold}) • Loc: ${item.location}",
                                            fontSize = 11.sp,
                                            color = if (item.isLowStock) LowStockRed else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (isSelected) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    val index = selectedExistingItems.indexOfFirst { it.first.id == item.id }
                                                    if (index >= 0) {
                                                        if (currentQty > 5) {
                                                            selectedExistingItems[index] = item to (currentQty - 5)
                                                        } else if (currentQty > 1) {
                                                            selectedExistingItems[index] = item to (currentQty - 1)
                                                        } else {
                                                            selectedExistingItems.removeAt(index)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                            Text("+$currentQty", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                            IconButton(
                                                onClick = {
                                                    val index = selectedExistingItems.indexOfFirst { it.first.id == item.id }
                                                    if (index >= 0) {
                                                        selectedExistingItems[index] = item to (currentQty + 10)
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                val suggestedReplenish = ((item.minThreshold * 2) - item.quantity).coerceAtLeast(10)
                                                selectedExistingItems.add(item to suggestedReplenish)
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Order", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab Content 1: Register New Items into Catalog
                Column(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                    Button(
                        onClick = {
                            newName = ""
                            newOldCode = ""
                            newBarcode = "89010010${(10..99).random()}"
                            newMinThresholdText = "10"
                            newOrderQtyText = "50"
                            showAddNewItemDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("add_new_sku_to_po_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add New Product / SKU to Purchase Order")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (newCatalogItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No new products added yet. Click above to define a new catalog item.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(newCatalogItems) { newItem ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary
                                                ) {
                                                    Text(
                                                        text = "NEW SKU",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(newItem.commonName, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                            }
                                            Text(
                                                text = "Barcode: ${newItem.newCode} • Order Qty: +${newItem.quantityOrdered} ${newItem.unit} @ ${newItem.location}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }

                                        IconButton(
                                            onClick = { newCatalogItems.remove(newItem) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = LowStockRed, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Order Summary Banner
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
                    Column {
                        Text(
                            text = "PO Total: $totalItemsCount line items ($totalUnitsCount units)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Items will show as 'ON THE WAY' in stock overview",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Submit Button
            Button(
                onClick = {
                    if (orderNumber.isNotBlank() && supplierName.isNotBlank() && totalItemsCount > 0) {
                        onCreatePurchaseOrder(
                            orderNumber.trim(),
                            supplierName.trim(),
                            receivingBay.trim(),
                            expectedDeliveryDate.trim().ifEmpty { null },
                            orderNotes.trim().ifEmpty { null },
                            selectedExistingItems.toList(),
                            newCatalogItems.toList()
                        )
                        onDismiss()
                    }
                },
                enabled = orderNumber.isNotBlank() && supplierName.isNotBlank() && totalItemsCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("submit_purchase_order_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Confirm & Register Purchase Order ($totalItemsCount Items)")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Modal Dialog to Register New Item definition
    if (showAddNewItemDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddNewItemDialog = false },
            title = { Text("Register New Catalog Product") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Product / Item Name *") },
                        placeholder = { Text("e.g. Copper Pipe 3/4 inch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_item_name_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = newBarcode,
                            onValueChange = { newBarcode = it },
                            label = { Text("Barcode / Code *") },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f).testTag("new_item_barcode_input"),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = newOldCode,
                            onValueChange = { newOldCode = it },
                            label = { Text("Old Code") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = newCategory,
                            onValueChange = { newCategory = it },
                            label = { Text("Category") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = newLocation,
                            onValueChange = { newLocation = it },
                            label = { Text("Storage Location") },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = newMinThresholdText,
                            onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) newMinThresholdText = it },
                            label = { Text("Min Stock") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = newOrderQtyText,
                            onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) newOrderQtyText = it },
                            label = { Text("Order Qty *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("new_item_order_qty_input"),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = newOrderQtyText.toIntOrNull() ?: 1
                        val minThresh = newMinThresholdText.toIntOrNull() ?: 10
                        if (newName.isNotBlank() && newBarcode.isNotBlank() && qty > 0) {
                            newCatalogItems.add(
                                NewItemOrderRequest(
                                    commonName = newName.trim(),
                                    oldCode = newOldCode.trim(),
                                    newCode = newBarcode.trim(),
                                    category = newCategory.trim().ifEmpty { "General" },
                                    location = newLocation.trim().ifEmpty { "Aisle 1, Bin 01" },
                                    unit = newUnit.trim().ifEmpty { "pcs" },
                                    minThreshold = minThresh,
                                    quantityOrdered = qty
                                )
                            )
                            showAddNewItemDialog = false
                        }
                    },
                    enabled = newName.isNotBlank() && newBarcode.isNotBlank() && (newOrderQtyText.toIntOrNull() ?: 0) > 0
                ) {
                    Text("Add to PO")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNewItemDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

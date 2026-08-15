package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.InventoryItem
import com.example.ui.components.BarcodeScannerComposable
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditItemSheet(
    item: InventoryItem?,
    initialBarcode: String?,
    onDismiss: () -> Unit,
    onSave: (
        commonName: String,
        oldCode: String,
        newCode: String,
        photoUri: String?,
        quantity: Int,
        location: String,
        minThreshold: Int,
        unit: String,
        category: String
    ) -> Unit,
    onDelete: ((InventoryItem) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var commonName by remember { mutableStateOf(item?.commonName ?: "") }
    var oldCode by remember { mutableStateOf(item?.oldCode ?: "") }
    var newCode by remember { mutableStateOf(item?.newCode ?: initialBarcode ?: "") }
    var photoUri by remember { mutableStateOf(item?.photoUri) }
    var quantityText by remember { mutableStateOf(item?.quantity?.toString() ?: "10") }
    var minThresholdText by remember { mutableStateOf(item?.minThreshold?.toString() ?: "5") }
    var location by remember { mutableStateOf(item?.location ?: "Aisle 01 - Rack A - Shelf 01") }
    var unit by remember { mutableStateOf(item?.unit ?: "pcs") }
    var category by remember { mutableStateOf(item?.category ?: "Mechanical") }

    var showCameraScannerDialog by remember { mutableStateOf(false) }

    // Photo Capture Launcher (Bitmap from Camera)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val savedUri = saveBitmapToFile(context, bitmap)
            photoUri = savedUri
        }
    }

    // Photo Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            photoUri = uri.toString()
        }
    }

    val categories = listOf(
        "Mechanical", "Electrical", "Fasteners", "Seals & Gaskets",
        "Pneumatics", "Chemicals", "Packaging", "General"
    )

    val units = listOf("pcs", "units", "spools", "bundles", "cartridges", "boxes", "kg", "meters")

    val locationPresets = listOf(
        "Aisle 01 - Rack A", "Aisle 01 - Rack B",
        "Aisle 02 - Rack A", "Aisle 02 - Rack B",
        "Aisle 03 - Rack A", "Aisle 04 - Bulk Zone"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (item == null) "Add New Stock Item" else "Edit Stock Item",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Photo Capture & Preview Row
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { cameraLauncher.launch(null) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!photoUri.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(photoUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Stock Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = "Add Photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Stock Product Photo",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { cameraLauncher.launch(null) },
                                modifier = Modifier.testTag("snap_photo_btn")
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Snap Photo", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { galleryLauncher.launch("image/*") }
                            ) {
                                Text("Gallery", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Barcode (New Code) with Camera Scan Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newCode,
                    onValueChange = { newCode = it },
                    label = { Text("New Code (Barcode Number) *") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_new_code"),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { showCameraScannerDialog = true },
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("scan_barcode_for_input_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan with Camera",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Common Name
            OutlinedTextField(
                value = commonName,
                onValueChange = { commonName = it },
                label = { Text("Common Name / Description *") },
                placeholder = { Text("e.g., 6204-2RS Deep Groove Ball Bearing") },
                singleLine = false,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_common_name"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Old Code
            OutlinedTextField(
                value = oldCode,
                onValueChange = { oldCode = it },
                label = { Text("Old Code / Legacy SKU *") },
                placeholder = { Text("e.g., BRG-6204") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_old_code"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Warehouse Location
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Warehouse Location *") },
                placeholder = { Text("Aisle 01 - Rack A - Shelf 02 - Bin 14") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_location"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))
            // Location presets chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                locationPresets.forEach { preset ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clickable { location = "$preset - Shelf 01" }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = preset,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quantity & Min Low-Stock Threshold
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter { c -> c.isDigit() } },
                    label = { Text("Quantity in Stock *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_quantity"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = minThresholdText,
                    onValueChange = { minThresholdText = it.filter { c -> c.isDigit() } },
                    label = { Text("Low Stock Alert Min *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_min_threshold"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Unit Selection
            Text(
                text = "Unit of Measurement",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                units.forEach { u ->
                    FilterChip(
                        selected = unit == u,
                        onClick = { unit = u },
                        label = { Text(u, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Selection
            Text(
                text = "Category",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save and Delete Buttons
            Button(
                onClick = {
                    val qty = quantityText.toIntOrNull() ?: 0
                    val minThresh = minThresholdText.toIntOrNull() ?: 5
                    if (commonName.isNotBlank() && newCode.isNotBlank()) {
                        onSave(
                            commonName,
                            oldCode.ifBlank { "SKU-${newCode.takeLast(4)}" },
                            newCode,
                            photoUri,
                            qty,
                            location.ifBlank { "Aisle 01 - Rack A" },
                            minThresh,
                            unit,
                            category
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_item_submit_btn"),
                enabled = commonName.isNotBlank() && newCode.isNotBlank(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (item == null) "Add to Warehouse Inventory" else "Save Changes", fontWeight = FontWeight.Bold)
            }

            if (item != null && onDelete != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onDelete(item) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("delete_item_btn"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Stock Item")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Camera Barcode Scanner Dialog for Fast Data Entry
    if (showCameraScannerDialog) {
        Dialog(
            onDismissRequest = { showCameraScannerDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                BarcodeScannerComposable(
                    modifier = Modifier.fillMaxSize(),
                    overlayTitle = "Scan product barcode to auto-fill",
                    onBarcodeDetected = { scannedCode ->
                        newCode = scannedCode
                        showCameraScannerDialog = false
                    },
                    quickBarcodes = listOf(
                        "Bearing" to "840192837461",
                        "Hex Bolt" to "793573192014",
                        "O-Ring" to "918273645019",
                        "Sensor" to "638492017582"
                    )
                )

                IconButton(
                    onClick = { showCameraScannerDialog = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        }
    }
}

private fun saveBitmapToFile(context: Context, bitmap: Bitmap): String? {
    return try {
        val photosDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
        val photoFile = File(photosDir, "stock_${System.currentTimeMillis()}.jpg")
        FileOutputStream(photoFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        photoFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

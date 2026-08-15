package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.ShoppingCartCheckout
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.ScreenTab
import com.example.ui.screens.AddEditItemSheet
import com.example.ui.screens.InventoryListScreen
import com.example.ui.screens.LiveScannerScreen
import com.example.ui.screens.OrderPullingScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.theme.LowStockRed
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                InventoryAppRoot()
            }
        }
    }
}

@Composable
fun InventoryAppRoot(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val lowStockCount by viewModel.lowStockCount.collectAsStateWithLifecycle()
    val showAddEditSheet by viewModel.showAddEditSheet.collectAsStateWithLifecycle()
    val editingItem by viewModel.editingItem.collectAsStateWithLifecycle()
    val prefillBarcode by viewModel.cameraPrefillBarcode.collectAsStateWithLifecycle()
    val activeOrderId by viewModel.activeOrderId.collectAsStateWithLifecycle()

    // Request Notification Permission for API 33+ (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // Hide bottom bar during active picking session to maximize scanner real estate
            if (selectedTab != ScreenTab.ORDER_PULLING || activeOrderId == null) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        // 1. Inventory Tab
                        NavigationBarItem(
                            selected = selectedTab == ScreenTab.INVENTORY,
                            onClick = { viewModel.setTab(ScreenTab.INVENTORY) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (lowStockCount > 0) {
                                            Badge(
                                                containerColor = LowStockRed,
                                                contentColor = Color.White
                                            ) {
                                                Text("$lowStockCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selectedTab == ScreenTab.INVENTORY) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                                        contentDescription = "Inventory",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            label = { Text("Inventory", fontWeight = if (selectedTab == ScreenTab.INVENTORY) FontWeight.Bold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("tab_inventory")
                        )

                        // 2. Order Pulling Tab
                        NavigationBarItem(
                            selected = selectedTab == ScreenTab.ORDER_PULLING,
                            onClick = { viewModel.setTab(ScreenTab.ORDER_PULLING) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == ScreenTab.ORDER_PULLING) Icons.Filled.ShoppingCartCheckout else Icons.Outlined.ShoppingCartCheckout,
                                    contentDescription = "Pull Orders",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text("Pull Orders", fontWeight = if (selectedTab == ScreenTab.ORDER_PULLING) FontWeight.Bold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("tab_orders")
                        )

                        // 3. Live Scanner Tab
                        NavigationBarItem(
                            selected = selectedTab == ScreenTab.LIVE_SCANNER,
                            onClick = { viewModel.setTab(ScreenTab.LIVE_SCANNER) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == ScreenTab.LIVE_SCANNER) Icons.Filled.QrCodeScanner else Icons.Outlined.QrCodeScanner,
                                    contentDescription = "Quick Scan",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text("Quick Scan", fontWeight = if (selectedTab == ScreenTab.LIVE_SCANNER) FontWeight.Bold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("tab_scanner")
                        )

                        // 4. Reports Tab
                        NavigationBarItem(
                            selected = selectedTab == ScreenTab.REPORTS,
                            onClick = { viewModel.setTab(ScreenTab.REPORTS) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == ScreenTab.REPORTS) Icons.Filled.Assessment else Icons.Outlined.Assessment,
                                    contentDescription = "Reports",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text("Reports", fontWeight = if (selectedTab == ScreenTab.REPORTS) FontWeight.Bold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("tab_reports")
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
            when (selectedTab) {
                ScreenTab.INVENTORY -> InventoryListScreen(viewModel = viewModel)
                ScreenTab.ORDER_PULLING -> OrderPullingScreen(viewModel = viewModel)
                ScreenTab.LIVE_SCANNER -> LiveScannerScreen(viewModel = viewModel)
                ScreenTab.REPORTS -> ReportsScreen(viewModel = viewModel)
            }
        }
    }

    // Global Add / Edit Stock Bottom Sheet
    if (showAddEditSheet) {
        AddEditItemSheet(
            item = editingItem,
            initialBarcode = prefillBarcode,
            onDismiss = { viewModel.closeAddEditSheet() },
            onSave = { commonName, oldCode, newCode, photoUri, quantity, location, minThreshold, unit, category ->
                viewModel.saveInventoryItem(
                    commonName = commonName,
                    oldCode = oldCode,
                    newCode = newCode,
                    photoUri = photoUri,
                    quantity = quantity,
                    location = location,
                    minThreshold = minThreshold,
                    unit = unit,
                    category = category
                )
            },
            onDelete = if (editingItem != null) {
                { item ->
                    viewModel.deleteItem(item)
                    viewModel.closeAddEditSheet()
                }
            } else null
        )
    }
}

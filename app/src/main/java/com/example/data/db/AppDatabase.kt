package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.InventoryDao
import com.example.data.dao.OrderDao
import com.example.data.dao.StockLogDao
import com.example.data.model.InventoryItem
import com.example.data.model.Order
import com.example.data.model.OrderItem
import com.example.data.model.StockLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        InventoryItem::class,
        Order::class,
        OrderItem::class,
        StockLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao
    abstract fun orderDao(): OrderDao
    abstract fun stockLogDao(): StockLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "warehouse_inventory.db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }
        }

        suspend fun populateInitialData(database: AppDatabase) {
            val inventoryDao = database.inventoryDao()
            val orderDao = database.orderDao()
            val logDao = database.stockLogDao()

            if (inventoryDao.getItemCount() > 0) return

            val sampleItems = listOf(
                InventoryItem(
                    commonName = "6204-2RS Deep Groove Ball Bearing",
                    oldCode = "BRG-6204",
                    newCode = "840192837461",
                    quantity = 85,
                    location = "Aisle 01 - Rack A - Shelf 02 - Bin 14",
                    minThreshold = 20,
                    unit = "pcs",
                    category = "Mechanical",
                    photoUri = null
                ),
                InventoryItem(
                    commonName = "M8 x 40mm Zinc Hex Bolt Grade 8.8",
                    oldCode = "BLT-M840",
                    newCode = "793573192014",
                    quantity = 240,
                    location = "Aisle 01 - Rack B - Shelf 01 - Bin 06",
                    minThreshold = 50,
                    unit = "pcs",
                    category = "Fasteners",
                    photoUri = null
                ),
                InventoryItem(
                    commonName = "Nitrile O-Ring Seal 70A (50mm ID)",
                    oldCode = "ORG-5070",
                    newCode = "918273645019",
                    quantity = 8, // Low stock on purpose for testing alerts
                    location = "Aisle 02 - Rack A - Shelf 03 - Bin 09",
                    minThreshold = 25,
                    unit = "pcs",
                    category = "Seals & Gaskets",
                    photoUri = null
                ),
                InventoryItem(
                    commonName = "24V DC Industrial Optical Sensor NPN",
                    oldCode = "SNS-OPT24",
                    newCode = "638492017582",
                    quantity = 14,
                    location = "Aisle 02 - Rack C - Shelf 04 - Bin 02",
                    minThreshold = 10,
                    unit = "units",
                    category = "Electrical",
                    photoUri = null
                ),
                InventoryItem(
                    commonName = "1/2 inch Pneumatic Polyurethane Tubing (100ft)",
                    oldCode = "TUB-PU12",
                    newCode = "482019374652",
                    quantity = 5, // Low stock
                    location = "Aisle 03 - Rack A - Shelf 01 - Bulk 01",
                    minThreshold = 10,
                    unit = "spools",
                    category = "Pneumatics",
                    photoUri = null
                ),
                InventoryItem(
                    commonName = "High-Temperature Silicone Sealant 300ml",
                    oldCode = "SLT-HT300",
                    newCode = "371928405918",
                    quantity = 42,
                    location = "Aisle 03 - Rack B - Shelf 02 - Bin 11",
                    minThreshold = 15,
                    unit = "cartridges",
                    category = "Chemicals",
                    photoUri = null
                ),
                InventoryItem(
                    commonName = "Heavy Duty Corrugated Shipping Boxes (18x14x12)",
                    oldCode = "BOX-1814",
                    newCode = "592817403921",
                    quantity = 150,
                    location = "Aisle 04 - Bulk Staging - Zone C",
                    minThreshold = 40,
                    unit = "bundles",
                    category = "Packaging",
                    photoUri = null
                ),
                InventoryItem(
                    commonName = "3-Phase 15A DIN-Rail Circuit Breaker",
                    oldCode = "CB-3P15A",
                    newCode = "109283746501",
                    quantity = 19,
                    location = "Aisle 02 - Rack B - Shelf 02 - Bin 05",
                    minThreshold = 8,
                    unit = "units",
                    category = "Electrical",
                    photoUri = null
                )
            )

            val insertedIds = inventoryDao.insertAll(sampleItems)
            sampleItems.forEachIndexed { index, item ->
                val id = insertedIds.getOrNull(index) ?: (index + 1).toLong()
                logDao.insertLog(
                    StockLog(
                        inventoryItemId = id,
                        itemName = item.commonName,
                        barcode = item.newCode,
                        actionType = "INITIAL_ENTRY",
                        quantityChanged = item.quantity,
                        resultingQuantity = item.quantity,
                        notes = "Initial warehouse stock load"
                    )
                )
            }

            // Create 2 realistic starter orders for immediate order pulling testing
            val order1Id = orderDao.insertOrder(
                Order(
                    orderNumber = "ORD-8942",
                    customerName = "Apex Manufacturing Corp",
                    destination = "Dock 3 / Outbound Freight",
                    status = "PENDING"
                )
            )
            orderDao.insertOrderItems(
                listOf(
                    OrderItem(
                        orderId = order1Id,
                        inventoryItemId = insertedIds[0],
                        commonName = sampleItems[0].commonName,
                        barcode = sampleItems[0].newCode,
                        location = sampleItems[0].location,
                        quantityRequired = 10,
                        quantityPulled = 0
                    ),
                    OrderItem(
                        orderId = order1Id,
                        inventoryItemId = insertedIds[1],
                        commonName = sampleItems[1].commonName,
                        barcode = sampleItems[1].newCode,
                        location = sampleItems[1].location,
                        quantityRequired = 40,
                        quantityPulled = 0
                    ),
                    OrderItem(
                        orderId = order1Id,
                        inventoryItemId = insertedIds[3],
                        commonName = sampleItems[3].commonName,
                        barcode = sampleItems[3].newCode,
                        location = sampleItems[3].location,
                        quantityRequired = 2,
                        quantityPulled = 0
                    )
                )
            )

            val order2Id = orderDao.insertOrder(
                Order(
                    orderNumber = "ORD-9104",
                    customerName = "Cascade Robotics Ltd",
                    destination = "Express Air Dispatch",
                    status = "IN_PROGRESS"
                )
            )
            orderDao.insertOrderItems(
                listOf(
                    OrderItem(
                        orderId = order2Id,
                        inventoryItemId = insertedIds[3],
                        commonName = sampleItems[3].commonName,
                        barcode = sampleItems[3].newCode,
                        location = sampleItems[3].location,
                        quantityRequired = 4,
                        quantityPulled = 2
                    ),
                    OrderItem(
                        orderId = order2Id,
                        inventoryItemId = insertedIds[5],
                        commonName = sampleItems[5].commonName,
                        barcode = sampleItems[5].newCode,
                        location = sampleItems[5].location,
                        quantityRequired = 6,
                        quantityPulled = 0
                    )
                )
            )
        }
    }
}

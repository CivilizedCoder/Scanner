package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_logs")
data class StockLog(
    @PrimaryKey(autoGenerate = true)
    val logId: Long = 0,
    val inventoryItemId: Long,
    val itemName: String,
    val barcode: String,
    val actionType: String, // ORDER_PULL, RESTOCK, MANUAL_ADJUST, INITIAL_ENTRY
    val quantityChanged: Int,
    val resultingQuantity: Int,
    val referenceOrder: String? = null,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

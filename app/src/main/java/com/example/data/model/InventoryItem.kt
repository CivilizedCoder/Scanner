package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_items",
    indices = [
        Index(value = ["newCode"], unique = false),
        Index(value = ["oldCode"], unique = false),
        Index(value = ["location"])
    ]
)
data class InventoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val commonName: String,
    val oldCode: String,
    val newCode: String, // Barcode number
    val photoUri: String? = null,
    val quantity: Int,
    val location: String, // Warehouse location e.g. "Aisle 3 - Rack B - Shelf 2 - Bin 04"
    val minThreshold: Int = 10,
    val unit: String = "pcs",
    val category: String = "General",
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val isLowStock: Boolean
        get() = quantity <= minThreshold
}

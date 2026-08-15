package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey(autoGenerate = true)
    val orderId: Long = 0,
    val orderNumber: String,
    val customerName: String,
    val destination: String,
    val status: String = "PENDING", // PENDING, IN_PROGRESS, COMPLETED
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = Order::class,
            parentColumns = ["orderId"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("orderId"), Index("inventoryItemId"), Index("barcode")]
)
data class OrderItem(
    @PrimaryKey(autoGenerate = true)
    val orderItemId: Long = 0,
    val orderId: Long,
    val inventoryItemId: Long,
    val commonName: String,
    val barcode: String,
    val location: String,
    val quantityRequired: Int,
    val quantityPulled: Int = 0
) {
    val isFulfilled: Boolean
        get() = quantityPulled >= quantityRequired
}

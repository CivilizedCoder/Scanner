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
    val customerName: String, // Supplier / Vendor if orderType == "PURCHASE", Customer if "PULL"
    val destination: String,  // Receiving Dock / Bin if "PURCHASE", Delivery Dock / Customer destination if "PULL"
    val orderType: String = "PULL", // "PULL" (Outbound Pick list) or "PURCHASE" (Inbound Replenishment / Supplier Order)
    val status: String = "PENDING", // PENDING, IN_TRANSIT, IN_PROGRESS, COMPLETED, CANCELLED
    val expectedDeliveryDate: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    val isPurchaseOrder: Boolean
        get() = orderType == "PURCHASE"

    val isPullOrder: Boolean
        get() = orderType != "PURCHASE"

    val isInTransit: Boolean
        get() = isPurchaseOrder && (status == "PENDING" || status == "IN_TRANSIT" || status == "IN_PROGRESS")
}

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
    val quantityRequired: Int, // Ordered / Target quantity
    val quantityPulled: Int = 0 // Pulled for pull order, or Received for purchase order
) {
    val isFulfilled: Boolean
        get() = quantityPulled >= quantityRequired

    val remainingQuantity: Int
        get() = (quantityRequired - quantityPulled).coerceAtLeast(0)
}


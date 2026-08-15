package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.Order
import com.example.data.model.OrderItem
import kotlinx.coroutines.flow.Flow

data class OrderWithItems(
    @androidx.room.Embedded val order: Order,
    @androidx.room.Relation(
        parentColumn = "orderId",
        entityColumn = "orderId"
    )
    val items: List<OrderItem>
)

@Dao
interface OrderDao {
    @Transaction
    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun getAllOrders(): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE orderId = :orderId LIMIT 1")
    fun getOrderWithItemsFlow(orderId: Long): Flow<OrderWithItems?>

    @Transaction
    @Query("SELECT * FROM orders WHERE orderId = :orderId LIMIT 1")
    suspend fun getOrderWithItems(orderId: Long): OrderWithItems?

    @Query("SELECT * FROM orders WHERE status != 'COMPLETED' ORDER BY createdAt ASC")
    fun getPendingOrders(): Flow<List<Order>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: Order): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItem>)

    @Update
    suspend fun updateOrder(order: Order)

    @Update
    suspend fun updateOrderItem(item: OrderItem)

    @Query("UPDATE order_items SET quantityPulled = :pulled WHERE orderItemId = :orderItemId")
    suspend fun updatePulledQuantity(orderItemId: Long, pulled: Int)

    @Delete
    suspend fun deleteOrder(order: Order)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteOrderItems(orderId: Long)
}

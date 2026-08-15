package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.InventoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items ORDER BY commonName ASC")
    fun getAllItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE quantity <= minThreshold ORDER BY quantity ASC")
    fun getLowStockItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    fun getItemByIdFlow(id: Long): Flow<InventoryItem?>

    @Query("SELECT * FROM inventory_items WHERE newCode = :barcode OR oldCode = :barcode LIMIT 1")
    suspend fun getItemByCode(barcode: String): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE newCode = :barcode LIMIT 1")
    suspend fun getItemByNewCode(barcode: String): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE commonName LIKE '%' || :query || '%' OR oldCode LIKE '%' || :query || '%' OR newCode LIKE '%' || :query || '%' OR location LIKE '%' || :query || '%'")
    fun searchItems(query: String): Flow<List<InventoryItem>>

    @Query("SELECT DISTINCT location FROM inventory_items ORDER BY location ASC")
    fun getAllLocations(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<InventoryItem>): List<Long>

    @Update
    suspend fun updateItem(item: InventoryItem)

    @Query("UPDATE inventory_items SET quantity = :newQuantity, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateQuantity(id: Long, newQuantity: Int, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteItem(item: InventoryItem)

    @Query("SELECT COUNT(*) FROM inventory_items")
    suspend fun getItemCount(): Int

    @Query("SELECT COUNT(*) FROM inventory_items WHERE quantity <= minThreshold")
    fun getLowStockCountFlow(): Flow<Int>
}

package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.StockLog
import kotlinx.coroutines.flow.Flow

@Dao
interface StockLogDao {
    @Query("SELECT * FROM stock_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogs(): Flow<List<StockLog>>

    @Query("SELECT * FROM stock_logs WHERE inventoryItemId = :itemId ORDER BY timestamp DESC")
    fun getLogsForItem(itemId: Long): Flow<List<StockLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: StockLog): Long
}

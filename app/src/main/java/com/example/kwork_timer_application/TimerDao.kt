package com.example.kwork_timer_application
import androidx.room.*

@Dao
interface TimerDao {
    @Insert
    suspend fun insert(timer: TimerItemEntity)

    @Delete
    suspend fun delete(timer: TimerItemEntity)

    @Query("SELECT * FROM timer_table")
    suspend fun getAllTimers(): List<TimerItemEntity>
}

package com.example.kwork_timer_application
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TimerItemEntity::class], version = 1)
abstract class TimerDatabase : RoomDatabase() {
    abstract fun timerDao(): TimerDao
}

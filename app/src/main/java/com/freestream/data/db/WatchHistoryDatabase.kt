package com.freestream.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WatchHistoryEntity::class], version = 1, exportSchema = false)
abstract class WatchHistoryDatabase : RoomDatabase() {

    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: WatchHistoryDatabase? = null

        fun getInstance(context: Context): WatchHistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WatchHistoryDatabase::class.java,
                    "watch_history.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

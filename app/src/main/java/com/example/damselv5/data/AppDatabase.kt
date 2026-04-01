package com.example.damselv5.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ContactEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var I: AppDatabase? = null

        fun getDatabase(c: Context): AppDatabase {
            return I ?: synchronized(this) {
                val ins = Room.databaseBuilder(
                    c.applicationContext,
                    AppDatabase::class.java,
                    "damsel_database"
                )
                .fallbackToDestructiveMigration() 
                .build()
                I = ins
                ins
            }
        }
    }
}
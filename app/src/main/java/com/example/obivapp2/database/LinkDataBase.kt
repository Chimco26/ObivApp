package com.example.obivapp2.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LinkData::class], version = 1, exportSchema = false)
abstract class LinkDatabase : RoomDatabase() {
    abstract fun linkDao(): LinkDao

    companion object {
        @Volatile
        private var INSTANCE: LinkDatabase? = null

        fun getDatabase(context: Context): LinkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LinkDatabase::class.java,
                    "link_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

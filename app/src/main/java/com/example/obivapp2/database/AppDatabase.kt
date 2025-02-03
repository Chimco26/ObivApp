package com.example.obivapp2.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.obivapp2.viewModel.LinkData

@Database(entities = [LinkData::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun linkDao(): LinkDao
}

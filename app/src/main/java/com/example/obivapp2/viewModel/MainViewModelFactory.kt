package com.example.obivapp2.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.obivapp2.database.LinkDao

class MainViewModelFactory(private val linkDao: LinkDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(linkDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

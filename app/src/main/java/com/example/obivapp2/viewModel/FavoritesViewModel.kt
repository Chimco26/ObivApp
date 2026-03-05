package com.example.obivapp2.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.obivapp2.data.AppDatabase
import com.example.obivapp2.data.FavoriteMovie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {
    private val favoriteDao = AppDatabase.getDatabase(application).favoriteDao()
    val allFavorites: Flow<List<FavoriteMovie>> = favoriteDao.getAllFavorites()

    fun toggleFavorite(favorite: FavoriteMovie, isFavorite: Boolean) {
        viewModelScope.launch {
            if (isFavorite) {
                favoriteDao.delete(favorite)
            } else {
                favoriteDao.insert(favorite)
            }
        }
    }

    fun isFavorite(url: String): Flow<Boolean> {
        return favoriteDao.isFavorite(url)
    }
}

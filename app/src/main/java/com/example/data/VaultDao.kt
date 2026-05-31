package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items ORDER BY title ASC")
    fun getAllItems(): Flow<List<VaultItem>>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getItemById(id: Int): VaultItem?

    @Query("SELECT * FROM vault_items WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteItems(): Flow<List<VaultItem>>

    @Query("SELECT * FROM vault_items WHERE category = :category ORDER BY title ASC")
    fun getItemsByCategory(category: String): Flow<List<VaultItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: VaultItem)

    @Delete
    suspend fun deleteItem(item: VaultItem)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteItemById(id: Int)

    @Query("DELETE FROM vault_items")
    suspend fun deleteAllItems()
}

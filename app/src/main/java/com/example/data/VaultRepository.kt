package com.example.data

import kotlinx.coroutines.flow.Flow

class VaultRepository(private val vaultDao: VaultDao) {
    val allItems: Flow<List<VaultItem>> = vaultDao.getAllItems()
    val favorites: Flow<List<VaultItem>> = vaultDao.getFavoriteItems()

    suspend fun insert(item: VaultItem) {
        vaultDao.insertItem(item)
    }

    suspend fun deleteById(id: Int) {
        vaultDao.deleteItemById(id)
    }

    suspend fun delete(item: VaultItem) {
        vaultDao.deleteItem(item)
    }

    suspend fun getItemById(id: Int): VaultItem? {
        return vaultDao.getItemById(id)
    }

    suspend fun clear() {
        vaultDao.deleteAllItems()
    }
}

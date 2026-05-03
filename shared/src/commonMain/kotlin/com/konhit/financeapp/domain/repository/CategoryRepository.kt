package com.konhit.financeapp.domain.repository

import com.konhit.financeapp.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun getAll(): List<Category>
    suspend fun insert(category: Category)
    suspend fun update(category: Category)
    suspend fun delete(id: Long)
    suspend fun hasTransactions(id: Long): Boolean
}

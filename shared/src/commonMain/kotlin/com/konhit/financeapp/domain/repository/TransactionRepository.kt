package com.konhit.financeapp.domain.repository

import com.konhit.financeapp.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observeByAccount(accountId: Long): Flow<List<Transaction>>
    fun observeAnyChange(): Flow<Unit>
    suspend fun getByAccount(accountId: Long): List<Transaction>
    suspend fun getById(transId: Long): Transaction?
    suspend fun insert(transaction: Transaction)
    suspend fun update(transaction: Transaction)
    suspend fun delete(transId: Long)
    suspend fun search(query: String): List<Transaction>
}

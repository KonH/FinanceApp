package com.konhit.financeapp.domain.repository

import com.konhit.financeapp.domain.model.Account
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeAll(): Flow<List<Account>>
    suspend fun getAll(): List<Account>
    suspend fun getById(id: Long): Account?
    suspend fun getBalance(id: Long): Double
    suspend fun insert(account: Account)
    suspend fun update(account: Account)
    suspend fun delete(id: Long)
}

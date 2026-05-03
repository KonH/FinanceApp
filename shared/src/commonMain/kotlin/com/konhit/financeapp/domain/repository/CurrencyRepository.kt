package com.konhit.financeapp.domain.repository

import com.konhit.financeapp.domain.model.Currency
import kotlinx.coroutines.flow.Flow

interface CurrencyRepository {
    fun observeAll(): Flow<List<Currency>>
    suspend fun getAll(): List<Currency>
    suspend fun getById(id: Long): Currency?
}

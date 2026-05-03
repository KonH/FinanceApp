package com.konhit.financeapp.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.konhit.financeapp.db.DatabaseHolder
import com.konhit.financeapp.domain.model.Currency
import com.konhit.financeapp.domain.repository.CurrencyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CurrencyRepositoryImpl(private val holder: DatabaseHolder) : CurrencyRepository {

    override fun observeAll(): Flow<List<Currency>> =
        holder.requireDb().currencyQueries
            .selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { row -> mapRow(row.CURRENCYID, row.CURRENCYNAME, row.PFX_SYMBOL, row.SFX_SYMBOL, row.DECIMAL_POINT, row.GROUP_SEPARATOR, row.SCALE, row.CURRENCY_SYMBOL) } }

    override suspend fun getAll(): List<Currency> = withContext(Dispatchers.IO) {
        holder.requireDb().currencyQueries.selectAll().executeAsList().map { row ->
            mapRow(row.CURRENCYID, row.CURRENCYNAME, row.PFX_SYMBOL, row.SFX_SYMBOL, row.DECIMAL_POINT, row.GROUP_SEPARATOR, row.SCALE, row.CURRENCY_SYMBOL)
        }
    }

    override suspend fun getById(id: Long): Currency? = withContext(Dispatchers.IO) {
        holder.requireDb().currencyQueries.selectById(id).executeAsOneOrNull()?.let { row ->
            Currency(
                id             = row.CURRENCYID,
                name           = row.CURRENCYNAME,
                pfxSymbol      = row.PFX_SYMBOL,
                sfxSymbol      = row.SFX_SYMBOL,
                decimalPoint   = row.DECIMAL_POINT,
                groupSeparator = row.GROUP_SEPARATOR,
                scale          = row.SCALE,
                currencySymbol = row.CURRENCY_SYMBOL
            )
        }
    }

    private fun mapRow(
        id: Long, name: String, pfxSymbol: String?, sfxSymbol: String?,
        decimalPoint: String?, groupSeparator: String?, scale: Long?, currencySymbol: String?
    ) = Currency(
        id             = id,
        name           = name,
        pfxSymbol      = pfxSymbol,
        sfxSymbol      = sfxSymbol,
        decimalPoint   = decimalPoint,
        groupSeparator = groupSeparator,
        scale          = scale,
        currencySymbol = currencySymbol
    )
}

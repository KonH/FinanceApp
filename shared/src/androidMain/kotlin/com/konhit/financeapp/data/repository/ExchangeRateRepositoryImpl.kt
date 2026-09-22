package com.konhit.financeapp.data.repository

import com.konhit.financeapp.domain.rates.ExchangeRates
import com.konhit.financeapp.domain.rates.RateFetch
import com.konhit.financeapp.domain.rates.RateRow
import com.konhit.financeapp.domain.rates.RateTable
import com.konhit.financeapp.domain.repository.ExchangeRateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Rates come from Frankfurter (https://frankfurter.dev, no API key; central-bank
 * reference rates for ~160 currencies back to the 1990s). The cache is a JSON
 * file in the app's files dir, in the same shape the desktop app uses.
 */
class ExchangeRateRepositoryImpl(private val cacheFile: File) : ExchangeRateRepository {

    private val mutex = Mutex()
    private var table: RateTable? = null
    private var supported: Set<String>? = null

    override suspend fun cached(): RateTable = mutex.withLock { loadLocked() }

    override suspend fun supportedCodes(): Set<String> {
        supported?.let { return it }
        val body = withContext(Dispatchers.IO) { get("$API/currencies") }
        val array = JSONArray(body)
        val codes = (0 until array.length()).map { array.getJSONObject(it).getString("iso_code") }.toSet()
        supported = codes
        return codes
    }

    override suspend fun fetch(fetch: RateFetch): RateTable {
        val url = "$API/rates?from=${fetch.from}&to=${fetch.to}" +
            "&base=${ExchangeRates.PIVOT}&quotes=${fetch.codes.joinToString(",") { URLEncoder.encode(it, "UTF-8") }}"
        val body = withContext(Dispatchers.IO) { get(url) }
        val array = JSONArray(body)
        val rows = (0 until array.length()).map {
            val row = array.getJSONObject(it)
            RateRow(row.getString("date"), row.getString("quote"), row.getDouble("rate"))
        }
        return mutex.withLock {
            val merged = ExchangeRates.merge(loadLocked(), fetch, rows)
            table = merged
            withContext(Dispatchers.IO) { save(merged) }
            merged
        }
    }

    private suspend fun loadLocked(): RateTable {
        table?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            runCatching { if (cacheFile.exists()) parse(JSONObject(cacheFile.readText())) else null }.getOrNull()
        } ?: RateTable()
        table = loaded
        return loaded
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("Exchange rate request failed: HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun save(table: RateTable) {
        val json = JSONObject()
            .put("version", 1)
            .put("rates", nested(table.rates))
            .put("coverage", nested(table.coverage))
        val temp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
        temp.writeText(json.toString())
        if (!temp.renameTo(cacheFile)) {
            cacheFile.delete()
            temp.renameTo(cacheFile)
        }
    }

    private fun nested(map: Map<String, Map<String, Any>>): JSONObject = JSONObject().apply {
        for ((key, inner) in map) put(key, JSONObject(inner))
    }

    private fun parse(json: JSONObject): RateTable {
        fun <T> section(name: String, read: (JSONObject, String) -> T): Map<String, Map<String, T>> {
            val outer = json.optJSONObject(name) ?: return emptyMap()
            return outer.keys().asSequence().associateWith { key ->
                val inner = outer.getJSONObject(key)
                inner.keys().asSequence().associateWith { read(inner, it) }
            }
        }
        return RateTable(
            rates = section("rates") { obj, key -> obj.getDouble(key) },
            coverage = section("coverage") { obj, key -> obj.getString(key) }
        )
    }

    private companion object {
        const val API = "https://api.frankfurter.dev/v2"
        const val TIMEOUT_MS = 15_000
    }
}

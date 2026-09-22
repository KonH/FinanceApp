package com.konhit.financeapp.domain.repository

import com.konhit.financeapp.domain.rates.RateFetch
import com.konhit.financeapp.domain.rates.RateTable

/** Historical exchange rates from the internet, cached on the device across sessions. */
interface ExchangeRateRepository {
    /** Everything downloaded so far. */
    suspend fun cached(): RateTable

    /** ISO codes the rate provider knows. */
    suspend fun supportedCodes(): Set<String>

    /** Downloads [fetch], adds it to the cache and returns the updated table. */
    suspend fun fetch(fetch: RateFetch): RateTable
}

package com.konhit.financeapp.domain.usecase

import com.konhit.financeapp.domain.rates.ExchangeRates
import com.konhit.financeapp.domain.rates.RateNeed
import com.konhit.financeapp.domain.rates.RateTable
import com.konhit.financeapp.domain.repository.ExchangeRateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** [table] holds every cached rate; [failed] is true when any download did not succeed. */
data class EnsuredRates(val table: RateTable, val failed: Boolean)

/**
 * Downloads whatever [needs] the rate cache lacks — one request per month, a
 * few in parallel. [onProgress] gets 0..100 and is only called when something
 * has to be downloaded; it runs on the caller's dispatcher.
 */
class EnsureRatesUseCase(private val rateRepo: ExchangeRateRepository) {

    suspend operator fun invoke(
        needs: Collection<RateNeed>,
        today: String,
        onProgress: (Int) -> Unit
    ): EnsuredRates {
        var fetches = ExchangeRates.plan(rateRepo.cached(), needs, today)
        if (fetches.isEmpty()) return EnsuredRates(rateRepo.cached(), failed = false)

        onProgress(0)
        val supported = try {
            rateRepo.supportedCodes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (supported != null) fetches = ExchangeRates.plan(rateRepo.cached(), needs, today, supported)

        val permits = Semaphore(PARALLEL_FETCHES)
        var done = 0
        var failed = false
        coroutineScope {
            for (fetch in fetches) {
                launch {
                    permits.withPermit {
                        try {
                            rateRepo.fetch(fetch)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failed = true
                        }
                    }
                    done++
                    onProgress(done * 100 / fetches.size)
                }
            }
        }
        return EnsuredRates(rateRepo.cached(), failed)
    }

    private companion object {
        const val PARALLEL_FETCHES = 4
    }
}

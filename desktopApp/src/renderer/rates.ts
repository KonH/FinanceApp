import { api } from './api';
import { mergeRates, planFetches, type RateNeed, type RateTable } from '../shared/rates';

const PARALLEL_FETCHES = 4;

/**
 * Port of `EnsureRatesUseCase`: downloads whatever `needs` the rate cache lacks —
 * one request per month, a few in parallel. `onProgress` gets 0..100 and is only
 * called when something has to be downloaded. Stops early once `isCancelled()`.
 */
export async function ensureRates(
  needs: RateNeed[],
  today: string,
  onProgress: (percent: number) => void,
  isCancelled: () => boolean
): Promise<{ table: RateTable; failed: boolean }> {
  let table = await api.cachedRates();
  let fetches = planFetches(table, needs, today);
  if (fetches.length === 0 || isCancelled()) return { table, failed: false };

  onProgress(0);
  const supported = await api.supportedRateCodes();
  if (supported.ok && supported.value) fetches = planFetches(table, needs, today, new Set(supported.value));

  let failed = false;
  let next = 0;
  let done = 0;
  const worker = async (): Promise<void> => {
    while (!isCancelled() && next < fetches.length) {
      const request = fetches[next++];
      const result = await api.fetchRates(request);
      if (result.ok && result.value) table = mergeRates(table, request, result.value);
      else failed = true;
      done++;
      if (!isCancelled()) onProgress(Math.floor((done * 100) / fetches.length));
    }
  };
  await Promise.all(Array.from({ length: PARALLEL_FETCHES }, worker));
  return { table, failed };
}

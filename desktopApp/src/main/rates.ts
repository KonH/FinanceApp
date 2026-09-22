import fs from 'node:fs';
import path from 'node:path';
import { app } from 'electron';
import { PIVOT, emptyRateTable, mergeRates, type RateFetch, type RateRow, type RateTable } from '../shared/rates';

/**
 * Desktop counterpart of `ExchangeRateRepositoryImpl`. Rates come from
 * Frankfurter (https://frankfurter.dev, no API key; central-bank reference rates
 * for ~160 currencies back to the 1990s). The cache is a JSON file in the
 * Electron userData directory, in the same shape the Android app uses.
 */

const API = 'https://api.frankfurter.dev/v2';
const TIMEOUT_MS = 15_000;

let table: RateTable | null = null;
let supported: string[] | null = null;

function cacheFile(): string {
  return path.join(app.getPath('userData'), 'exchange-rates.json');
}

async function getJson(url: string): Promise<unknown> {
  const response = await fetch(url, {
    headers: { Accept: 'application/json' },
    signal: AbortSignal.timeout(TIMEOUT_MS)
  });
  if (!response.ok) throw new Error(`Exchange rate request failed: HTTP ${response.status}`);
  return response.json();
}

/** Everything downloaded so far. */
export function cachedRates(): RateTable {
  if (table) return table;
  try {
    const parsed = JSON.parse(fs.readFileSync(cacheFile(), 'utf8')) as Partial<RateTable>;
    table = { rates: parsed.rates ?? {}, coverage: parsed.coverage ?? {} };
  } catch {
    table = emptyRateTable();
  }
  return table;
}

/** ISO codes the rate provider knows. */
export async function supportedCodes(): Promise<string[]> {
  if (supported) return supported;
  const list = (await getJson(`${API}/currencies`)) as Array<{ iso_code: string }>;
  supported = list.map((entry) => entry.iso_code);
  return supported;
}

/** Downloads `request`, adds it to the cache and returns the rows it brought. */
export async function fetchRates(request: RateFetch): Promise<RateRow[]> {
  const url =
    `${API}/rates?from=${request.from}&to=${request.to}` +
    `&base=${PIVOT}&quotes=${request.codes.map(encodeURIComponent).join(',')}`;
  const body = (await getJson(url)) as Array<{ date: string; quote: string; rate: number }>;
  const rows = body.map((row) => ({ date: row.date, code: row.quote, rate: row.rate }));
  table = mergeRates(cachedRates(), request, rows);
  const file = cacheFile();
  const temp = `${file}.tmp`;
  fs.writeFileSync(temp, JSON.stringify({ version: 1, ...table }));
  fs.renameSync(temp, file);
  return rows;
}

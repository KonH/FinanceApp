import fs from 'node:fs';
import path from 'node:path';
import initSqlJs, { type Database, type SqlJsStatic } from 'sql.js';
import { CREATE_SCHEMA_SQL, SEED_CURRENCIES } from './schema';

export type SqlValue = number | string | Uint8Array | null;
export type SqlParams = SqlValue[];

let sqlJs: SqlJsStatic | null = null;

async function loadSqlJs(): Promise<SqlJsStatic> {
  if (sqlJs) return sqlJs;
  // Read the wasm ourselves so the module works both from node_modules and from
  // inside a packaged app.asar, with no runtime path guessing by emscripten.
  const wasmPath = path.join(path.dirname(require.resolve('sql.js')), 'sql-wasm.wasm');
  const file = fs.readFileSync(wasmPath);
  const wasmBinary = file.buffer.slice(file.byteOffset, file.byteOffset + file.byteLength) as ArrayBuffer;
  sqlJs = await initSqlJs({ wasmBinary });
  return sqlJs;
}

/**
 * An open MMEX `.mmb` file.
 *
 * The whole file is held in memory by SQLite (compiled to WebAssembly) and
 * written back with `db.export()`, which returns the SQLite file image itself.
 * That is why every table, index, trigger and view of the original file — plus
 * `user_version` and `INFOTABLE_V1` — survives a write untouched: nothing here
 * ever runs DDL against an existing file.
 */
export class MmexDatabase {
  private constructor(
    private readonly db: Database,
    public readonly filePath: string,
    public defaultPayeeId: number
  ) {}

  static async openExisting(filePath: string): Promise<MmexDatabase> {
    if (!fs.existsSync(filePath)) throw new Error(`File does not exist: ${filePath}`);
    const SQL = await loadSqlJs();
    const db = new SQL.Database(fs.readFileSync(filePath));
    const holder = new MmexDatabase(db, filePath, -1);
    holder.assertLooksLikeMmex();
    holder.defaultPayeeId = holder.readDefaultPayeeId();
    return holder;
  }

  static async createNew(filePath: string): Promise<MmexDatabase> {
    if (fs.existsSync(filePath)) throw new Error(`File already exists: ${filePath}`);
    fs.mkdirSync(path.dirname(filePath), { recursive: true });
    const SQL = await loadSqlJs();
    const db = new SQL.Database();
    db.run(CREATE_SCHEMA_SQL);

    const created = new Date().toISOString().slice(0, 10);
    const info: Array<[string, string]> = [
      ['DATAVERSION', '3'],
      ['BASECURRENCYID', '1'],
      ['MMEXVERSION', '1.7.0'],
      ['CREATEDATE', created],
      ['DATEFORMAT', '%d/%m/%Y']
    ];
    for (const [name, value] of info) {
      db.run('INSERT INTO INFOTABLE_V1 (INFONAME, INFOVALUE) VALUES (?, ?)', [name, value]);
    }
    for (const c of SEED_CURRENCIES) {
      db.run(
        'INSERT INTO CURRENCYFORMATS_V1 (CURRENCYID, CURRENCYNAME, CURRENCY_SYMBOL, PFX_SYMBOL, SFX_SYMBOL, BASECONVRATE, DECIMAL_POINT, GROUP_SEPARATOR, SCALE) ' +
          "VALUES (?, ?, ?, ?, ?, 1.0, '.', ' ', 100)",
        [c.id, c.name, c.symbol, c.pfx, c.sfx]
      );
    }

    const payeeId = Date.now() * 1000;
    db.run('INSERT INTO PAYEE_V1 (PAYEEID, PAYEENAME) VALUES (?, ?)', [payeeId, 'Default']);

    const holder = new MmexDatabase(db, filePath, payeeId);
    holder.save();
    return holder;
  }

  /** Rejects files that are not MoneyManagerEx databases before anything is written. */
  private assertLooksLikeMmex(): void {
    const tables = this.all<{ name: string }>(
      "SELECT name FROM sqlite_master WHERE type = 'table'"
    ).map((r) => r.name.toUpperCase());
    const required = ['ACCOUNTLIST_V1', 'CHECKINGACCOUNT_V1', 'CATEGORY_V1', 'CURRENCYFORMATS_V1'];
    const missing = required.filter((t) => !tables.includes(t));
    if (missing.length > 0) {
      throw new Error(`Not a MoneyManagerEx file — missing table(s): ${missing.join(', ')}`);
    }
  }

  private readDefaultPayeeId(): number {
    const row = this.one<{ PAYEEID: number }>('SELECT PAYEEID FROM PAYEE_V1 LIMIT 1');
    return row ? row.PAYEEID : -1;
  }

  all<T = Record<string, SqlValue>>(sql: string, params: SqlParams = []): T[] {
    const stmt = this.db.prepare(sql);
    try {
      stmt.bind(params);
      const rows: T[] = [];
      while (stmt.step()) rows.push(stmt.getAsObject() as T);
      return rows;
    } finally {
      stmt.free();
    }
  }

  one<T = Record<string, SqlValue>>(sql: string, params: SqlParams = []): T | null {
    const rows = this.all<T>(sql, params);
    return rows.length > 0 ? rows[0] : null;
  }

  scalar(sql: string, params: SqlParams = []): SqlValue {
    const row = this.one<Record<string, SqlValue>>(sql, params);
    if (!row) return null;
    const values = Object.values(row);
    return values.length > 0 ? values[0] : null;
  }

  run(sql: string, params: SqlParams = []): void {
    this.db.run(sql, params);
  }

  /** Serialises the in-memory image and replaces the file atomically. */
  save(): void {
    const bytes = this.db.export();
    const tmp = `${this.filePath}.tmp-${process.pid}`;
    fs.writeFileSync(tmp, Buffer.from(bytes));
    fs.renameSync(tmp, this.filePath);
  }

  close(): void {
    this.db.close();
  }
}

/** Singleton holder — the desktop counterpart of `DatabaseHolder`. */
class DatabaseHolder {
  private current: MmexDatabase | null = null;

  get db(): MmexDatabase | null {
    return this.current;
  }

  require(): MmexDatabase {
    if (!this.current) throw new Error('No database open');
    return this.current;
  }

  get isOpen(): boolean {
    return this.current !== null;
  }

  get filePath(): string | null {
    return this.current?.filePath ?? null;
  }

  get defaultPayeeId(): number {
    return this.current?.defaultPayeeId ?? -1;
  }

  open(db: MmexDatabase): void {
    if (this.current && this.current !== db) this.current.close();
    this.current = db;
  }

  close(): void {
    this.current?.close();
    this.current = null;
  }
}

export const dbHolder = new DatabaseHolder();

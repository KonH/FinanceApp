import { dbHolder } from './db/database';
import { computeBalance } from '../shared/balance';
import type {
  Account,
  Category,
  Currency,
  ScheduledTransaction,
  Transaction,
  TransactionType
} from '../shared/types';

/**
 * Data access for the open .mmb file. Every statement here is the query of the
 * matching SQLDelight `.sq` file, so the desktop app touches MMEX rows exactly
 * the way the Android app does.
 */

const num = (v: unknown): number => (typeof v === 'number' ? v : Number(v ?? 0));
const numOrNull = (v: unknown): number | null => (v === null || v === undefined ? null : Number(v));
const str = (v: unknown): string => (v === null || v === undefined ? '' : String(v));
const strOrNull = (v: unknown): string | null => (v === null || v === undefined ? null : String(v));

// ---------------------------------------------------------------- accounts

const ACCOUNT_SELECT = `
SELECT ACCOUNTID, ACCOUNTNAME, ACCOUNTTYPE, INITIALBAL, CURRENCYID, STATUS
FROM ACCOUNTLIST_V1
WHERE STATUS != 'Closed'
  AND ACCOUNTTYPE IN ('Cash', 'Savings', 'Investment')
ORDER BY ACCOUNTNAME`;

const BALANCE_COMPONENTS = `
SELECT
    a.INITIALBAL AS initialBal,
    COALESCE((
        SELECT SUM(TRANSAMOUNT) FROM CHECKINGACCOUNT_V1
        WHERE ACCOUNTID = ?1 AND TRANSCODE = 'Deposit' AND DELETEDTIME IS NULL
    ), 0) AS totalDeposits,
    COALESCE((
        SELECT SUM(TRANSAMOUNT) FROM CHECKINGACCOUNT_V1
        WHERE ACCOUNTID = ?1 AND TRANSCODE = 'Withdrawal' AND DELETEDTIME IS NULL
    ), 0) AS totalWithdrawals,
    COALESCE((
        SELECT SUM(TOTRANSAMOUNT) FROM CHECKINGACCOUNT_V1
        WHERE TOACCOUNTID = ?1 AND TRANSCODE = 'Transfer' AND DELETEDTIME IS NULL
    ), 0) AS transfersIn,
    COALESCE((
        SELECT SUM(TRANSAMOUNT) FROM CHECKINGACCOUNT_V1
        WHERE ACCOUNTID = ?1 AND TRANSCODE = 'Transfer' AND DELETEDTIME IS NULL
    ), 0) AS transfersOut
FROM ACCOUNTLIST_V1 a
WHERE a.ACCOUNTID = ?1`;

const BALANCE_COMPONENTS_AT_DATE = `
SELECT
    a.INITIALBAL AS initialBal,
    COALESCE((
        SELECT SUM(TRANSAMOUNT) FROM CHECKINGACCOUNT_V1
        WHERE ACCOUNTID = ?1 AND TRANSCODE = 'Deposit'
          AND DELETEDTIME IS NULL AND substr(TRANSDATE, 1, 10) <= ?2
    ), 0) AS totalDeposits,
    COALESCE((
        SELECT SUM(TRANSAMOUNT) FROM CHECKINGACCOUNT_V1
        WHERE ACCOUNTID = ?1 AND TRANSCODE = 'Withdrawal'
          AND DELETEDTIME IS NULL AND substr(TRANSDATE, 1, 10) <= ?2
    ), 0) AS totalWithdrawals,
    COALESCE((
        SELECT SUM(TOTRANSAMOUNT) FROM CHECKINGACCOUNT_V1
        WHERE TOACCOUNTID = ?1 AND TRANSCODE = 'Transfer'
          AND DELETEDTIME IS NULL AND substr(TRANSDATE, 1, 10) <= ?2
    ), 0) AS transfersIn,
    COALESCE((
        SELECT SUM(TRANSAMOUNT) FROM CHECKINGACCOUNT_V1
        WHERE ACCOUNTID = ?1 AND TRANSCODE = 'Transfer'
          AND DELETEDTIME IS NULL AND substr(TRANSDATE, 1, 10) <= ?2
    ), 0) AS transfersOut
FROM ACCOUNTLIST_V1 a
WHERE a.ACCOUNTID = ?1`;

interface BalanceRow {
  initialBal: number;
  totalDeposits: number;
  totalWithdrawals: number;
  transfersIn: number;
  transfersOut: number;
}

export const accountRepo = {
  getAll(): Account[] {
    const db = dbHolder.require();
    return db.all<Record<string, unknown>>(ACCOUNT_SELECT).map((row) => ({
      id: num(row.ACCOUNTID),
      name: str(row.ACCOUNTNAME),
      type: str(row.ACCOUNTTYPE),
      initialBal: num(row.INITIALBAL),
      currencyId: num(row.CURRENCYID),
      status: str(row.STATUS),
      balance: accountRepo.getBalance(num(row.ACCOUNTID))
    }));
  },

  getById(id: number): Account | null {
    const db = dbHolder.require();
    const row = db.one<Record<string, unknown>>('SELECT * FROM ACCOUNTLIST_V1 WHERE ACCOUNTID = ?', [id]);
    if (!row) return null;
    return {
      id: num(row.ACCOUNTID),
      name: str(row.ACCOUNTNAME),
      type: str(row.ACCOUNTTYPE),
      initialBal: num(row.INITIALBAL),
      currencyId: num(row.CURRENCYID),
      status: str(row.STATUS),
      balance: accountRepo.getBalance(id)
    };
  },

  getBalance(id: number): number {
    const db = dbHolder.require();
    const row = db.one<BalanceRow>(BALANCE_COMPONENTS, [id]);
    if (!row) return 0;
    return computeBalance(row);
  },

  getBalanceAtDate(id: number, date: string): number {
    const db = dbHolder.require();
    const row = db.one<BalanceRow>(BALANCE_COMPONENTS_AT_DATE, [id, date]);
    if (!row) return 0;
    return computeBalance(row);
  },

  insert(account: Account): void {
    dbHolder.require().run(
      `INSERT INTO ACCOUNTLIST_V1 (
          ACCOUNTID, ACCOUNTNAME, ACCOUNTTYPE, INITIALBAL, CURRENCYID,
          STATUS, NOTES, HELDAT, WEBSITE, CONTACTINFO, ACCESSINFO, FAVORITEACCT
      ) VALUES (?, ?, ?, ?, ?, 'Open', '', '', '', '', '', 'FALSE')`,
      [account.id, account.name, account.type, account.initialBal, account.currencyId]
    );
  },

  update(account: Account): void {
    dbHolder.require().run(
      `UPDATE ACCOUNTLIST_V1
       SET ACCOUNTNAME = ?, ACCOUNTTYPE = ?, INITIALBAL = ?, CURRENCYID = ?
       WHERE ACCOUNTID = ?`,
      [account.name, account.type, account.initialBal, account.currencyId, account.id]
    );
  },

  delete(id: number): void {
    dbHolder.require().run('DELETE FROM ACCOUNTLIST_V1 WHERE ACCOUNTID = ?', [id]);
  }
};

// -------------------------------------------------------------- categories

export const categoryRepo = {
  getAll(): Category[] {
    return dbHolder
      .require()
      .all<Record<string, unknown>>(
        'SELECT CATEGID, CATEGNAME, PARENTID FROM CATEGORY_V1 ORDER BY PARENTID, CATEGNAME'
      )
      .map((row) => ({
        id: num(row.CATEGID),
        name: str(row.CATEGNAME),
        parentId: num(row.PARENTID)
      }));
  },

  insert(category: Category): void {
    dbHolder
      .require()
      .run('INSERT INTO CATEGORY_V1 (CATEGID, CATEGNAME, PARENTID) VALUES (?, ?, ?)', [
        category.id,
        category.name,
        category.parentId
      ]);
  },

  update(category: Category): void {
    dbHolder
      .require()
      .run('UPDATE CATEGORY_V1 SET CATEGNAME = ?, PARENTID = ? WHERE CATEGID = ?', [
        category.name,
        category.parentId,
        category.id
      ]);
  },

  delete(id: number): void {
    dbHolder.require().run('DELETE FROM CATEGORY_V1 WHERE CATEGID = ?', [id]);
  },

  hasTransactions(id: number): boolean {
    const count = dbHolder
      .require()
      .scalar('SELECT COUNT(*) FROM CHECKINGACCOUNT_V1 WHERE CATEGID = ? AND DELETEDTIME IS NULL', [id]);
    return num(count) > 0;
  },

  hasChildren(id: number): boolean {
    const count = dbHolder.require().scalar('SELECT COUNT(*) FROM CATEGORY_V1 WHERE PARENTID = ?', [id]);
    return num(count) > 0;
  }
};

// -------------------------------------------------------------- currencies

const CURRENCY_SELECT = `
SELECT CURRENCYID, CURRENCYNAME, PFX_SYMBOL, SFX_SYMBOL, DECIMAL_POINT,
       GROUP_SEPARATOR, SCALE, CURRENCY_SYMBOL
FROM CURRENCYFORMATS_V1
ORDER BY CURRENCYNAME`;

function mapCurrency(row: Record<string, unknown>): Currency {
  return {
    id: num(row.CURRENCYID),
    name: str(row.CURRENCYNAME),
    pfxSymbol: strOrNull(row.PFX_SYMBOL),
    sfxSymbol: strOrNull(row.SFX_SYMBOL),
    decimalPoint: strOrNull(row.DECIMAL_POINT),
    groupSeparator: strOrNull(row.GROUP_SEPARATOR),
    scale: numOrNull(row.SCALE),
    currencySymbol: strOrNull(row.CURRENCY_SYMBOL)
  };
}

export const currencyRepo = {
  getAll(): Currency[] {
    return dbHolder.require().all<Record<string, unknown>>(CURRENCY_SELECT).map(mapCurrency);
  },

  getById(id: number): Currency | null {
    const row = dbHolder
      .require()
      .one<Record<string, unknown>>('SELECT * FROM CURRENCYFORMATS_V1 WHERE CURRENCYID = ?', [id]);
    return row ? mapCurrency(row) : null;
  },

  insert(currency: Currency): void {
    dbHolder
      .require()
      .run(
        'INSERT INTO CURRENCYFORMATS_V1 (CURRENCYID, CURRENCYNAME, CURRENCY_SYMBOL, PFX_SYMBOL, SFX_SYMBOL, BASECONVRATE) VALUES (?, ?, ?, ?, ?, 1.0)',
        [currency.id, currency.name, currency.currencySymbol, currency.pfxSymbol, currency.sfxSymbol]
      );
  },

  update(currency: Currency): void {
    dbHolder
      .require()
      .run(
        'UPDATE CURRENCYFORMATS_V1 SET CURRENCYNAME = ?, CURRENCY_SYMBOL = ?, PFX_SYMBOL = ?, SFX_SYMBOL = ? WHERE CURRENCYID = ?',
        [currency.name, currency.currencySymbol, currency.pfxSymbol, currency.sfxSymbol, currency.id]
      );
  },

  delete(id: number): void {
    dbHolder.require().run('DELETE FROM CURRENCYFORMATS_V1 WHERE CURRENCYID = ?', [id]);
  },

  isUsedByAccount(id: number): boolean {
    const count = dbHolder.require().scalar('SELECT COUNT(*) FROM ACCOUNTLIST_V1 WHERE CURRENCYID = ?', [id]);
    return num(count) > 0;
  }
};

// ------------------------------------------------------------ transactions

function mapTransaction(row: Record<string, unknown>): Transaction {
  return {
    transId: num(row.TRANSID),
    accountId: num(row.ACCOUNTID),
    toAccountId: num(row.TOACCOUNTID),
    payeeId: num(row.PAYEEID),
    type: str(row.TRANSCODE) as TransactionType,
    transAmount: num(row.TRANSAMOUNT),
    toTransAmount: num(row.TOTRANSAMOUNT),
    categId: numOrNull(row.CATEGID),
    transDate: str(row.TRANSDATE),
    lastUpdatedTime: strOrNull(row.LASTUPDATEDTIME),
    notes: strOrNull(row.NOTES),
    followupId: row.FOLLOWUPID === null || row.FOLLOWUPID === undefined ? -1 : num(row.FOLLOWUPID)
  };
}

export const transactionRepo = {
  getAll(): Transaction[] {
    return dbHolder
      .require()
      .all<Record<string, unknown>>(
        'SELECT * FROM CHECKINGACCOUNT_V1 WHERE DELETEDTIME IS NULL ORDER BY TRANSDATE DESC, TRANSID DESC'
      )
      .map(mapTransaction);
  },

  getByAccount(accountId: number): Transaction[] {
    return dbHolder
      .require()
      .all<Record<string, unknown>>(
        `SELECT * FROM CHECKINGACCOUNT_V1
         WHERE (ACCOUNTID = ? OR TOACCOUNTID = ?) AND DELETEDTIME IS NULL
         ORDER BY TRANSDATE DESC, TRANSID DESC`,
        [accountId, accountId]
      )
      .map(mapTransaction);
  },

  getById(transId: number): Transaction | null {
    const row = dbHolder
      .require()
      .one<Record<string, unknown>>('SELECT * FROM CHECKINGACCOUNT_V1 WHERE TRANSID = ?', [transId]);
    return row ? mapTransaction(row) : null;
  },

  /**
   * MMEX insert defaults — see CLAUDE.md. STATUS '', COLOR -1, DELETEDTIME NULL,
   * TRANSACTIONNUMBER NULL, and full-word TRANSCODE.
   */
  insert(tx: Transaction): void {
    dbHolder.require().run(
      `INSERT INTO CHECKINGACCOUNT_V1 (
          TRANSID, ACCOUNTID, TOACCOUNTID, PAYEEID,
          TRANSCODE, TRANSAMOUNT, TOTRANSAMOUNT,
          STATUS, TRANSACTIONNUMBER, NOTES, CATEGID,
          TRANSDATE, LASTUPDATEDTIME, DELETEDTIME,
          FOLLOWUPID, COLOR
      ) VALUES (?, ?, ?, ?, ?, ?, ?, '', NULL, ?, ?, ?, ?, NULL, ?, -1)`,
      [
        tx.transId,
        tx.accountId,
        tx.toAccountId,
        tx.payeeId,
        tx.type,
        tx.transAmount,
        tx.toTransAmount,
        tx.notes,
        tx.categId,
        tx.transDate,
        tx.lastUpdatedTime,
        tx.followupId
      ]
    );
  },

  update(tx: Transaction): void {
    dbHolder.require().run(
      `UPDATE CHECKINGACCOUNT_V1
       SET TRANSCODE = ?, ACCOUNTID = ?, TOACCOUNTID = ?, TRANSAMOUNT = ?, TOTRANSAMOUNT = ?,
           CATEGID = ?, TRANSDATE = ?, NOTES = ?, LASTUPDATEDTIME = ?
       WHERE TRANSID = ?`,
      [
        tx.type,
        tx.accountId,
        tx.toAccountId,
        tx.transAmount,
        tx.toTransAmount,
        tx.categId,
        tx.transDate,
        tx.notes,
        tx.lastUpdatedTime,
        tx.transId
      ]
    );
  },

  delete(transId: number): void {
    dbHolder.require().run('DELETE FROM CHECKINGACCOUNT_V1 WHERE TRANSID = ?', [transId]);
  },

  getExpensesByCurrencyForMonth(yearMonth: string): Record<number, number> {
    const rows = dbHolder.require().all<{ currencyId: number; total: number }>(
      `SELECT a.CURRENCYID AS currencyId, COALESCE(SUM(t.TRANSAMOUNT), 0) AS total
       FROM CHECKINGACCOUNT_V1 t
       JOIN ACCOUNTLIST_V1 a ON t.ACCOUNTID = a.ACCOUNTID
       WHERE t.TRANSCODE = 'Withdrawal'
         AND t.DELETEDTIME IS NULL
         AND substr(t.TRANSDATE, 1, 7) = ?
       GROUP BY a.CURRENCYID`,
      [yearMonth]
    );
    const result: Record<number, number> = {};
    for (const row of rows) result[num(row.currencyId)] = num(row.total);
    return result;
  },

  /** Latest category used for a transaction type — backs "use latest category". */
  getLatestCategoryId(type: TransactionType): number | null {
    const row = dbHolder.require().one<{ CATEGID: number | null }>(
      `SELECT CATEGID FROM CHECKINGACCOUNT_V1
       WHERE TRANSCODE = ? AND DELETEDTIME IS NULL AND CATEGID IS NOT NULL
       ORDER BY TRANSDATE DESC, TRANSID DESC LIMIT 1`,
      [type]
    );
    return row ? numOrNull(row.CATEGID) : null;
  }
};

// -------------------------------------------------------- scheduled (bills)

function mapScheduled(row: Record<string, unknown>): ScheduledTransaction {
  return {
    bdId: num(row.BDID),
    accountId: num(row.ACCOUNTID),
    toAccountId: row.TOACCOUNTID === null || row.TOACCOUNTID === undefined ? -1 : num(row.TOACCOUNTID),
    payeeId: num(row.PAYEEID),
    type: str(row.TRANSCODE) as TransactionType,
    transAmount: num(row.TRANSAMOUNT),
    toTransAmount: row.TOTRANSAMOUNT === null || row.TOTRANSAMOUNT === undefined ? 0 : num(row.TOTRANSAMOUNT),
    categId: numOrNull(row.CATEGID),
    notes: strOrNull(row.NOTES),
    nextOccurrenceDate: str(row.NEXTOCCURRENCEDATE || row.TRANSDATE || ''),
    repeats: row.REPEATS === null || row.REPEATS === undefined ? 0 : num(row.REPEATS),
    numOccurrences: numOrNull(row.NUMOCCURRENCES),
    status: strOrNull(row.STATUS),
    transactionNumber: strOrNull(row.TRANSACTIONNUMBER),
    color: row.COLOR === null || row.COLOR === undefined ? -1 : num(row.COLOR),
    followupId: row.FOLLOWUPID === null || row.FOLLOWUPID === undefined ? -1 : num(row.FOLLOWUPID)
  };
}

export const scheduledRepo = {
  getAllOrdered(): ScheduledTransaction[] {
    return dbHolder
      .require()
      .all<Record<string, unknown>>(
        'SELECT * FROM BILLSDEPOSITS_V1 ORDER BY NEXTOCCURRENCEDATE ASC, BDID ASC'
      )
      .map(mapScheduled);
  },

  getById(bdId: number): ScheduledTransaction | null {
    const row = dbHolder
      .require()
      .one<Record<string, unknown>>('SELECT * FROM BILLSDEPOSITS_V1 WHERE BDID = ?', [bdId]);
    return row ? mapScheduled(row) : null;
  },

  getDue(todayIsoDate: string): ScheduledTransaction[] {
    return dbHolder
      .require()
      .all<Record<string, unknown>>(
        `SELECT * FROM BILLSDEPOSITS_V1
         WHERE substr(NEXTOCCURRENCEDATE, 1, 10) <= ?
         ORDER BY NEXTOCCURRENCEDATE ASC, BDID ASC`,
        [todayIsoDate]
      )
      .map(mapScheduled);
  },

  insert(s: ScheduledTransaction): void {
    dbHolder.require().run(
      `INSERT INTO BILLSDEPOSITS_V1 (
          BDID, ACCOUNTID, TOACCOUNTID, PAYEEID,
          TRANSCODE, TRANSAMOUNT, STATUS, TRANSACTIONNUMBER, NOTES, CATEGID,
          TRANSDATE, FOLLOWUPID, TOTRANSAMOUNT, REPEATS,
          NEXTOCCURRENCEDATE, NUMOCCURRENCES, COLOR
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        s.bdId,
        s.accountId,
        s.toAccountId,
        s.payeeId,
        s.type,
        s.transAmount,
        s.status ?? '',
        s.transactionNumber,
        s.notes,
        s.categId,
        s.nextOccurrenceDate,
        s.followupId,
        s.toTransAmount,
        s.repeats,
        s.nextOccurrenceDate,
        s.numOccurrences,
        s.color
      ]
    );
  },

  update(s: ScheduledTransaction): void {
    dbHolder.require().run(
      `UPDATE BILLSDEPOSITS_V1
       SET ACCOUNTID = ?, TOACCOUNTID = ?, PAYEEID = ?, TRANSCODE = ?, TRANSAMOUNT = ?,
           STATUS = ?, TRANSACTIONNUMBER = ?, NOTES = ?, CATEGID = ?, TRANSDATE = ?,
           FOLLOWUPID = ?, TOTRANSAMOUNT = ?, REPEATS = ?, NEXTOCCURRENCEDATE = ?,
           NUMOCCURRENCES = ?, COLOR = ?
       WHERE BDID = ?`,
      [
        s.accountId,
        s.toAccountId,
        s.payeeId,
        s.type,
        s.transAmount,
        s.status ?? '',
        s.transactionNumber,
        s.notes,
        s.categId,
        s.nextOccurrenceDate,
        s.followupId,
        s.toTransAmount,
        s.repeats,
        s.nextOccurrenceDate,
        s.numOccurrences,
        s.color,
        s.bdId
      ]
    );
  },

  delete(bdId: number): void {
    dbHolder.require().run('DELETE FROM BILLSDEPOSITS_V1 WHERE BDID = ?', [bdId]);
  }
};

export const infoRepo = {
  get(key: string): string | null {
    const row = dbHolder
      .require()
      .one<{ INFOVALUE: string }>('SELECT INFOVALUE FROM INFOTABLE_V1 WHERE INFONAME = ?', [key]);
    return row ? str(row.INFOVALUE) : null;
  }
};

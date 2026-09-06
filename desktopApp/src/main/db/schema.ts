/**
 * DDL for brand-new .mmb files only.
 *
 * These statements are byte-for-byte the `CREATE TABLE` blocks of the SQLDelight
 * `.sq` files in `shared/src/commonMain/sqldelight/com/konhit/financeapp/db/`.
 * They are executed exactly once, when creating a file — never against an
 * existing MMEX database.
 */
export const CREATE_SCHEMA_SQL = `
CREATE TABLE IF NOT EXISTS ACCOUNTLIST_V1 (
    ACCOUNTID       INTEGER NOT NULL PRIMARY KEY,
    ACCOUNTNAME     TEXT    NOT NULL,
    ACCOUNTTYPE     TEXT    NOT NULL DEFAULT 'Checking',
    ACCOUNTNUM      TEXT,
    STATUS          TEXT    NOT NULL DEFAULT 'Open',
    NOTES           TEXT,
    HELDAT          TEXT,
    WEBSITE         TEXT,
    CONTACTINFO     TEXT,
    ACCESSINFO      TEXT,
    INITIALBAL      REAL    NOT NULL DEFAULT 0,
    FAVORITEACCT    TEXT    NOT NULL DEFAULT 'FALSE',
    CURRENCYID      INTEGER NOT NULL DEFAULT 1,
    STATEMENTLOCKED INTEGER,
    STATEMENTDATE   TEXT,
    MINIMUMBALANCE  REAL,
    CREDITLIMIT     REAL,
    INTERESTRATE    REAL,
    PAYMENTDUEDATE  TEXT,
    MINIMUMPAYMENT  REAL
);

CREATE TABLE IF NOT EXISTS BILLSDEPOSITS_V1 (
    BDID                INTEGER PRIMARY KEY,
    ACCOUNTID           INTEGER NOT NULL,
    TOACCOUNTID         INTEGER,
    PAYEEID             INTEGER NOT NULL,
    TRANSCODE           TEXT    NOT NULL,
    TRANSAMOUNT         REAL    NOT NULL,
    STATUS              TEXT,
    TRANSACTIONNUMBER   TEXT,
    NOTES               TEXT,
    CATEGID             INTEGER,
    TRANSDATE           TEXT,
    FOLLOWUPID          INTEGER,
    TOTRANSAMOUNT       REAL,
    REPEATS             INTEGER,
    NEXTOCCURRENCEDATE  TEXT,
    NUMOCCURRENCES      INTEGER,
    COLOR               INTEGER DEFAULT -1
);

CREATE TABLE IF NOT EXISTS CATEGORY_V1 (
    CATEGID     INTEGER NOT NULL PRIMARY KEY,
    CATEGNAME   TEXT    NOT NULL,
    PARENTID    INTEGER NOT NULL DEFAULT -1,
    ACTIVE      INTEGER
);

CREATE TABLE IF NOT EXISTS CURRENCYFORMATS_V1 (
    CURRENCYID          INTEGER NOT NULL PRIMARY KEY,
    CURRENCYNAME        TEXT    NOT NULL,
    PFX_SYMBOL          TEXT,
    SFX_SYMBOL          TEXT,
    DECIMAL_POINT       TEXT,
    THOUSANDS_SEPARATOR TEXT,
    UNIT_NAME           TEXT,
    CENT_NAME           TEXT,
    SCALE               INTEGER,
    BASECONVRATE        REAL,
    CURRENCY_SYMBOL     TEXT,
    GROUP_SEPARATOR     TEXT
);

CREATE TABLE IF NOT EXISTS INFOTABLE_V1 (
    INFOID      INTEGER NOT NULL PRIMARY KEY,
    INFONAME    TEXT    NOT NULL,
    INFOVALUE   TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS PAYEE_V1 (
    PAYEEID         INTEGER NOT NULL PRIMARY KEY,
    PAYEENAME       TEXT    NOT NULL,
    CATEGID         INTEGER,
    NUMBER          TEXT,
    WEBSITE         TEXT,
    NOTES           TEXT,
    ACTIVE          INTEGER
);

CREATE TABLE IF NOT EXISTS SPLITTRANSACTIONS_V1 (
    SPLITTRANSID    INTEGER NOT NULL PRIMARY KEY,
    TRANSID         INTEGER NOT NULL,
    CATEGID         INTEGER,
    SPLITTRANSAMOUNT REAL
);

CREATE TABLE IF NOT EXISTS CHECKINGACCOUNT_V1 (
    TRANSID             INTEGER NOT NULL PRIMARY KEY,
    ACCOUNTID           INTEGER NOT NULL,
    TOACCOUNTID         INTEGER NOT NULL DEFAULT -1,
    PAYEEID             INTEGER NOT NULL DEFAULT -1,
    TRANSCODE           TEXT    NOT NULL,
    TRANSAMOUNT         REAL    NOT NULL,
    STATUS              TEXT    NOT NULL DEFAULT '',
    TRANSACTIONNUMBER   TEXT,
    NOTES               TEXT,
    CATEGID             INTEGER,
    TRANSDATE           TEXT    NOT NULL,
    LASTUPDATEDTIME     TEXT,
    DELETEDTIME         TEXT,
    FOLLOWUPID          INTEGER NOT NULL DEFAULT -1,
    TOTRANSAMOUNT       REAL    NOT NULL DEFAULT 0,
    COLOR               INTEGER NOT NULL DEFAULT -1
);
`;

/**
 * Seed currencies for a new file. The Android app relies on the currency table
 * of an existing MMEX file; a file created from scratch needs at least one row
 * so accounts can reference a currency (BASECURRENCYID = 1).
 */
export const SEED_CURRENCIES: Array<{
  id: number;
  name: string;
  pfx: string;
  sfx: string;
  symbol: string;
}> = [
  { id: 1, name: 'Euro', pfx: '', sfx: '', symbol: 'EUR' },
  { id: 2, name: 'US Dollar', pfx: '$', sfx: '', symbol: 'USD' },
  { id: 3, name: 'British Pound', pfx: '£', sfx: '', symbol: 'GBP' }
];

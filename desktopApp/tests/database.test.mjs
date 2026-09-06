import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { MmexDatabase, dbHolder } = require('../dist/main/main/db/database.js');
const { accountRepo, categoryRepo, transactionRepo, scheduledRepo, infoRepo } = require('../dist/main/main/repositories.js');
const initSqlJs = require('sql.js');

const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'financeapp-desktop-'));

function tempFile(name) {
  return path.join(workDir, name);
}

async function newDb(name) {
  const file = tempFile(name);
  const db = await MmexDatabase.createNew(file);
  dbHolder.open(db);
  return { db, file };
}

/** Reads a saved file with a bare sql.js instance — no app code involved. */
async function inspect(file) {
  const SQL = await initSqlJs({
    wasmBinary: fs.readFileSync(path.join(path.dirname(require.resolve('sql.js')), 'sql-wasm.wasm'))
  });
  const raw = new SQL.Database(fs.readFileSync(file));
  const list = (sql) => {
    const stmt = raw.prepare(sql);
    const rows = [];
    while (stmt.step()) rows.push(stmt.getAsObject());
    stmt.free();
    return rows;
  };
  return {
    tables: list("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").map(
      (r) => r.name
    ),
    triggers: list("SELECT name FROM sqlite_master WHERE type='trigger'").map((r) => r.name),
    views: list("SELECT name FROM sqlite_master WHERE type='view'").map((r) => r.name),
    indexes: list("SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%'").map(
      (r) => r.name
    ),
    userVersion: list('PRAGMA user_version')[0].user_version,
    rows: (sql) => list(sql),
    close: () => raw.close()
  };
}

test('createNew writes the MMEX skeleton (TC-01/TC-10)', async () => {
  const { file } = await newDb('create.mmb');
  assert.equal(infoRepo.get('DATAVERSION'), '3');
  assert.equal(infoRepo.get('BASECURRENCYID'), '1');
  dbHolder.close();

  const info = await inspect(file);
  assert.deepEqual(info.tables.sort(), [
    'ACCOUNTLIST_V1',
    'BILLSDEPOSITS_V1',
    'CATEGORY_V1',
    'CHECKINGACCOUNT_V1',
    'CURRENCYFORMATS_V1',
    'INFOTABLE_V1',
    'PAYEE_V1',
    'SPLITTRANSACTIONS_V1'
  ]);
  assert.equal(info.triggers.length, 0);
  assert.equal(info.views.length, 0);
  info.close();
});

test('inserted transactions carry the MMEX defaults (TC-04/TC-05/TC-06)', async () => {
  const { db, file } = await newDb('insert.mmb');
  accountRepo.insert({ id: 1001, name: 'Cash', type: 'Cash', initialBal: 100, currencyId: 1, status: '', balance: 0 });
  accountRepo.insert({ id: 1002, name: 'Bank', type: 'Savings', initialBal: 0, currencyId: 1, status: '', balance: 0 });
  categoryRepo.insert({ id: 2001, name: 'Food', parentId: -1 });

  const base = {
    payeeId: db.defaultPayeeId,
    categId: 2001,
    transDate: '2026-03-01T00:00:00',
    lastUpdatedTime: '2026-03-01T10:00:00',
    notes: 'test',
    followupId: -1
  };
  transactionRepo.insert({ ...base, transId: 3001, accountId: 1001, toAccountId: -1, type: 'Withdrawal', transAmount: 25, toTransAmount: 0 });
  transactionRepo.insert({ ...base, transId: 3002, accountId: 1001, toAccountId: -1, type: 'Deposit', transAmount: 60, toTransAmount: 0 });
  transactionRepo.insert({ ...base, transId: 3003, accountId: 1001, toAccountId: 1002, type: 'Transfer', transAmount: 30, toTransAmount: 30 });
  db.save();
  dbHolder.close();

  const info = await inspect(file);
  const rows = info.rows('SELECT * FROM CHECKINGACCOUNT_V1 ORDER BY TRANSID');
  assert.equal(rows.length, 3);
  for (const row of rows) {
    assert.equal(row.STATUS, '');
    assert.equal(row.COLOR, -1);
    assert.equal(row.DELETEDTIME, null);
    assert.equal(row.FOLLOWUPID, -1);
    assert.ok(['Deposit', 'Withdrawal', 'Transfer'].includes(row.TRANSCODE));
    assert.match(row.TRANSDATE, /^\d{4}-\d{2}-\d{2}T00:00:00$/);
    assert.match(row.LASTUPDATEDTIME, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/);
    assert.ok(row.PAYEEID > 0);
  }
  assert.equal(rows[0].TOACCOUNTID, -1);
  assert.equal(rows[0].TOTRANSAMOUNT, 0);
  assert.equal(rows[2].TOACCOUNTID, 1002);
  assert.equal(rows[2].TOTRANSAMOUNT, 30);
  info.close();
});

test('balances follow the MMEX formula (TC-02)', async () => {
  const { db } = await newDb('balance.mmb');
  accountRepo.insert({ id: 1, name: 'A', type: 'Cash', initialBal: 100, currencyId: 1, status: '', balance: 0 });
  accountRepo.insert({ id: 2, name: 'B', type: 'Cash', initialBal: 0, currencyId: 1, status: '', balance: 0 });
  const base = { payeeId: db.defaultPayeeId, categId: null, lastUpdatedTime: null, notes: null, followupId: -1 };
  transactionRepo.insert({ ...base, transId: 11, accountId: 1, toAccountId: -1, type: 'Deposit', transAmount: 50, toTransAmount: 0, transDate: '2026-01-10T00:00:00' });
  transactionRepo.insert({ ...base, transId: 12, accountId: 1, toAccountId: -1, type: 'Withdrawal', transAmount: 20, toTransAmount: 0, transDate: '2026-02-10T00:00:00' });
  transactionRepo.insert({ ...base, transId: 13, accountId: 1, toAccountId: 2, type: 'Transfer', transAmount: 30, toTransAmount: 25, transDate: '2026-03-10T00:00:00' });

  assert.equal(accountRepo.getBalance(1), 100 + 50 - 20 - 30);
  assert.equal(accountRepo.getBalance(2), 25);
  // As-of-date balance ignores everything after the given day.
  assert.equal(accountRepo.getBalanceAtDate(1, '2026-01-31'), 150);
  assert.equal(accountRepo.getBalanceAtDate(1, '2026-02-28'), 130);
  assert.equal(transactionRepo.getByAccount(2).length, 1);

  const monthly = transactionRepo.getExpensesByCurrencyForMonth('2026-02');
  assert.equal(monthly[1], 20);
  dbHolder.close();
});

test('writing an existing file preserves its schema and user_version (TC-10/TC-11)', async () => {
  // Build a file that looks like a real MMEX database: extra table, index,
  // trigger, view and user_version = 20.
  const file = tempFile('existing.mmb');
  const { db } = await newDb('existing.mmb');
  db.run('CREATE TABLE ASSETS_V1 (ASSETID INTEGER PRIMARY KEY, ASSETNAME TEXT)');
  db.run('CREATE INDEX IDX_CHECKING_ACCOUNT ON CHECKINGACCOUNT_V1 (ACCOUNTID)');
  db.run('CREATE VIEW OPEN_ACCOUNTS AS SELECT ACCOUNTID FROM ACCOUNTLIST_V1');
  db.run(
    "CREATE TRIGGER TOUCH_ASSETS AFTER INSERT ON ASSETS_V1 BEGIN UPDATE ASSETS_V1 SET ASSETNAME = 'x' WHERE ASSETID = NEW.ASSETID; END"
  );
  db.run('PRAGMA user_version = 20');
  db.save();
  dbHolder.close();

  const before = await inspect(file);
  assert.equal(before.userVersion, 20);
  before.close();

  // Re-open the way the app does, write a row, save.
  const reopened = await MmexDatabase.openExisting(file);
  dbHolder.open(reopened);
  accountRepo.insert({ id: 7, name: 'Cash', type: 'Cash', initialBal: 5, currencyId: 1, status: '', balance: 0 });
  transactionRepo.insert({
    transId: 71,
    accountId: 7,
    toAccountId: -1,
    payeeId: reopened.defaultPayeeId,
    type: 'Deposit',
    transAmount: 1,
    toTransAmount: 0,
    categId: null,
    transDate: '2026-04-01T00:00:00',
    lastUpdatedTime: '2026-04-01T09:00:00',
    notes: null,
    followupId: -1
  });
  reopened.save();
  dbHolder.close();

  const after = await inspect(file);
  assert.equal(after.userVersion, 20, 'user_version must never be rewritten');
  assert.ok(after.tables.includes('ASSETS_V1'), 'unknown MMEX tables must survive');
  assert.ok(after.indexes.includes('IDX_CHECKING_ACCOUNT'));
  assert.deepEqual(after.views, ['OPEN_ACCOUNTS']);
  assert.deepEqual(after.triggers, ['TOUCH_ASSETS']);
  assert.equal(after.rows('SELECT COUNT(*) c FROM CHECKINGACCOUNT_V1')[0].c, 1);
  assert.equal(after.rows("SELECT INFOVALUE v FROM INFOTABLE_V1 WHERE INFONAME='DATAVERSION'")[0].v, '3');
  after.close();
});

test('opening a non-MMEX SQLite file is refused', async () => {
  const SQL = await initSqlJs({
    wasmBinary: fs.readFileSync(path.join(path.dirname(require.resolve('sql.js')), 'sql-wasm.wasm'))
  });
  const foreign = new SQL.Database();
  foreign.run('CREATE TABLE NOTES (ID INTEGER PRIMARY KEY)');
  const file = tempFile('foreign.db');
  fs.writeFileSync(file, Buffer.from(foreign.export()));
  foreign.close();

  await assert.rejects(() => MmexDatabase.openExisting(file), /Not a MoneyManagerEx file/);
});

test('scheduled rules round-trip through BILLSDEPOSITS_V1', async () => {
  const { db } = await newDb('scheduled.mmb');
  scheduledRepo.insert({
    bdId: 900,
    accountId: 1,
    toAccountId: -1,
    payeeId: db.defaultPayeeId,
    type: 'Withdrawal',
    transAmount: 42.5,
    toTransAmount: 0,
    categId: null,
    notes: 'Rent',
    nextOccurrenceDate: '2026-05-01T00:00:00',
    repeats: 3,
    numOccurrences: -1,
    status: '',
    transactionNumber: 'END:2026-12-31',
    color: -1,
    followupId: -1
  });

  const [stored] = scheduledRepo.getAllOrdered();
  assert.equal(stored.bdId, 900);
  assert.equal(stored.type, 'Withdrawal');
  assert.equal(stored.transAmount, 42.5);
  assert.equal(stored.transactionNumber, 'END:2026-12-31');
  assert.equal(scheduledRepo.getDue('2026-05-01').length, 1);
  assert.equal(scheduledRepo.getDue('2026-04-30').length, 0);

  scheduledRepo.update({ ...stored, transAmount: 50, nextOccurrenceDate: '2026-06-01T00:00:00' });
  assert.equal(scheduledRepo.getById(900).transAmount, 50);
  scheduledRepo.delete(900);
  assert.equal(scheduledRepo.getAllOrdered().length, 0);
  dbHolder.close();
});

test.after(() => {
  fs.rmSync(workDir, { recursive: true, force: true });
});

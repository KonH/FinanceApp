# Finance App — Prototype Specification

## Goal

Build a personal finance Android app (later: Windows, macOS) that reads and writes
MoneyManagerEx-compatible `.mmb` SQLite files. No server. Sync via Google Drive.

---

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| Android UI | Jetpack Compose |
| Desktop UI (later) | Compose Multiplatform |
| Shared logic | Kotlin Multiplatform (KMP) |
| Database ORM | SQLDelight |
| Dependency injection | Koin |
| Google Drive sync | Google Drive Android SDK (Android) / Drive REST API via Ktor (Desktop) |
| Settings storage | DataStore (Android) |
| IDE | Android Studio + KMP plugin |

---

## Database

### Format
- Must use full **MoneyManagerEx (MMEX) SQLite schema** — `.mmb` file extension.
- The app must be able to open existing MMEX files created by other MMEX clients.
- Reference schema: https://github.com/moneymanagerex/moneymanagerex (see `src/db/`)
- Key MMEX tables to support (minimum for prototype):
  - `ACCOUNTLIST_V1` — accounts
  - `CURRENCYFORMATS_V1` — currencies
  - `CATEGORY_V1` — categories (self-referencing `PARENTID` for nesting)
  - `CHECKINGACCOUNT_V1` — transactions (income, expense, transfer)
  - `INFOTABLE_V1` — metadata / app settings inside the file
  - `SPLITTRANSACTIONS_V1` — not required for prototype, but do not break it
- Do **not** invent custom tables. Store all app data inside the MMEX schema.

### MMEX Write Compatibility (critical)

The app must produce rows that MMEX desktop can read without errors. Requirements:

**SQLDelight configuration**
- Open existing `.mmb` files as-is. SQLDelight must NOT run `CREATE TABLE` or migration
  statements against an existing file — doing so will destroy MMEX triggers and views.
- Use `verifyMigrations = false` and treat the schema as pre-existing (schema-only mode).
- SQLDelight is used for query generation only, not schema ownership.

**CHECKINGACCOUNT_V1 — required fields when inserting**
| Field | Required value |
|---|---|
| `TRANSCODE` | `'Withdrawal'` (expense), `'Deposit'` (income), `'Transfer'` — exact full words |
| `STATUS` | `''` (empty string) — MMEX default for new transactions |
| `FOLLOWUPID` | `-1` |
| `COLOR` | `-1` |
| `TOACCOUNTID` | target account ID for transfers, `-1` for income/expense |
| `TOTRANSAMOUNT` | destination amount for transfers, `0` for income/expense |
| `TRANSID` | `System.currentTimeMillis()` — MMEX Android uses millisecond timestamps |
| `LASTUPDATEDTIME` | current datetime as `'YYYY-MM-DDTHH:mm:ss'` |

**Balance calculation formula** (verified against example.mmb):
```
balance = INITIALBAL
        + SUM(TRANSAMOUNT) where TRANSCODE='Deposit' and ACCOUNTID=X
        - SUM(TRANSAMOUNT) where TRANSCODE='Withdrawal' and ACCOUNTID=X
        + SUM(TOTRANSAMOUNT) where TRANSCODE='Transfer' and TOACCOUNTID=X
        - SUM(TRANSAMOUNT) where TRANSCODE='Transfer' and ACCOUNTID=X
```
Note: transfers use `TRANSAMOUNT` for the source and `TOTRANSAMOUNT` for the destination.

**INFOTABLE_V1**
- Read only. Never update `DATAVERSION` or any existing key.
- MMEX uses this to validate file integrity on open.

**Preserve existing database objects**
- Do not `DROP` or `ALTER` any existing triggers, views, or indexes.
- When creating a brand-new file, initialise with the full MMEX schema including
  all triggers and views from the reference repo.

**Round-trip test (required before Phase 1 sign-off)**
1. Create a file in MMEX desktop, add sample data.
2. Open file in this app, add a transaction of each type (income, expense, transfer).
3. Reopen the file in MMEX desktop — verify all transactions appear correctly,
   balances match, and no errors are shown.

### Access modes
- **Read-write**: one client at a time. No concurrent writes.
- **Read-only**: multiple clients can open simultaneously.
- Mode is stored in app settings alongside the file path.

---

## Google Drive Sync

- File is stored on Google Drive. App downloads it locally to work, then uploads on sync.
- **Sync triggers**: app open, any database change, app closing.
- **Manual sync button** always visible.
- **Conflict resolution**: if remote file timestamp differs from local baseline,
  warn the user and let them choose which version to keep (local or remote).
- No merge — one file wins entirely.

---

## App Settings (persisted via DataStore)

- Google Drive file ID / path
- Access mode (read-write or read-only)
- Last sync timestamp

---

## Account Types

- **Cash**
- **Investment** (simple balance only — no holdings tracking)
- Each account has exactly one currency.
- Transactions always use the account's currency (no per-transaction currency override).

---

## Transactions

### Types (MMEX TRANSCODE string values — exact, case-sensitive)
- `'Deposit'` — income, money coming into an account
- `'Withdrawal'` — expense, money leaving an account
- `'Transfer'` — between two of the user's own accounts

### Fields (maps to `CHECKINGACCOUNT_V1`)
| Column | Value when inserting | Notes |
|---|---|---|
| `TRANSID` | millisecond timestamp (e.g. `System.currentTimeMillis()`) | MMEX Android convention — not sequential |
| `ACCOUNTID` | selected account ID | Source account for transfers |
| `TOACCOUNTID` | target account ID for transfers; `-1` otherwise | Never NULL |
| `PAYEEID` | default/system payee ID | Required NOT NULL — create a default payee on new file init; reuse existing on open |
| `TRANSCODE` | `'Deposit'`, `'Withdrawal'`, or `'Transfer'` | Exact strings, NOT `'D'`/`'W'`/`'T'` |
| `TRANSAMOUNT` | positive numeric amount | Source account amount |
| `TOTRANSAMOUNT` | destination amount for transfers; `0` for income/expense | Allows different amounts per account (manual entry, no conversion) |
| `STATUS` | `''` (empty string) | MMEX default for new unreconciled transactions |
| `TRANSACTIONNUMBER` | `NULL` | |
| `NOTES` | memo text or `NULL` | |
| `CATEGID` | selected category ID | Required — never NULL in practice; all transactions must have a category |
| `TRANSDATE` | ISO format `'YYYY-MM-DDT00:00:00'` | Time component always `00:00:00` |
| `LASTUPDATEDTIME` | ISO format `'YYYY-MM-DDTHH:mm:ss'` | Set on insert and update |
| `DELETEDTIME` | `NULL` | Soft delete not used in prototype |
| `FOLLOWUPID` | `-1` | MMEX Android default |
| `COLOR` | `-1` | MMEX default |

### Constraints
- No void, reconciled, or payee UI — but `PAYEEID` must still be a valid row in `PAYEE_V1`.
- On opening an existing file: reuse the first existing payee as the default.
- On creating a new file: insert one default payee row and use its ID for all transactions.
- Transfer amounts: both `TRANSAMOUNT` and `TOTRANSAMOUNT` entered manually.

---

## Categories

- Stored in `CATEGORY_V1` with `PARENTID` for parent reference.
- Unlimited nesting depth.
- CRUD: add, edit, remove (warn if category has transactions).
- Display as indented tree in the category picker.

---

## Currencies

- Stored in `CURRENCYFORMATS_V1`.
- CRUD: add, edit, remove.
- Each account is assigned one currency.
- No exchange rates, no conversion logic.

---

## Screens

### 1. First Launch
- If no database configured: ask user to **create new** file or **open existing** file from Google Drive.
- On create: prompt for file name, create MMEX-compatible schema, upload to Drive.
- On open: Google Drive file picker.

### 2. Main Page
- List of all accounts with current balance and currency symbol.
- Total balance (same-currency accounts only, or grouped by currency).
- **Search bar**: searches across transaction notes, amounts, category names,
  account names, and date range.
- **Settings button** (top bar).
- Tap account → Account Page.

### 3. Settings Page
- Database section: current file path, mode toggle (read-write / read-only),
  change file button, manual sync button + last sync time.
- Accounts section: add / edit / remove accounts.
- Categories section: add / edit / remove, shown as nested tree.
- Currencies section: add / edit / remove.

### 4. Account Page
- Header: account name, balance, currency.
- Transaction list: newest first. Each row shows date, category, notes (truncated), amount.
- Swipe or long-press options: **edit**, **remove** (with confirmation).
- FAB: **Add transaction**.

### 5. Transaction View (add / edit)
- Fields: type selector (income / expense / transfer), account selector,
  to-account selector (transfer only), date picker (default = now),
  category picker (tree), amount field, notes field.
- Amount field: tapping opens an **inline calculator view** (basic: +, −, ×, ÷, =).
  Result fills the amount field.
- Save / Cancel buttons.

---

## Search

Covers: transaction notes/memo, amount (exact or range), category name (full path),
account name, date range. Results shown as a flat transaction list, grouped by account
or date (TBD during implementation).

---

## Prototype Scope (Phase 1 — Android only)

Implement in this order:

1. MMEX schema setup via SQLDelight (`.sq` files matching MMEX table definitions)
2. Google Drive auth + file download/upload
3. Settings screen (file setup, mode selection)
4. Main page (account list + balances)
5. Account page (transaction list)
6. Transaction add/edit (income, expense, transfer)
7. Category picker (nested tree)
8. Search
9. Sync logic (triggers + conflict warning dialog)

**Out of scope for prototype**: Desktop targets, split transactions, payees,
budget tracking, reports, recurring transactions, attachments.

---

## Key Constraints

- No backend, no custom server.
- All data lives in the `.mmb` file. App settings live in DataStore (not in the file).
- Must not corrupt MMEX files — test open/save round-trips against the official MMEX desktop app.
- Read-only mode must disable all write operations in the UI.

---

## Compatibility Test Cases (against example.mmb)

The file `example.mmb` is the reference fixture for all tests below.
It contains: **16 accounts** (Cash + Investment), **119 categories**, **1728 transactions**,
**2 payees**, **168 currencies**, base currency EUR (ID=2).

Run these tests in order — each builds on the previous state.

---

### TC-01 · Open existing file — read integrity

**Action**: open `example.mmb` in read-only mode.

**Assert**:
- Account list loads with exactly 16 accounts
- `Raif RSD` shown with `INITIALBAL = 284889`, currency `RSD` (ID=121)
- `Raif UCITS EUR` shown as Investment type account
- Category tree loads 119 entries; root `Bills` (ID=1) has children: Telephone, Electricity, Gas, Internet, Rent, Cable TV, Water
- Root categories have `PARENTID = -1`
- `INFOTABLE_V1.DATAVERSION = 3` — must not be altered
- No crashes, no schema migrations attempted

---

### TC-02 · Balance calculation

**Action**: open `example.mmb`, read balance for account `Raif RSD` (ID=`1748881720210000`).

**Assert**:
- Formula applied: `INITIALBAL + deposits − withdrawals + transfer_in (TOTRANSAMOUNT) − transfer_out (TRANSAMOUNT)`
- Expected balance ≈ **334 857.57 RSD** (verify with the Python formula from inspection)
- Balance for `Raif EUR` (ID=`1748881873924000`) computed separately, not mixed into RSD total

---

### TC-03 · Transaction list — existing data

**Action**: open account `Raif RSD`, view transaction list.

**Assert**:
- 1728 total transactions across the file (only those for this account shown)
- Transactions sorted newest first
- First visible transaction date is `2026-05-03`
- Transaction with `TRANSID=1749285537504000`: type=Deposit, amount=500, notes=`'Andrey - gasoline'`, category ID=55
- Transaction with `TRANSID=1749068491324000`: type=Transfer, TRANSAMOUNT=117797.1, TOTRANSAMOUNT=1000, toAccount=`Raif EUR`

---

### TC-04 · Insert expense (Withdrawal)

**Action**: in read-write mode, add expense on account `Raif RSD`:
- Amount: `1500`
- Category: `Food > Groceries` (CATEGID=10)
- Date: today
- Notes: `'test withdrawal'`

**Assert inserted row** in `CHECKINGACCOUNT_V1`:
- `TRANSCODE = 'Withdrawal'` (not `'W'`)
- `TRANSAMOUNT = 1500`
- `TOTRANSAMOUNT = 0`
- `TOACCOUNTID = -1`
- `STATUS = ''` (empty string, not `'U'` or `NULL`)
- `FOLLOWUPID = -1`
- `COLOR = -1`
- `PAYEEID` is a valid ID from `PAYEE_V1`
- `TRANSID` is a 16-digit millisecond timestamp
- `LASTUPDATEDTIME` is set to current time
- `CATEGID = 10`
- `TRANSDATE` format matches `'YYYY-MM-DDT00:00:00'`
- `Raif RSD` balance decreases by 1500

---

### TC-05 · Insert income (Deposit)

**Action**: add income on account `Raif RSD`:
- Amount: `50000`
- Category: any income category
- Notes: `'test deposit'`

**Assert inserted row**:
- `TRANSCODE = 'Deposit'` (not `'D'`)
- `TRANSAMOUNT = 50000`
- `TOTRANSAMOUNT = 0`
- `TOACCOUNTID = -1`
- All other defaults same as TC-04
- `Raif RSD` balance increases by 50000

---

### TC-06 · Insert transfer

**Action**: add transfer from `Raif RSD` → `Raif EUR`:
- Source amount: `117000` (RSD)
- Destination amount: `1000` (EUR)

**Assert inserted row**:
- `TRANSCODE = 'Transfer'`
- `ACCOUNTID` = ID of `Raif RSD`
- `TOACCOUNTID` = ID of `Raif EUR`
- `TRANSAMOUNT = 117000`
- `TOTRANSAMOUNT = 1000`
- `Raif RSD` balance decreases by 117000
- `Raif EUR` balance increases by 1000

---

### TC-07 · Edit transaction

**Action**: edit the transaction inserted in TC-04, change amount to `2000` and notes to `'edited'`.

**Assert**:
- `TRANSAMOUNT` updated to `2000`
- `NOTES` updated to `'edited'`
- `LASTUPDATEDTIME` updated to current time
- `TRANSID` unchanged
- Balance recalculated correctly

---

### TC-08 · Delete transaction

**Action**: delete the transaction inserted in TC-05.

**Assert**:
- Row removed from `CHECKINGACCOUNT_V1` (hard delete — `DELETEDTIME` not used)
- Account balance updated correctly
- No orphan references

---

### TC-09 · INFOTABLE_V1 preservation

**Action**: perform TC-04 through TC-08 (insert, edit, delete).

**Assert after all operations**:
- `INFOTABLE_V1` still has exactly 5 rows
- `DATAVERSION` still = `3`
- `BASECURRENCYID` still = `2`
- No new rows added to `INFOTABLE_V1`

---

### TC-10 · Schema preservation

**Action**: open `example.mmb`, perform any write operation, save.

**Assert**:
- All 23 original tables still present
- No tables added or removed
- No columns added, removed, or renamed
- All existing triggers and views intact (none in this file — verify count = 0 triggers, 0 views)
- `SPLITTRANSACTIONS_V1` table untouched (0 rows, structure intact)

---

### TC-11 · Category tree — nested display

**Action**: open the category picker in the transaction form.

**Assert**:
- Root categories shown at top level: Bills, Food, Leisure, Automobile, etc.
- `Food` expands to show: Groceries (ID=10), Delivery (ID=11)
- `Bills` expands to show 7 children (Telephone, Electricity, Gas, Internet, Rent, Cable TV, Water)
- Selecting a child category (e.g. Groceries) stores `CATEGID=10`, not the parent ID

---

### TC-12 · Search

**Action**: search for `'gasoline'` across all transactions.

**Assert**:
- Returns transaction `TRANSID=1749285537504000` (notes=`'Andrey - gasoline'`)
- Search for amount `'500'` returns at minimum that transaction
- Search by account name `'Raif RSD'` returns transactions for that account
- Search by category name `'Groceries'` returns transactions with `CATEGID=10`

---

### TC-13 · Read-only mode enforcement

**Action**: open `example.mmb` in read-only mode, attempt to add a transaction.

**Assert**:
- Add transaction button is disabled or hidden
- No write operations reach SQLite
- File on disk is byte-for-byte identical before and after opening

---

### TC-14 · Round-trip with MMEX desktop

**Manual test** (cannot be automated):
1. Open `example.mmb` in MMEX desktop — note balances for all accounts.
2. Open same file in this app (read-write), add one Withdrawal, one Deposit, one Transfer.
3. Close app (triggers sync if Drive connected, or just close the local file).
4. Reopen file in MMEX desktop.

**Assert in MMEX desktop**:
- All three new transactions appear in the correct accounts
- Amounts and categories display correctly
- MMEX shows no errors, warnings, or schema migration prompts
- Balances match expected values


# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Always build via `build.ps1` (sets `JAVA_HOME` to Android Studio JBR and writes output to `.tmp\gradle\log.txt`).
The working directory is always the project root — never use `cd` before commands.

```powershell
.\build.ps1
# Then check the log:
Get-Content .tmp\gradle\log.txt | Select-Object -Last 30
```

```bash
# Assemble debug APK (direct, only if JAVA_HOME is already set)
./gradlew :androidApp:assembleDebug

# Install on connected device
./gradlew :androidApp:installDebug

# Run unit tests (shared module)
./gradlew :shared:testDebugUnitTest

# Run instrumented tests (requires device/emulator)
./gradlew :shared:connectedAndroidTest

# Run a single test class
./gradlew :shared:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.konhit.financeapp.db.TC01_OpenExistingTest
```

## Project Layout

```
shared/      — Kotlin Multiplatform module: domain logic, database, Drive sync
androidApp/  — Android Compose UI + ViewModels + DI wiring
```

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.0.21 |
| Android UI | Jetpack Compose (Compose BOM 2024.09.00) |
| Shared logic | Kotlin Multiplatform (Android target only for now) |
| Database ORM | SQLDelight 2.0.2 |
| DI | Koin 3.5.6 |
| Settings | DataStore Preferences 1.1.1 |
| Drive sync | Google Drive Android SDK + play-services-auth |
| Navigation | Navigation Compose 2.7.7 |
| Async | Coroutines + Flow |

## Architecture

### Modules

**`:shared`** — KMP module. Contains:
- `commonMain` — domain models, repository interfaces, `ComputeBalanceUseCase`, `MmexDateFormat`
- `androidMain` — `DatabaseFactory`, `DatabaseHolder`, repository impls, Drive classes, `SyncCoordinator`, Koin `sharedModule`
- SQLDelight `.sq` files in `src/commonMain/sqldelight/com/konhit/financeapp/db/`

**`:androidApp`** — ViewModels, Compose screens, Koin `appModule`, `FinanceApplication`.

### Data Flow

`SyncCoordinator` → `DatabaseHolder` (singleton holding the open `MmexDatabase`) → repositories → ViewModels → Screens.

### Koin DI

- `sharedModule` — all repositories, Drive classes, `DatabaseFactory/Holder`, `SyncCoordinator`
- `appModule` — all ViewModels
- Started in `FinanceApplication.onCreate()`

### Navigation Routes

`Routes.kt` — `first_launch`, `main`, `settings`, `account/{accountId}`, `transaction?accountId={accountId}&transId={transId}`

## MMEX Database Compatibility (Critical)

The app reads/writes MoneyManagerEx `.mmb` SQLite files. Several constraints must never be violated:

### Never mutate an existing file's schema

`DatabaseFactory.openExisting()` uses `AndroidSqliteDriver(database = sqliteDb, cacheSize = 512)` — the raw-SQLiteDatabase constructor that completely bypasses `SQLiteOpenHelper`. This means **`Schema.create()` is never called on existing files**. Only `createNew()` calls it.

### CHECKINGACCOUNT_V1 insert defaults

Every new transaction row must have exactly:
- `TRANSCODE` — `"Deposit"`, `"Withdrawal"`, or `"Transfer"` (full words, never single letters)
- `STATUS` — `""` (empty string, not NULL, not `"U"`)
- `FOLLOWUPID` — `-1`
- `COLOR` — `-1`
- `TOACCOUNTID` — target account ID for transfers, `-1` for income/expense
- `TOTRANSAMOUNT` — destination amount for transfers, `0` for income/expense
- `TRANSID` — `System.currentTimeMillis() * 1000L` (16-digit microsecond timestamp, MMEX convention)
- `LASTUPDATEDTIME` — `"YYYY-MM-DDTHH:mm:ss"` via `MmexDateFormat.formatLastUpdated()`
- `TRANSDATE` — `"YYYY-MM-DDT00:00:00"` via `MmexDateFormat.formatTransDate()`
- `PAYEEID` — must reference a valid `PAYEE_V1` row; resolved into `DatabaseHolder.defaultPayeeId` on file open

### Balance formula

```
balance = INITIALBAL
        + SUM(TRANSAMOUNT)   where TRANSCODE='Deposit'    AND ACCOUNTID=X
        - SUM(TRANSAMOUNT)   where TRANSCODE='Withdrawal' AND ACCOUNTID=X
        + SUM(TOTRANSAMOUNT) where TRANSCODE='Transfer'   AND TOACCOUNTID=X
        - SUM(TRANSAMOUNT)   where TRANSCODE='Transfer'   AND ACCOUNTID=X
```

Implemented in `Transaction.sq` → `balanceComponents` query, applied by `ComputeBalanceUseCase`.

### INFOTABLE_V1

Read-only. Never write to it after file creation. `DATAVERSION` must stay `3`.

## Sync Architecture

`SyncCoordinator` manages the full Drive sync lifecycle:
- **On open** — download file from Drive, check conflict (remote `modifiedTime > lastSyncTime`), open with `DatabaseFactory`
- **After every write** — `uploadCurrent()` called from each ViewModel mutation
- **On `Activity.onStop()`** — `uploadCurrent()`
- **Conflict** — emits `SyncState.Conflict`; `MainScreen` shows "Keep local / Use remote" dialog

## Test Reference Fixture

`example.mmb` contains 16 accounts, 119 categories, 1728 transactions, 2 payees, 168 currencies, base currency EUR (ID=2).

Key assertions:
- `TC-01` — 16 accounts load, `DATAVERSION=3` unchanged
- `TC-02` — `Raif RSD` (ID=`1748881720210000`) balance ≈ 334 857.57
- `TC-04..TC-06` — insert field defaults match spec exactly
- `TC-10` — 23 tables, 0 triggers, 0 views after any write

Place `example.mmb` in `shared/src/androidTest/assets/` for instrumented tests.

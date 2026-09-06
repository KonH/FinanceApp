# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Versioning

App version is set in `androidApp/build.gradle.kts` → `versionName`. Format: `X.YY` (e.g., `1.00`, `1.01`, `1.02`).
**Bump `versionName` by `0.01` after each completed task or fix before committing.**
The Settings screen reads `BuildConfig.VERSION_NAME` — no other file needs updating.

The desktop app mirrors that version in `desktopApp/package.json` → `version` (Android `1.07` ≙
desktop `1.7.0`). Bump it together with `versionName` whenever the desktop app changes.

## Build & Run

**Always build using the `/build` skill** — invoke it via `Skill("build")`. Never run `gradlew` directly.
The working directory is always the project root — never use `cd` before commands.

`build.ps1` sets `JAVA_HOME` to the Android Studio JBR and writes full output to `.tmp\gradle\log.txt`.
The skill runs `build.ps1` via `powershell -File build.ps1`, then reads the last 30 lines of the log.

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
desktopApp/  — Electron + React desktop client (Windows / macOS), see desktopApp/README.md
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

## Desktop App (`desktopApp/`)

Chromium-based (Electron) client with feature parity to the Android app, on the same `.mmb` files.
Never build it with Gradle — it is an npm project:

```bash
cd desktopApp
npm install
npm start          # build + run
npm run dev        # Vite dev server + Electron
npm test           # domain + real-.mmb tests (node:test, no Electron/device needed)
npm run typecheck  # main process + renderer
npm run package:win  # NSIS installer + portable exe into desktopApp/release
```

Structure:

- `src/shared/` — the Kotlin domain ported to TypeScript (types, MMEX dates, category tree,
  balance, budget, recurrence, amount formatting). Used by both Electron processes.
- `src/main/` — main process: `db/database.ts` (sql.js + `dbHolder`), `repositories.ts` (the SQL of
  the `.sq` files), `settings.ts` (JSON store replacing DataStore), `drive.ts` (OAuth loopback +
  Drive REST), `sync.ts` (`SyncCoordinator` port), `scheduled.ts`, `ipc.ts`, `main.ts`
- `src/preload/` — `contextBridge` with `invoke(channel, …)` plus sync/menu events
- `src/renderer/` — React screens mirroring the Compose screens one-to-one

Rules specific to this module:

- All file and network access lives in the main process; the renderer is sandboxed
  (`contextIsolation: true`, `nodeIntegration: false`) and the built page runs under a strict CSP.
- Every mutation goes through `mutate()` in `ipc.ts`: it enforces read-only mode, writes the
  `.mmb` back to disk and then calls `syncCoordinator.uploadCurrent()`.
- Saving uses `db.export()` (the SQLite image) written to a temp file and renamed — existing
  tables, indexes, triggers, views and `user_version` are never touched.
- When domain rules change on either side, change both: the Kotlin file under `shared/` and its
  port under `desktopApp/src/shared/`.

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

The desktop app covers the same cases in `desktopApp/tests/` (`npm test`): schema creation,
insert defaults, balance formula, schema/`user_version` preservation on write, category tree,
recurrence math and amount formatting. These run on generated `.mmb` files, so no fixture or
device is required.

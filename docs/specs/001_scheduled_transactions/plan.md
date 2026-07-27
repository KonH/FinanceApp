# Plan: Scheduled Transactions

## Spec

`docs/specs/001_scheduled_transactions/spec.md`

## Constitution check

| Rule | How satisfied |
|---|---|
| No schema mutation on existing files | `BillsDeposits.sq` CREATE used only via `createNew()`; `openExisting()` unchanged (no ALTER) |
| No custom tables | Use `BILLSDEPOSITS_V1` |
| Insert defaults | Approve path uses same MMEX contract; `FOLLOWUPID` set to source `BDID` only for scheduled posts |
| INFOTABLE read-only | Untouched |
| Every write → `uploadCurrent()` | Schedule CRUD + approve/cancel/delete call sync |
| Domain in `:shared`, UI in `:androidApp` | Repo/use-case in shared; screens/VMs in androidApp |
| Koin only | Register new types in `sharedModule` / `appModule` |

## Implementation steps

### 1. SQLDelight — `BillsDeposits.sq`

Add under `shared/src/commonMain/sqldelight/.../db/`:

- `CREATE TABLE IF NOT EXISTS BILLSDEPOSITS_V1 (...)` matching MMEX
- Queries: `selectAllOrdered`, `selectById`, `selectDue(today)`, `insert`, `update`, `delete`, `advanceOccurrence`

### 2. Domain (`commonMain`)

- `ScheduledTransaction` model + `RecurrenceMode` / mapping helpers (`repeatsCode`, advance next date, end-date → remaining count)
- `Transaction.fromSchedule: Boolean` (default `false`)
- `ScheduledTransactionRepository` interface
- `ProcessDueScheduledTransactionsUseCase` — list due snapshots for dialogs (pure/query); mutations stay in repo called from VM
- `AdvanceScheduleUseCase` or methods on repo: `approve`, `cancelOnce`, `delete` (approve also inserts via `TransactionRepository`)

Date math: use `java.time` on Android (`androidMain`) or kotlinx-datetime if already present; prefer simple LocalDate in androidMain use-case if commonMain stays free of java.time — put recurrence advance in `androidMain` or `commonMain` with manual Y-M-D math. Prefer `commonMain` pure functions on ISO date strings already used by MMEX.

### 3. Data layer (`androidMain`)

- `ScheduledTransactionRepositoryImpl`
- Extend `Transaction.sq` `insert` to accept `:followupId` (default path passes `-1`; approve passes `BDID`)
- Map `FOLLOWUPID` → `fromSchedule` in `TransactionRepositoryImpl`
- Update instrumented tests that assert insert args if signatures change (still expect `-1` for normal inserts)

### 4. DI

- `sharedModule`: repo + processing helpers
- `appModule`: `ScheduledListViewModel`, `ScheduledEditViewModel`, host for due-dialog state (see below)

### 5. Navigation / Settings

- `Routes.SCHEDULED`, `Routes.SCHEDULED_EDIT` (optional query `bdId`)
- Settings Data section: ListItem “Scheduled transactions”
- `AppNavGraph` composables

### 6. UI screens

- `ScheduledListScreen` — ordered list, add FAB, edit/delete
- `ScheduledEditScreen` — form per spec (reuse category picker / account patterns from `TransactionScreen`)
- `ScheduledDueDialog` — AlertDialog or modal; fixed summary + three actions

### 7. Due-check host

- Hold queue in a small `ScheduledDueViewModel` (activity-scoped / single) or `MainActivity` + shared coordinator
- Call after `openFile()` success on cold start
- `MainActivity.onStart`: if DB open && RW && not already showing, refresh due queue
- Present dialogs above `AppNavGraph` when queue non-empty
- Debounce: skip re-entrant checks while a dialog is active; after dismiss action, peek next

### 8. Marker UI

- `AccountScreen` / `FilterScreen` row trailing or leading icon when `fromSchedule`
- `TransactionScreen`: read-only chip/text “From schedule” when editing such a tx

### 9. Version

- Bump `androidApp/build.gradle.kts` `versionName` by `0.01`

### 10. Tests

- Unit: recurrence advance (daily/weekly/monthly/once/every-X), end-date remaining count, `repeats % 100` parsing
- Instrumented (if fixture has bills): list loads; approve insert defaults + FOLLOWUPID; cancel advances; no DDL (TC-10 still green)
- Update TC-04..06 if insert signature changes (FOLLOWUPID still `-1`)

## File touch list (expected)

```
docs/specs/001_scheduled_transactions/spec.md
docs/specs/001_scheduled_transactions/plan.md
shared/.../db/BillsDeposits.sq
shared/.../db/Transaction.sq          # insert followupId param
shared/.../domain/model/ScheduledTransaction.kt
shared/.../domain/model/Transaction.kt
shared/.../domain/model/Recurrence.kt
shared/.../domain/repository/ScheduledTransactionRepository.kt
shared/.../data/repository/ScheduledTransactionRepositoryImpl.kt
shared/.../data/repository/TransactionRepositoryImpl.kt
shared/.../domain/usecase/ProcessDueScheduledUseCase.kt (or similar)
shared/.../di/SharedModule.kt
androidApp/.../navigation/Routes.kt
androidApp/.../navigation/AppNavGraph.kt
androidApp/.../MainActivity.kt
androidApp/.../settings/SettingsScreen.kt
androidApp/.../scheduled/*            # new package
androidApp/.../account/AccountScreen.kt
androidApp/.../filter/FilterScreen.kt
androidApp/.../transaction/TransactionScreen.kt
androidApp/.../di/AppModule.kt
androidApp/build.gradle.kts
```

## Risks

- `EVERY_X_*` vs end date / `NUMOCCURRENCES` dual use — handled per spec (`TRANSACTIONNUMBER` end-date tag when needed)
- Foreground spam if `onStart` fires often — only enqueue when due set non-empty and no active dialog; compare occurrence keys already dismissed this session for cancel-once already advanced
- SQLDelight `SELECT *` on bills requires table present — `example.mmb` already has full MMEX schema (23 tables); new empty files get table from `createNew`

## Build

Use `/build` skill (`assembleDebug`) after implementation; do not invoke `gradlew` directly.

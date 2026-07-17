# Project Constitution

Non-negotiable architectural principles. The `plan` skill checks these before finalising any plan.

## Versioning

- **Bump `versionName` by `0.01` per completed task.** `androidApp/build.gradle.kts` → `versionName`, format `X.YY`; do this before committing. The Settings screen reads it via `BuildConfig.VERSION_NAME` — no other file needs updating.

## Build Discipline

- **Build only through the `build` skill.** Never invoke `gradlew` directly; the skill wraps `build.ps1`, sets `JAVA_HOME` to the Android Studio JBR, and logs to `.tmp\gradle\log.txt`.

## MMEX Database Compatibility

- **Never mutate an existing `.mmb` file's schema.** `DatabaseFactory.openExisting()` uses the raw-`SQLiteDatabase` driver constructor specifically so `Schema.create()` is never invoked on an existing file; only `createNew()` may call it.
- **New transaction rows must match the MMEX insert-default contract exactly** — `TRANSCODE` full words (`Deposit`/`Withdrawal`/`Transfer`), `STATUS` = `""`, `FOLLOWUPID`/`COLOR` = `-1`, transfer-aware `TOACCOUNTID`/`TOTRANSAMOUNT`, `TRANSID` = `System.currentTimeMillis() * 1000L`, MMEX-formatted `LASTUPDATEDTIME`/`TRANSDATE`, and a `PAYEEID` resolved from `DatabaseHolder.defaultPayeeId`. No ad hoc insert paths that skip these fields.
- **Balance is always derived via the documented formula** (`INITIALBAL` + deposits − withdrawals + incoming transfers − outgoing transfers), implemented once in `Transaction.sq` → `balanceComponents` and applied through `ComputeBalanceUseCase`. No parallel balance-computation logic.
- **`INFOTABLE_V1` is read-only after file creation.** `DATAVERSION` must never change from `3`.

## Module Boundaries

- **Domain logic lives in `:shared`, UI/DI wiring lives in `:androidApp`.** Repository interfaces, `ComputeBalanceUseCase`, `MmexDateFormat`, and other domain rules belong in `commonMain`; Android-specific implementations (drivers, Drive classes, `SyncCoordinator`) belong in `androidMain`. ViewModels and Compose screens stay in `:androidApp`.
- **Koin is the sole DI mechanism.** All repositories, Drive classes, and `SyncCoordinator` register in `sharedModule`; all ViewModels register in `appModule`. No manual construction of these types outside Koin.

## Sync Architecture

- **Every write triggers `uploadCurrent()`.** Each ViewModel mutation that touches the database calls `SyncCoordinator.uploadCurrent()`, in addition to the `onStop()` upload. No silent local-only writes.
- **Conflicts are surfaced, never auto-resolved.** A remote `modifiedTime` newer than `lastSyncTime` emits `SyncState.Conflict` and must be resolved via the "Keep local / Use remote" dialog — no automatic pick of either side.

## Planning Discipline

- **Plan before implement.** No non-trivial code change without an approved plan file under `docs/specs/<index>_<name>/plan.md`; this keeps the git history reviewable and matches the `specify` → `plan` → `implement` workflow.
- **Spec before plan for feature work.** New user-facing functionality starts with a `specify` pass capturing intent and acceptance criteria; purely technical tasks (refactors, dependency bumps, test-only changes) may skip the spec and go straight to `plan`.

## File Organisation

- **`docs/specs/<index>_<name>/` for all plans and specs.** The numeric index is shared across spec and plan folders and always increments; no two entries share a prefix.

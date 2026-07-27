# Spec: Scheduled Transactions

## Goal

Let users define recurring/one-shot scheduled transactions in MMEX-compatible `.mmb` files, approve or skip them when due, and see a marker on ledger transactions created from a schedule.

## Decisions (from clarification)

| # | Topic | Decision |
|---|---|---|
| 1 | Settings UX | Dedicated screen, linked from Settings (same pattern as Accounts/Categories) |
| 2 | Ledger marker | Boolean `fromSchedule` on posted transactions; UI shows marker in list + transaction view |
| 3 | Types | Deposit, Withdrawal, **Transfer** |
| 4 | Overdue | Process **all** missed occurrences (one dialog after another) |
| 5 | When to check | Cold start **and** return to foreground |
| 6 | Multi weekday | MMEX-friendly: **one schedule per weekday** (no multi-select in one row) |
| 7 | End | Optional end date; plus Once mode |
| 8 | Approve dialog | Fixed fields (no edit before insert) |
| 9 | Access mode | Skip processing entirely in read-only; CRUD only in read-write |
| 10 | Existing data | Read/process schedules already present in `.mmb` from desktop MMEX |

## Marker persistence (constraint)

Constitution forbids mutating an existing `.mmb` schema (`ALTER TABLE` / new columns on open). A new physical SQLite column on `CHECKINGACCOUNT_V1` is therefore **not** used for existing files.

**Encoding of the boolean marker:**

- Domain: `Transaction.fromSchedule: Boolean`
- DB: on approve, insert with `FOLLOWUPID = <source BDID>` (instead of `-1`)
- `fromSchedule = (followupId != -1)`
- Normal manual inserts keep `FOLLOWUPID = -1`
- UI: optional marker icon/column when `fromSchedule == true` on account list, filter list, and transaction screen

## Storage

Use MMEX table `BILLSDEPOSITS_V1` (no custom tables).

Key fields:

- `BDID`, `ACCOUNTID`, `TOACCOUNTID`, `PAYEEID`, `TRANSCODE`, `TRANSAMOUNT`, `TOTRANSAMOUNT`
- `CATEGID`, `NOTES`, `STATUS`, `FOLLOWUPID`, `COLOR`
- `TRANSDATE` (due-related; keep in sync with next occurrence on write)
- `NEXTOCCURRENCEDATE` (next due / payment date — sort & trigger key)
- `REPEATS` — recurrence type (`value % 100`) + optional auto mode bits (`/ 100`); app always prompts, ignores auto bits for behaviour
- `NUMOCCURRENCES` — remaining payments (`-1` = infinite); for `EVERY_X_*` types this is the interval X (MMEX convention)

Include `BILLSDEPOSITS_V1` in `Schema.create()` via SQLDelight so **new** files support schedules. Never DDL on `openExisting()`.

## Recurrence UI → MMEX `REPEATS`

| UI mode | `REPEATS % 100` | Notes |
|---|---|---|
| Once | `0` ONCE | Deleted after approve or cancel-once |
| Daily | `10` DAILY | |
| Monthly | `3` MONTHLY | |
| Day of week | `1` WEEKLY | Next date anchored to chosen weekday |
| Custom: every N weeks | `1`/`2`/`9` or `13` EVERY_X_DAYS | Prefer WEEKLY/BIWEEKLY/FOUR_WEEKS for N=1/2/4; else `EVERY_X_DAYS` with `NUMOCCURRENCES = N*7` |
| Custom: every N months | `3`/`4`/`5`/`6`/`8`/`14` | Prefer fixed codes when N matches; else `EVERY_X_MONTHS` |
| Custom: every N years | `7` or `14` | N=1 → ANNUALLY; else `EVERY_X_MONTHS` with `N*12` |

Optional end date (when `NUMOCCURRENCES` means remaining, not interval):

- On save, compute remaining occurrence count from next date through end date inclusive → store as `NUMOCCURRENCES`
- If no end date → `NUMOCCURRENCES = -1` (except Once)
- For `EVERY_X_*` where `NUMOCCURRENCES` is the interval: end date is applied by deleting the schedule when the advanced next date would exceed the end date; interval stays in `NUMOCCURRENCES`, end date kept in `TRANSACTIONNUMBER` as `END:YYYY-MM-DD` when set (ignored by MMEX desktop)

## Functional requirements

### Settings → Scheduled transactions screen

- List ordered by `NEXTOCCURRENCEDATE` ascending
- Row: next date, account, category, amount (signed/type-coloured), edit + delete
- Add button → editor
- Read-only: list visible, no add/edit/delete

### Editor (add/edit)

- Next date
- Mode: Once | Daily | Monthly | Day of week | Custom
- Custom controls: every N + unit (week | month | year); weekday comes from next date (MMEX-friendly)
- Account; for Transfer also To account + destination amount
- Category
- Type: Deposit / Withdrawal / Transfer
- Amount
- Description (`NOTES`)
- Optional end date (hidden or disabled for Once)
- Save / Cancel

### Due processing (RW only)

Triggers: after successful file open on cold start; again when activity returns to foreground (`onStart`), if DB is open.

For each due occurrence (`NEXTOCCURRENCEDATE date part <= today`), queue dialogs oldest-first:

Dialog shows fixed info (date, account, category, type, amount, notes) and:

| Action | Effect |
|---|---|
| **Approve** | Insert `CHECKINGACCOUNT_V1` with MMEX defaults + `FOLLOWUPID=BDID`; advance or delete schedule; `uploadCurrent()` |
| **Cancel once** | Advance next date only (or delete if Once / last occurrence); do not insert; `uploadCurrent()` |
| **Delete scheduled** | Delete `BILLSDEPOSITS_V1` row; `uploadCurrent()` |

Catch-up: after approve/cancel-once, if the same schedule is still due, show it again before moving to other schedules (all missing periods).

Posted transaction `TRANSDATE` = the occurrence’s due date (fixed).

### Marker

- Account transaction list + filter list: small schedule icon when `fromSchedule`
- Transaction screen (edit existing): show “Scheduled” marker when `fromSchedule`

## Out of scope

- Push notifications / background alarm
- Editing amount/date in the due dialog
- Multi-weekday single schedule
- Split scheduled transactions
- Auto-enter without prompt (MMEX auto mode bits ignored for behaviour)

## Acceptance criteria

1. Settings has a row opening a dedicated Scheduled transactions screen.
2. CRUD works against `BILLSDEPOSITS_V1`; existing MMEX schedules appear.
3. Cold start and foreground return in RW mode prompt for every overdue occurrence.
4. Approve inserts a correct ledger row, syncs, advances/deletes schedule, and marks `fromSchedule`.
5. Cancel once skips without ledger row and does not re-prompt until the new next date.
6. Read-only: no due dialogs; no schedule mutations.
7. Transfer schedules supported.
8. `example.mmb` / existing files: no schema mutation; TC-10 still holds (23 tables, etc.).
9. `versionName` bumped by `0.01` for the implementation commit.

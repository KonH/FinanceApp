# FinanceApp Desktop

Chromium-based desktop client (Electron) for MoneyManagerEx `.mmb` files — the desktop twin of the
FinanceApp Android app. Same file format, same balance rules, same screens, no server.

| | |
|---|---|
| Shell | Electron 44 (Chromium 152) |
| UI | React 19 + TypeScript, hand-rolled Material 3 theme (light/dark follows the OS) |
| Database | SQLite compiled to WebAssembly (`sql.js`) over the raw `.mmb` file |
| Packaging | electron-builder — Windows NSIS installer + portable exe; macOS dmg/zip (optional) |
| Runtime deps | `sql.js` only |

---

## Run it

```bash
cd desktopApp
npm install

npm run dev        # Vite dev server + Electron with hot reload of the renderer
npm start          # build once, then run Electron against the built files
npm test           # domain + MMEX database tests (node:test, no Electron needed)
npm run typecheck  # tsc for main process and renderer
```

Packaging (needs the platform's toolchain; run each on its own OS):

```bash
npm run package:win   # release/FinanceApp Setup <version>.exe + release/FinanceApp.exe
npm run package:mac   # release/*.dmg and *.zip (x64 + arm64) — build on macOS
```

The portable build is always `release/FinanceApp.exe` — no version in the name — so a desktop
shortcut to it survives updates. It unpacks to a fixed directory (`FinanceApp`) instead of a
per-version temp folder, and the version is shown in Settings → About.

> **Windows note.** electron-builder unpacks its `winCodeSign` cache with symlinks, which Windows
> only allows for administrators or with **Developer Mode** enabled; without it the build stops
> after `release/win-unpacked` with *"Cannot create symbolic link"*. Enable Developer Mode once, or
> use `npm run package:win:unsigned`, which skips the signing/rcedit step (the installer is built,
> but the exe keeps Electron's default icon and metadata).

The app registers the `.mmb` extension, so a double-clicked file opens in the running instance.

## What it does

Feature parity with the Android app:

- **First launch** — create a new `.mmb`, open a local one, or pick one from Google Drive
- **Accounts** — grouped by type, per-currency group totals and grand total, balance masking,
  hidden accounts, budget chip, sync state
- **Account ledger** — add / edit / delete transactions, balance as of any date
- **Transaction form** — deposit / withdrawal / transfer, category tree picker with search,
  inline calculator, destination amount mirrored for same-currency transfers, zero-destination guard
- **Filter** — free-text search plus type, account, currency, category, date range and amount range,
  with a per-currency net-flow summary
- **Scheduled transactions** — the MMEX `BILLSDEPOSITS_V1` rules, a due prompt on start
  (approve / cancel once / delete), and the full recurrence editor
- **Settings** — Drive connection, file path and access mode, accounts / categories / currencies /
  schedules, default category per transaction type, monthly budget per currency
- **Sync** — upload after every write and on quit, conflict dialog ("keep local" / "use remote")

## How the file is handled

`.mmb` compatibility rules are identical to the Android app (see `CLAUDE.md` at the repo root):

- **Existing files are never migrated.** The file is loaded into SQLite as-is; no `CREATE TABLE`,
  no `PRAGMA user_version` write. Saving calls `db.export()`, which returns the SQLite image
  itself, so unknown tables, indexes, triggers, views and `INFOTABLE_V1` all survive a write.
  A file that is not a MoneyManagerEx database is refused before anything is written.
- **Schema creation happens only for new files**, from the same DDL as the SQLDelight `.sq` files.
- **Insert defaults** match the spec exactly: full-word `TRANSCODE`, `STATUS = ''`,
  `FOLLOWUPID = -1` (or the source `BDID`), `COLOR = -1`, microsecond `TRANSID`,
  `TRANSDATE = YYYY-MM-DDT00:00:00`, `LASTUPDATEDTIME = YYYY-MM-DDTHH:mm:ss`, and a `PAYEEID`
  taken from the file's first `PAYEE_V1` row.
- **Balance formula** is the shared one: `INITIALBAL + deposits − withdrawals + transfers in − transfers out`.
- Writes are atomic: the image is written to a temp file next to the target and renamed over it.

## Google Drive

Android signs in through Play Services, which does not exist on desktop, so the desktop app uses the
standard OAuth 2.0 *installed app* flow (loopback redirect + PKCE) with **your own** OAuth client:

1. Google Cloud Console → create a project → enable the **Google Drive API**
2. Credentials → *Create credentials* → *OAuth client ID* → **Desktop app**
3. In FinanceApp: Settings → Google Drive → *Set up*, paste the client id and secret
4. *Connect* opens the consent screen in your browser; the app captures the code on `127.0.0.1`

Client id, secret and refresh token are stored in `settings.json` in the Electron user-data
directory (`%APPDATA%\FinanceApp` on Windows, `~/Library/Application Support/FinanceApp` on macOS) —
never inside the `.mmb`. Drive-backed files are cached at `<user data>/drive-cache/current.mmb`,
exactly like the Android cache copy.

## Layout

```
src/shared/      domain ported from :shared commonMain — types, dates, category tree,
                 balance, budget, recurrence, amount formatting (used by both processes)
src/main/        Electron main process: sql.js database + holder, repositories (the SQL of the
                 .sq files), settings store, Drive client, sync coordinator, scheduled use case,
                 IPC surface, window/menu
src/preload/     contextBridge: invoke(channel, …) + sync/menu event subscriptions
src/renderer/    React UI: store, screens and components mirroring the Compose screens
tests/           node:test suites for the domain and for real .mmb files
resources/       app icon (regenerate with `node resources/make-icon.cjs`)
```

The renderer is sandboxed (`contextIsolation: true`, `nodeIntegration: false`) and the built page
runs under a strict CSP; every file and network operation happens in the main process.

## Differences from the Android app

Deliberate, documented deviations:

| Area | Desktop behaviour | Why |
|---|---|---|
| Read-only mode | Blocks **all** writes, including accounts, categories and currencies | On Android read-only only guards transactions and schedules |
| "Use latest category" | Actually applied when adding a transaction | The Android setting is stored but never read |
| New file | Seeds three currencies (EUR, USD, GBP) | A file created from scratch has no currency rows at all otherwise, so accounts cannot pick one |
| Category / currency deletion | Refused when still referenced (transactions, sub-categories, accounts) | Prevents dangling MMEX ids |
| Drive sign-in | User-supplied desktop OAuth client | Play Services sign-in is Android-only |
| Balance visibility | Session-scoped toggle | Same as Android (`BalanceVisibilityStore`) |

## Versioning

`package.json` → `version` tracks the Android `versionName`: Android `1.07` ≙ desktop `1.7.0`.
Bump both when finishing a task that touches either app.

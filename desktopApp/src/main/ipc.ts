import fs from 'node:fs';
import path from 'node:path';
import { BrowserWindow, app, dialog, ipcMain, shell } from 'electron';
import { MmexDatabase, dbHolder } from './db/database';
import * as drive from './drive';
import { accountRepo, categoryRepo, currencyRepo, infoRepo, scheduledRepo, transactionRepo } from './repositories';
import * as scheduled from './scheduled';
import { settingsStore } from './settings';
import { syncCoordinator } from './sync';
import { descendantIds } from '../shared/category';
import type {
  Account,
  AccessMode,
  AppSettings,
  Category,
  Currency,
  DriveFileEntry,
  FileInfo,
  ScheduledTransaction,
  Transaction,
  TransactionType
} from '../shared/types';

export interface Snapshot {
  dbOpen: boolean;
  file: FileInfo;
  accounts: Account[];
  categories: Category[];
  currencies: Currency[];
  settings: AppSettings;
  dataVersion: string | null;
}

export interface Result<T = void> {
  ok: boolean;
  error?: string;
  value?: T;
}

const ok = <T>(value?: T): Result<T> => ({ ok: true, value });
const fail = (error: unknown): Result<never> => ({
  ok: false,
  error: error instanceof Error ? error.message : String(error)
});

function requireWritable(): void {
  if (!dbHolder.isOpen) throw new Error('No database open');
  if (settingsStore.isReadOnly) throw new Error('Cannot write in read-only mode');
}

/**
 * Runs a mutation, writes the .mmb back to disk and pushes it to Drive —
 * the desktop equivalent of "ViewModel writes, then `syncCoordinator.uploadCurrent()`".
 */
async function mutate<T>(action: () => T): Promise<Result<T>> {
  try {
    requireWritable();
    const value = action();
    dbHolder.require().save();
    syncCoordinator.markPendingIfDriveBacked();
    await syncCoordinator.uploadCurrent();
    return ok(value);
  } catch (error) {
    return fail(error);
  }
}

function fileInfo(): FileInfo {
  const settings = settingsStore.all;
  return {
    path: dbHolder.filePath,
    sourceType: settings.driveFileId ? 'Google Drive' : 'Local',
    driveFileId: settings.driveFileId,
    accessMode: settings.accessMode,
    lastSyncDisplay: settings.lastSyncTime
      ? new Date(settings.lastSyncTime).toLocaleString()
      : 'Never'
  };
}

function snapshot(): Snapshot {
  if (!dbHolder.isOpen) {
    return {
      dbOpen: false,
      file: fileInfo(),
      accounts: [],
      categories: [],
      currencies: [],
      settings: settingsStore.all,
      dataVersion: null
    };
  }
  return {
    dbOpen: true,
    file: fileInfo(),
    accounts: accountRepo.getAll(),
    categories: categoryRepo.getAll(),
    currencies: currencyRepo.getAll(),
    settings: settingsStore.all,
    dataVersion: infoRepo.get('DATAVERSION')
  };
}

function newId(): number {
  return Date.now() * 1000;
}

/**
 * The app's own version. `app.getVersion()` falls back to Electron's version
 * when the process was started with a script instead of the app directory.
 */
function appVersion(): string {
  try {
    const pkg = JSON.parse(fs.readFileSync(path.join(app.getAppPath(), 'package.json'), 'utf8')) as {
      version?: string;
    };
    return pkg.version ?? app.getVersion();
  } catch {
    return app.getVersion();
  }
}

async function openLocalPath(filePath: string): Promise<void> {
  settingsStore.patch({ driveFileId: null, lastSyncTime: null, pendingUpload: false });
  await syncCoordinator.openLocal(filePath);
  syncCoordinator.reset();
}

/** Registers every IPC handler. Channel names are the renderer's whole API surface. */
export function registerIpcHandlers(getWindow: () => BrowserWindow | null): void {
  const handlers: Record<string, (...args: never[]) => unknown> = {
    // ------------------------------------------------------------- app/file
    'app:info': () => ({
      version: appVersion(),
      platform: process.platform,
      electron: process.versions.electron,
      chrome: process.versions.chrome
    }),

    'file:info': () => fileInfo(),

    'data:snapshot': () => snapshot(),

    'file:createNew': async (): Promise<Result<string>> => {
      try {
        const window = getWindow();
        const result = await dialog.showSaveDialog(window!, {
          title: 'Create MoneyManagerEx file',
          defaultPath: path.join(app.getPath('documents'), 'finance.mmb'),
          filters: [{ name: 'MoneyManagerEx database', extensions: ['mmb'] }]
        });
        if (result.canceled || !result.filePath) return ok('');
        let target = result.filePath;
        if (!target.toLowerCase().endsWith('.mmb')) target += '.mmb';
        if (fs.existsSync(target)) fs.rmSync(target);
        const db = await MmexDatabase.createNew(target);
        dbHolder.open(db);
        settingsStore.patch({
          localFilePath: target,
          driveFileId: null,
          lastSyncTime: null,
          pendingUpload: false
        });
        syncCoordinator.reset();
        return ok(target);
      } catch (error) {
        return fail(error);
      }
    },

    'file:openDialog': async (): Promise<Result<string>> => {
      try {
        const window = getWindow();
        const result = await dialog.showOpenDialog(window!, {
          title: 'Open MoneyManagerEx file',
          properties: ['openFile'],
          filters: [
            { name: 'MoneyManagerEx database', extensions: ['mmb'] },
            { name: 'All files', extensions: ['*'] }
          ]
        });
        if (result.canceled || result.filePaths.length === 0) return ok('');
        await openLocalPath(result.filePaths[0]);
        return ok(result.filePaths[0]);
      } catch (error) {
        return fail(error);
      }
    },

    'file:openPath': async (...args: never[]): Promise<Result<string>> => {
      const filePath = args[0] as unknown as string;
      try {
        await openLocalPath(filePath);
        return ok(filePath);
      } catch (error) {
        return fail(error);
      }
    },

    'file:close': (): Result => {
      dbHolder.close();
      settingsStore.patch({ localFilePath: null, driveFileId: null, pendingUpload: false });
      syncCoordinator.reset();
      return ok();
    },

    'file:showInFolder': (): Result => {
      const current = dbHolder.filePath;
      if (current) shell.showItemInFolder(current);
      return ok();
    },

    // ------------------------------------------------------------- accounts
    'accounts:balanceAtDate': (...args: never[]) => {
      const [id, date] = args as unknown as [number, string];
      return accountRepo.getBalanceAtDate(id, date);
    },

    'accounts:save': (...args: never[]) => {
      const [account, isNew] = args as unknown as [Account, boolean];
      return mutate(() => {
        const row: Account = { ...account, id: isNew ? newId() : account.id };
        if (isNew) accountRepo.insert(row);
        else accountRepo.update(row);
      });
    },

    'accounts:delete': (...args: never[]) => {
      const [id] = args as unknown as [number];
      return mutate(() => accountRepo.delete(id));
    },

    'accounts:toggleHidden': (...args: never[]): Result => {
      const [id] = args as unknown as [number];
      settingsStore.toggleHiddenAccount(id);
      return ok();
    },

    // ----------------------------------------------------------- categories
    'categories:save': (...args: never[]) => {
      const [category, isNew] = args as unknown as [Category, boolean];
      return mutate(() => {
        if (isNew) {
          categoryRepo.insert({ ...category, id: newId() });
          return;
        }
        const all = categoryRepo.getAll();
        if (descendantIds(all, category.id).has(category.parentId)) {
          throw new Error('A category cannot be moved inside itself');
        }
        categoryRepo.update(category);
      });
    },

    'categories:delete': (...args: never[]) => {
      const [id] = args as unknown as [number];
      return mutate(() => {
        if (categoryRepo.hasTransactions(id)) throw new Error('Category is used by transactions');
        if (categoryRepo.hasChildren(id)) throw new Error('Category has sub-categories');
        categoryRepo.delete(id);
      });
    },

    // ----------------------------------------------------------- currencies
    'currencies:save': (...args: never[]) => {
      const [currency, isNew] = args as unknown as [Currency, boolean];
      return mutate(() => {
        if (isNew) currencyRepo.insert({ ...currency, id: newId() });
        else currencyRepo.update(currency);
      });
    },

    'currencies:delete': (...args: never[]) => {
      const [id] = args as unknown as [number];
      return mutate(() => {
        if (currencyRepo.isUsedByAccount(id)) throw new Error('Currency is used by an account');
        currencyRepo.delete(id);
      });
    },

    // --------------------------------------------------------- transactions
    'transactions:byAccount': (...args: never[]) => {
      const [accountId] = args as unknown as [number];
      return transactionRepo.getByAccount(accountId);
    },

    'transactions:all': () => transactionRepo.getAll(),

    'transactions:get': (...args: never[]) => {
      const [transId] = args as unknown as [number];
      return transactionRepo.getById(transId);
    },

    'transactions:save': (...args: never[]) => {
      const [tx, isNew] = args as unknown as [Transaction, boolean];
      return mutate(() => {
        const row: Transaction = {
          ...tx,
          transId: isNew ? newId() : tx.transId,
          payeeId: dbHolder.defaultPayeeId
        };
        if (isNew) transactionRepo.insert(row);
        else transactionRepo.update(row);
      });
    },

    'transactions:delete': (...args: never[]) => {
      const [transId] = args as unknown as [number];
      return mutate(() => transactionRepo.delete(transId));
    },

    'transactions:expensesByMonth': (...args: never[]) => {
      const [yearMonth] = args as unknown as [string];
      return transactionRepo.getExpensesByCurrencyForMonth(yearMonth);
    },

    'transactions:latestCategory': (...args: never[]) => {
      const [type] = args as unknown as [TransactionType];
      return transactionRepo.getLatestCategoryId(type);
    },

    // ------------------------------------------------------------ scheduled
    'scheduled:list': () => scheduledRepo.getAllOrdered(),

    'scheduled:get': (...args: never[]) => {
      const [bdId] = args as unknown as [number];
      return scheduledRepo.getById(bdId);
    },

    'scheduled:save': (...args: never[]) => {
      const [item, isNew] = args as unknown as [ScheduledTransaction, boolean];
      return mutate(() => {
        const row: ScheduledTransaction = {
          ...item,
          bdId: isNew ? newId() : item.bdId,
          payeeId: dbHolder.defaultPayeeId
        };
        if (isNew) scheduledRepo.insert(row);
        else scheduledRepo.update(row);
      });
    },

    'scheduled:delete': (...args: never[]) => {
      const [bdId] = args as unknown as [number];
      return mutate(() => scheduledRepo.delete(bdId));
    },

    'scheduled:due': () => scheduled.listDueToday(),

    'scheduled:approve': (...args: never[]) => {
      const [bdId] = args as unknown as [number];
      return mutate(() => {
        const item = scheduledRepo.getById(bdId);
        if (!item) throw new Error('Scheduled transaction not found');
        scheduled.approve(item);
      });
    },

    'scheduled:cancelOnce': (...args: never[]) => {
      const [bdId] = args as unknown as [number];
      return mutate(() => {
        const item = scheduledRepo.getById(bdId);
        if (!item) throw new Error('Scheduled transaction not found');
        scheduled.cancelOnce(item);
      });
    },

    'scheduled:deleteDue': (...args: never[]) => {
      const [bdId] = args as unknown as [number];
      return mutate(() => {
        const item = scheduledRepo.getById(bdId);
        if (!item) throw new Error('Scheduled transaction not found');
        scheduled.deleteScheduled(item);
      });
    },

    // ------------------------------------------------------------- settings
    'settings:get': () => settingsStore.all,

    'settings:setAccessMode': (...args: never[]): Result => {
      const [mode] = args as unknown as [AccessMode];
      settingsStore.patch({ accessMode: mode });
      return ok();
    },

    'settings:setDefaultCategory': (...args: never[]): Result => {
      const [type, id] = args as unknown as [TransactionType, number | null];
      settingsStore.setDefaultCategoryId(type, id);
      return ok();
    },

    'settings:setUseLatestCategory': (...args: never[]): Result => {
      const [type, enabled] = args as unknown as [TransactionType, boolean];
      settingsStore.setUseLatestCategory(type, enabled);
      return ok();
    },

    'settings:setBudget': (...args: never[]): Result => {
      const [currencyId, amount] = args as unknown as [number, number | null];
      settingsStore.setBudget(currencyId, amount);
      return ok();
    },

    // ----------------------------------------------------------------- sync
    'sync:state': () => syncCoordinator.current,

    'sync:upload': async (): Promise<Result> => {
      try {
        await syncCoordinator.uploadCurrent();
        return ok();
      } catch (error) {
        return fail(error);
      }
    },

    'sync:keepLocal': async (): Promise<Result> => {
      try {
        await syncCoordinator.resolveConflictKeepLocal();
        return ok();
      } catch (error) {
        return fail(error);
      }
    },

    'sync:keepRemote': async (): Promise<Result> => {
      try {
        const fileId = settingsStore.all.driveFileId;
        if (!fileId) return fail(new Error('No Drive file configured'));
        await syncCoordinator.resolveConflictKeepRemote(fileId);
        return ok();
      } catch (error) {
        return fail(error);
      }
    },

    // ---------------------------------------------------------------- drive
    'drive:status': () => drive.driveStatus(),

    'drive:setClient': (...args: never[]): Result => {
      const [clientId, clientSecret] = args as unknown as [string, string];
      drive.setClientCredentials(clientId, clientSecret);
      return ok();
    },

    'drive:connect': async (): Promise<Result> => {
      try {
        await drive.connect();
        return ok();
      } catch (error) {
        return fail(error);
      }
    },

    'drive:disconnect': (): Result => {
      drive.disconnect();
      return ok();
    },

    'drive:listFiles': async (): Promise<Result<DriveFileEntry[]>> => {
      try {
        return ok(await drive.listMmbFiles());
      } catch (error) {
        return fail(error);
      }
    },

    'drive:open': async (...args: never[]): Promise<Result> => {
      const [fileId] = args as unknown as [string];
      try {
        settingsStore.patch({ driveFileId: fileId, lastSyncTime: null, pendingUpload: false });
        await syncCoordinator.downloadAndOpen(fileId);
        settingsStore.patch({ localFilePath: dbHolder.filePath });
        return ok();
      } catch (error) {
        return fail(error);
      }
    }
  };

  for (const [channel, handler] of Object.entries(handlers)) {
    ipcMain.handle(channel, (_event, ...args) => (handler as (...a: unknown[]) => unknown)(...args));
  }
}

/** A .mmb passed on the command line (or via the macOS "open-file" event). */
let pendingOpenPath: string | null = null;

export function setPendingOpenPath(filePath: string | null): void {
  pendingOpenPath = filePath;
}

/** Re-opens the file recorded in settings, mirroring `OpenFileUseCase`. */
export async function reopenLastFile(): Promise<boolean> {
  const settings = settingsStore.all;
  if (pendingOpenPath && fs.existsSync(pendingOpenPath)) {
    const requested = pendingOpenPath;
    pendingOpenPath = null;
    try {
      await openLocalPath(requested);
      return true;
    } catch {
      // Fall through to whatever was open last time.
    }
  }
  try {
    if (settings.driveFileId && drive.isSignedIn()) {
      await syncCoordinator.downloadAndOpen(settings.driveFileId);
      return dbHolder.isOpen;
    }
    if (settings.localFilePath && fs.existsSync(settings.localFilePath)) {
      await syncCoordinator.openLocal(settings.localFilePath);
      return true;
    }
  } catch {
    return false;
  }
  return false;
}

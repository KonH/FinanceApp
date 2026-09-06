import type {
  Account,
  AccessMode,
  AppSettings,
  Category,
  Currency,
  DriveFileEntry,
  DriveStatus,
  FileInfo,
  ScheduledTransaction,
  SyncState,
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

export interface AppInfo {
  version: string;
  platform: string;
  electron: string;
  chrome: string;
}

interface Bridge {
  invoke(channel: string, ...args: unknown[]): Promise<unknown>;
  onSyncChanged(listener: (state: SyncState) => void): () => void;
  onMenuCommand(listener: (command: string) => void): () => void;
}

declare global {
  interface Window {
    financeApp: Bridge;
  }
}

const bridge = (): Bridge => window.financeApp;

const invoke = <T>(channel: string, ...args: unknown[]): Promise<T> =>
  bridge().invoke(channel, ...args) as Promise<T>;

export const api = {
  onSyncChanged: (listener: (state: SyncState) => void) => bridge().onSyncChanged(listener),
  onMenuCommand: (listener: (command: string) => void) => bridge().onMenuCommand(listener),

  appInfo: () => invoke<AppInfo>('app:info'),
  bootstrap: () => invoke<{ opened: boolean }>('app:bootstrap'),
  snapshot: () => invoke<Snapshot>('data:snapshot'),
  fileInfo: () => invoke<FileInfo>('file:info'),

  createFile: () => invoke<Result<string>>('file:createNew'),
  openFileDialog: () => invoke<Result<string>>('file:openDialog'),
  openFilePath: (filePath: string) => invoke<Result<string>>('file:openPath', filePath),
  closeFile: () => invoke<Result>('file:close'),
  showInFolder: () => invoke<Result>('file:showInFolder'),

  balanceAtDate: (accountId: number, date: string) =>
    invoke<number>('accounts:balanceAtDate', accountId, date),
  saveAccount: (account: Account, isNew: boolean) => invoke<Result>('accounts:save', account, isNew),
  deleteAccount: (id: number) => invoke<Result>('accounts:delete', id),
  toggleHiddenAccount: (id: number) => invoke<Result>('accounts:toggleHidden', id),

  saveCategory: (category: Category, isNew: boolean) => invoke<Result>('categories:save', category, isNew),
  deleteCategory: (id: number) => invoke<Result>('categories:delete', id),

  saveCurrency: (currency: Currency, isNew: boolean) => invoke<Result>('currencies:save', currency, isNew),
  deleteCurrency: (id: number) => invoke<Result>('currencies:delete', id),

  transactionsByAccount: (accountId: number) => invoke<Transaction[]>('transactions:byAccount', accountId),
  allTransactions: () => invoke<Transaction[]>('transactions:all'),
  getTransaction: (transId: number) => invoke<Transaction | null>('transactions:get', transId),
  saveTransaction: (tx: Transaction, isNew: boolean) => invoke<Result>('transactions:save', tx, isNew),
  deleteTransaction: (transId: number) => invoke<Result>('transactions:delete', transId),
  expensesByMonth: (yearMonth: string) =>
    invoke<Record<number, number>>('transactions:expensesByMonth', yearMonth),
  latestCategoryId: (type: TransactionType) => invoke<number | null>('transactions:latestCategory', type),

  scheduledList: () => invoke<ScheduledTransaction[]>('scheduled:list'),
  getScheduled: (bdId: number) => invoke<ScheduledTransaction | null>('scheduled:get', bdId),
  saveScheduled: (item: ScheduledTransaction, isNew: boolean) =>
    invoke<Result>('scheduled:save', item, isNew),
  deleteScheduled: (bdId: number) => invoke<Result>('scheduled:delete', bdId),
  dueScheduled: () => invoke<ScheduledTransaction[]>('scheduled:due'),
  approveScheduled: (bdId: number) => invoke<Result>('scheduled:approve', bdId),
  cancelScheduledOnce: (bdId: number) => invoke<Result>('scheduled:cancelOnce', bdId),
  deleteDueScheduled: (bdId: number) => invoke<Result>('scheduled:deleteDue', bdId),

  settings: () => invoke<AppSettings>('settings:get'),
  setAccessMode: (mode: AccessMode) => invoke<Result>('settings:setAccessMode', mode),
  setDefaultCategory: (type: TransactionType, id: number | null) =>
    invoke<Result>('settings:setDefaultCategory', type, id),
  setUseLatestCategory: (type: TransactionType, enabled: boolean) =>
    invoke<Result>('settings:setUseLatestCategory', type, enabled),
  setBudget: (currencyId: number, amount: number | null) =>
    invoke<Result>('settings:setBudget', currencyId, amount),

  syncState: () => invoke<SyncState>('sync:state'),
  syncNow: () => invoke<Result>('sync:upload'),
  conflictKeepLocal: () => invoke<Result>('sync:keepLocal'),
  conflictKeepRemote: () => invoke<Result>('sync:keepRemote'),

  driveStatus: () => invoke<DriveStatus>('drive:status'),
  driveSetClient: (clientId: string, clientSecret: string) =>
    invoke<Result>('drive:setClient', clientId, clientSecret),
  driveConnect: () => invoke<Result>('drive:connect'),
  driveDisconnect: () => invoke<Result>('drive:disconnect'),
  driveListFiles: () => invoke<Result<DriveFileEntry[]>>('drive:listFiles'),
  driveOpen: (fileId: string) => invoke<Result>('drive:open', fileId)
};

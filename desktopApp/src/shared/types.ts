/**
 * Domain model shared by the Electron main process and the renderer.
 * Mirrors `shared/src/commonMain/kotlin/com/konhit/financeapp/domain/model` of the Android app.
 */

export type TransactionType = 'Deposit' | 'Withdrawal' | 'Transfer';

export const TRANSACTION_TYPES: TransactionType[] = ['Deposit', 'Withdrawal', 'Transfer'];

export type AccessMode = 'READ_WRITE' | 'READ_ONLY';

export const ACCOUNT_TYPES = ['Cash', 'Savings', 'Investment'] as const;

export interface Account {
  id: number;
  name: string;
  type: string;
  initialBal: number;
  currencyId: number;
  status: string;
  /** Computed balance; 0 when the row was loaded without balance computation. */
  balance: number;
}

export interface Category {
  id: number;
  name: string;
  parentId: number;
}

export interface CategoryNode {
  category: Category;
  children: CategoryNode[];
}

export interface Currency {
  id: number;
  name: string;
  pfxSymbol: string | null;
  sfxSymbol: string | null;
  decimalPoint: string | null;
  groupSeparator: string | null;
  scale: number | null;
  currencySymbol: string | null;
}

export interface Transaction {
  transId: number;
  accountId: number;
  toAccountId: number;
  payeeId: number;
  type: TransactionType;
  transAmount: number;
  toTransAmount: number;
  categId: number | null;
  transDate: string;
  lastUpdatedTime: string | null;
  notes: string | null;
  /**
   * MMEX FOLLOWUPID. `-1` for normal rows; source `BDID` when created from a schedule.
   * Encodes the boolean fromSchedule marker without altering CHECKINGACCOUNT_V1.
   */
  followupId: number;
}

export const fromSchedule = (tx: Transaction): boolean => tx.followupId !== -1;

export interface ScheduledTransaction {
  bdId: number;
  accountId: number;
  toAccountId: number;
  payeeId: number;
  type: TransactionType;
  transAmount: number;
  toTransAmount: number;
  categId: number | null;
  notes: string | null;
  nextOccurrenceDate: string;
  repeats: number;
  numOccurrences: number | null;
  status: string | null;
  transactionNumber: string | null;
  color: number;
  followupId: number;
}

export type SyncState =
  | { kind: 'Idle' }
  | { kind: 'Syncing' }
  | { kind: 'PendingSync' }
  | { kind: 'Conflict'; remoteTime: number; localSyncTime: number }
  | { kind: 'Error'; message: string };

export interface DriveStatus {
  connected: boolean;
  email: string | null;
  hasClientCredentials: boolean;
}

export interface DriveFileEntry {
  id: string;
  name: string;
  modifiedTime: string;
}

export interface FileInfo {
  /** Absolute path of the open .mmb file, or null when nothing is open. */
  path: string | null;
  /** "Local" or "Google Drive". */
  sourceType: string;
  driveFileId: string | null;
  accessMode: AccessMode;
  lastSyncDisplay: string;
}

export interface AppSettings {
  accessMode: AccessMode;
  localFilePath: string | null;
  driveFileId: string | null;
  lastSyncTime: number | null;
  pendingUpload: boolean;
  defaultCategoryIds: Partial<Record<TransactionType, number | null>>;
  useLatestCategory: Partial<Record<TransactionType, boolean>>;
  budgets: Record<string, number>;
  hiddenAccountIds: number[];
}

export interface TransactionFilter {
  type: TransactionType | null;
  accountId: number | null;
  currencyId: number | null;
  categoryId: number | null;
  startDate: string | null;
  endDate: string | null;
  minAmount: number | null;
  maxAmount: number | null;
}

export const EMPTY_FILTER: TransactionFilter = {
  type: null,
  accountId: null,
  currencyId: null,
  categoryId: null,
  startDate: null,
  endDate: null,
  minAmount: null,
  maxAmount: null
};

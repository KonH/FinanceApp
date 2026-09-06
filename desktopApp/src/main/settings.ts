import fs from 'node:fs';
import path from 'node:path';
import { app } from 'electron';
import type { AccessMode, AppSettings, TransactionType } from '../shared/types';

/**
 * Desktop counterpart of `SettingsRepositoryImpl` (DataStore on Android):
 * a small JSON document in the Electron userData directory.
 */

const DEFAULTS: AppSettings = {
  accessMode: 'READ_WRITE',
  localFilePath: null,
  driveFileId: null,
  lastSyncTime: null,
  pendingUpload: false,
  defaultCategoryIds: {},
  useLatestCategory: {},
  budgets: {},
  hiddenAccountIds: []
};

/** OAuth material and tokens live beside the settings, never inside the .mmb. */
export interface DriveCredentials {
  clientId: string | null;
  clientSecret: string | null;
  refreshToken: string | null;
  accessToken: string | null;
  accessTokenExpiry: number | null;
  email: string | null;
}

const DRIVE_DEFAULTS: DriveCredentials = {
  clientId: null,
  clientSecret: null,
  refreshToken: null,
  accessToken: null,
  accessTokenExpiry: null,
  email: null
};

interface StoreShape {
  settings: AppSettings;
  drive: DriveCredentials;
}

class SettingsStore {
  private data: StoreShape = { settings: { ...DEFAULTS }, drive: { ...DRIVE_DEFAULTS } };
  private file = '';

  init(): void {
    this.file = path.join(app.getPath('userData'), 'settings.json');
    try {
      if (fs.existsSync(this.file)) {
        const parsed = JSON.parse(fs.readFileSync(this.file, 'utf8')) as Partial<StoreShape>;
        this.data = {
          settings: { ...DEFAULTS, ...(parsed.settings ?? {}) },
          drive: { ...DRIVE_DEFAULTS, ...(parsed.drive ?? {}) }
        };
      }
    } catch {
      // A corrupt settings file must never block startup — fall back to defaults.
      this.data = { settings: { ...DEFAULTS }, drive: { ...DRIVE_DEFAULTS } };
    }
  }

  private persist(): void {
    if (!this.file) return;
    fs.mkdirSync(path.dirname(this.file), { recursive: true });
    fs.writeFileSync(this.file, JSON.stringify(this.data, null, 2), 'utf8');
  }

  get all(): AppSettings {
    return { ...this.data.settings };
  }

  get drive(): DriveCredentials {
    return { ...this.data.drive };
  }

  patch(patch: Partial<AppSettings>): void {
    this.data.settings = { ...this.data.settings, ...patch };
    this.persist();
  }

  patchDrive(patch: Partial<DriveCredentials>): void {
    this.data.drive = { ...this.data.drive, ...patch };
    this.persist();
  }

  get accessMode(): AccessMode {
    return this.data.settings.accessMode;
  }

  get isReadOnly(): boolean {
    return this.data.settings.accessMode === 'READ_ONLY';
  }

  setDefaultCategoryId(type: TransactionType, id: number | null): void {
    this.patch({ defaultCategoryIds: { ...this.data.settings.defaultCategoryIds, [type]: id } });
  }

  setUseLatestCategory(type: TransactionType, enabled: boolean): void {
    this.patch({ useLatestCategory: { ...this.data.settings.useLatestCategory, [type]: enabled } });
  }

  setBudget(currencyId: number, amount: number | null): void {
    const budgets = { ...this.data.settings.budgets };
    if (amount !== null && amount > 0) budgets[String(currencyId)] = amount;
    else delete budgets[String(currencyId)];
    this.patch({ budgets });
  }

  toggleHiddenAccount(id: number): void {
    const hidden = new Set(this.data.settings.hiddenAccountIds);
    if (hidden.has(id)) hidden.delete(id);
    else hidden.add(id);
    this.patch({ hiddenAccountIds: [...hidden] });
  }
}

export const settingsStore = new SettingsStore();

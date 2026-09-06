import path from 'node:path';
import { BrowserWindow, Menu, app, ipcMain, shell } from 'electron';
import { dbHolder } from './db/database';
import { registerIpcHandlers, reopenLastFile, setPendingOpenPath } from './ipc';
import { settingsStore } from './settings';
import { syncCoordinator } from './sync';

const DEV_SERVER = process.env.FINANCEAPP_DEV_SERVER;

let mainWindow: BrowserWindow | null = null;
let quitting = false;

/** A .mmb given on the command line — the app is registered for that extension. */
function mmbFromArgv(argv: string[]): string | null {
  const match = argv.slice(1).find((arg) => arg.toLowerCase().endsWith('.mmb'));
  return match ?? null;
}

function createWindow(): void {
  mainWindow = new BrowserWindow({
    width: 1180,
    height: 820,
    minWidth: 900,
    minHeight: 600,
    show: false,
    backgroundColor: '#faf8fc',
    title: 'FinanceApp',
    webPreferences: {
      preload: path.join(__dirname, '..', 'preload', 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  mainWindow.once('ready-to-show', () => mainWindow?.show());

  // External links open in the user's browser, never inside the app shell.
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    void shell.openExternal(url);
    return { action: 'deny' };
  });

  if (DEV_SERVER) {
    void mainWindow.loadURL(DEV_SERVER);
  } else {
    // dist/main/main/main.js → dist/renderer/index.html
    void mainWindow.loadFile(path.join(__dirname, '..', '..', 'renderer', 'index.html'));
  }

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

function buildMenu(): void {
  const isMac = process.platform === 'darwin';
  const template: Electron.MenuItemConstructorOptions[] = [
    ...(isMac
      ? ([{ role: 'appMenu' }] as Electron.MenuItemConstructorOptions[])
      : []),
    {
      label: 'File',
      submenu: [
        {
          label: 'Open .mmb…',
          accelerator: 'CmdOrCtrl+O',
          click: () => mainWindow?.webContents.send('menu:command', 'open')
        },
        {
          label: 'New .mmb…',
          accelerator: 'CmdOrCtrl+N',
          click: () => mainWindow?.webContents.send('menu:command', 'new')
        },
        { type: 'separator' },
        {
          label: 'Sync now',
          accelerator: 'CmdOrCtrl+S',
          click: () => mainWindow?.webContents.send('menu:command', 'sync')
        },
        { type: 'separator' },
        isMac ? { role: 'close' } : { role: 'quit' }
      ]
    },
    { role: 'editMenu' },
    {
      label: 'View',
      submenu: [
        { role: 'reload' },
        { role: 'toggleDevTools' },
        { type: 'separator' },
        { role: 'resetZoom' },
        { role: 'zoomIn' },
        { role: 'zoomOut' },
        { type: 'separator' },
        { role: 'togglefullscreen' }
      ]
    },
    { role: 'windowMenu' }
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  app.quit();
} else {
  app.on('second-instance', (_event, argv) => {
    const requested = mmbFromArgv(argv);
    if (requested) {
      setPendingOpenPath(requested);
      void reopenLastFile().then(() => mainWindow?.webContents.send('menu:command', 'refresh'));
    }
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });

  // macOS delivers file opens through this event instead of argv.
  app.on('open-file', (event, filePath) => {
    event.preventDefault();
    setPendingOpenPath(filePath);
    if (app.isReady()) {
      void reopenLastFile().then(() => mainWindow?.webContents.send('menu:command', 'refresh'));
    }
  });

  void app.whenReady().then(() => {
    settingsStore.init();
    // Never clobber a path an earlier "open-file" event already queued.
    const argvFile = mmbFromArgv(process.argv);
    if (argvFile) setPendingOpenPath(argvFile);
    registerIpcHandlers(() => mainWindow);

    // Re-opens the last file (Drive or local) the way MainActivity does on start.
    ipcMain.handle('app:bootstrap', async () => ({ opened: await reopenLastFile() }));

    syncCoordinator.onChange((state) => {
      mainWindow?.webContents.send('sync:changed', state);
    });

    buildMenu();
    createWindow();

    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
  });

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
  });

  // Android uploads on Activity.onStop(); the desktop equivalent is app shutdown.
  app.on('before-quit', (event) => {
    if (quitting || !dbHolder.isOpen) return;
    event.preventDefault();
    quitting = true;
    void syncCoordinator
      .uploadCurrent()
      .catch(() => undefined)
      .finally(() => {
        dbHolder.close();
        app.quit();
      });
  });
}

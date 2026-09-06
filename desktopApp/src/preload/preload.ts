import { contextBridge, ipcRenderer } from 'electron';

/**
 * The only bridge between the sandboxed renderer and Node. The renderer can
 * invoke IPC channels (all of which are validated by `ipcMain.handle`
 * registrations in the main process) and subscribe to push events.
 */
const api = {
  invoke: (channel: string, ...args: unknown[]): Promise<unknown> => ipcRenderer.invoke(channel, ...args),

  onSyncChanged: (listener: (state: unknown) => void): (() => void) => {
    const wrapped = (_event: unknown, state: unknown): void => listener(state);
    ipcRenderer.on('sync:changed', wrapped);
    return () => ipcRenderer.removeListener('sync:changed', wrapped);
  },

  onMenuCommand: (listener: (command: string) => void): (() => void) => {
    const wrapped = (_event: unknown, command: string): void => listener(command);
    ipcRenderer.on('menu:command', wrapped);
    return () => ipcRenderer.removeListener('menu:command', wrapped);
  }
};

contextBridge.exposeInMainWorld('financeApp', api);

export type PreloadApi = typeof api;

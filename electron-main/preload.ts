import { electronAPI } from '@electron-toolkit/preload';
import { contextBridge } from 'electron';

contextBridge.exposeInMainWorld('electronAPI', electronAPI);
contextBridge.exposeInMainWorld(
  'billdPerformanceEnabled',
  process.argv.includes('--billd-performance-log')
);

console.log('执行了electron-main的preload.ts');

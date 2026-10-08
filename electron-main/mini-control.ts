import { randomUUID } from 'crypto';

import { desktopCapturer, ipcMain, screen } from 'electron';

import { IPC_EVENT } from '../src/event';

import type { nutjsTs } from './types';

/** Commands are scoped to a capture session and serialized to preserve order. */
export function registerMiniControl(nutjs: nutjsTs) {
  const sessions = new Map<number, { token: string; displayId: number }>();
  let pending = 0;
  let queue: Promise<void> = Promise.resolve();
  ipcMain.handle(IPC_EVENT.miniScreenSource, async (event) => {
    const display = screen.getPrimaryDisplay();
    const sources = await desktopCapturer.getSources({
      types: ['screen'],
      thumbnailSize: { width: 0, height: 0 },
    });
    const source = sources.find((item) => item.display_id === `${display.id}`);
    if (!source) throw new Error('无法获取主屏幕，请检查 BilldDesk 的录屏权限');
    const token = randomUUID();
    sessions.set(event.sender.id, { token, displayId: display.id });
    event.sender.once('destroyed', () => sessions.delete(event.sender.id));
    return { id: source.id, token };
  });
  ipcMain.handle(IPC_EVENT.miniRelease, (event) => {
    sessions.delete(event.sender.id);
  });
  ipcMain.handle(IPC_EVENT.miniControl, async (event, token, command) => {
    const session = sessions.get(event.sender.id);
    if (!session || session.token !== token || pending >= 20) return false;
    const point =
      Number.isFinite(command?.x) &&
      Number.isFinite(command?.y) &&
      command.x >= 0 &&
      command.x <= 1 &&
      command.y >= 0 &&
      command.y <= 1;
    const keyMap = {
      Enter: nutjs.Key.Return,
      Backspace: nutjs.Key.Backspace,
      Escape: nutjs.Key.Escape,
      Tab: nutjs.Key.Tab,
    };
    const valid =
      (['move', 'click', 'rightClick'].includes(command?.action) && point) ||
      (command?.action === 'scroll' &&
        Number.isInteger(command.amount) &&
        Math.abs(command.amount) <= 20) ||
      (command?.action === 'text' &&
        typeof command.text === 'string' &&
        command.text.length > 0 &&
        command.text.length <= 1000) ||
      (command?.action === 'key' &&
        Object.prototype.hasOwnProperty.call(keyMap, command.key));
    if (!valid) return false;
    pending += 1;
    const operation = queue.then(async () => {
      if (sessions.get(event.sender.id) !== session) return;
      const display = screen.getPrimaryDisplay();
      // If the primary screen changed, coordinates no longer match the capture.
      if (display.id !== session.displayId) {
        sessions.delete(event.sender.id);
        throw new Error('主屏幕发生变化，请重新连接');
      }
      if (point) {
        const bounds = display.bounds;
        await nutjs.mouse.setPosition({
          x: bounds.x + Math.round(command.x * (bounds.width - 1)),
          y: bounds.y + Math.round(command.y * (bounds.height - 1)),
        });
        if (sessions.get(event.sender.id) !== session) return;
      }
      if (command.action === 'click')
        await nutjs.mouse.click(nutjs.Button.LEFT);
      if (command.action === 'rightClick')
        await nutjs.mouse.click(nutjs.Button.RIGHT);
      if (command.action === 'scroll') {
        if (command.amount > 0) await nutjs.mouse.scrollDown(command.amount);
        if (command.amount < 0) await nutjs.mouse.scrollUp(-command.amount);
      }
      if (command.action === 'text') {
        // Use the public native provider one character at a time. The higher
        // level type(string) logs text and keeps typing the whole string after
        // disconnection. Yield between characters so revocation takes effect.
        const characters = Array.from(command.text as string);
        let index = 0;
        while (index < characters.length) {
          if (sessions.get(event.sender.id) !== session) return;
          await nutjs.providerRegistry.getKeyboard().type(characters[index]);
          index += 1;
          await new Promise((resolve) => setTimeout(resolve, 10));
        }
      }
      if (command.action === 'key')
        await nutjs.keyboard.type(keyMap[command.key]);
    });
    queue = operation.catch(() => {
      // Preserve the queue after an input failure; the caller receives the error.
    });
    try {
      await operation;
      return sessions.get(event.sender.id) === session;
    } finally {
      pending -= 1;
    }
  });
}

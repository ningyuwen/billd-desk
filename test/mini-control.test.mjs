import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';
import { transformSync } from 'esbuild';

function harness() {
  const handlers = new Map();
  const calls = [];
  const display = { id: 1, bounds: { x: 0, y: 0, width: 1440, height: 900 } };
  const electron = {
    ipcMain: {
      handle(name, fn) {
        handlers.set(name, fn);
      },
    },
    screen: {
      getPrimaryDisplay() {
        return display;
      },
    },
    desktopCapturer: {
      async getSources() {
        return [{ id: 'screen:1', display_id: '1' }];
      },
    },
  };
  const events = {
    miniScreenSource: 'source',
    miniControl: 'control',
    miniRelease: 'release',
  };
  const record =
    (name) =>
    async (...args) => {
      calls.push([name, ...args]);
    };
  const nativeKeyboard = { type: record('nativeType') };
  const nut = {
    providerRegistry: { getKeyboard: () => nativeKeyboard },
    Key: { Return: 1, Backspace: 2, Escape: 3, Tab: 4 },
    Button: { LEFT: 0, RIGHT: 1 },
    mouse: {
      setPosition: record('position'),
      click: record('click'),
      scrollUp: record('up'),
      scrollDown: record('down'),
    },
    keyboard: { type: record('type') },
  };
  const module = { exports: {} };
  vm.runInNewContext(
    transformSync(readFileSync('electron-main/mini-control.ts', 'utf8'), {
      loader: 'ts',
      format: 'cjs',
    }).code,
    {
      module,
      exports: module.exports,
      Map,
      Number,
      Object,
      Error,
      setTimeout,
      require(name) {
        if (name === 'electron') return electron;
        if (name === 'crypto') return { randomUUID: () => 'test-session' };
        if (name === '../src/event') return { IPC_EVENT: events };
        throw new Error(name);
      },
    }
  );
  module.exports.registerMiniControl(nut);
  const event = { sender: { id: 10, once() {} } };
  return { handlers, calls, event, display, nativeKeyboard };
}

test('native input requires a live session and ignores commands queued after release', async () => {
  const { handlers, calls, event, display } = harness();
  const control = handlers.get('control');
  const command = { action: 'click', x: 0.5, y: 0.5 };
  assert.equal(await control(event, 'test-session', command), false);
  const source = await handlers.get('source')(event);
  assert.equal(source.id, 'screen:1');
  assert.equal(await control(event, 'wrong', command), false);
  assert.equal(
    await control(event, source.token, { ...command, x: -1 }),
    false
  );
  assert.equal(await control(event, source.token, command), true);
  assert.equal(calls[0][0], 'position');
  assert.equal(calls[0][1].x, 720);
  assert.equal(calls[0][1].y, 450);
  assert.equal(calls[1][0], 'click');
  const pending = control(event, source.token, {
    action: 'text',
    text: 'must not execute',
  });
  handlers.get('release')(event);
  assert.equal(await pending, false);
  assert.equal(calls.length, 2);
  await handlers.get('source')(event);
  display.id = 2;
  await assert.rejects(control(event, source.token, command), /主屏幕/);
  assert.equal(calls.length, 2);
});

test('disconnect cancels the remaining text while preserving whole Unicode characters', async () => {
  const { handlers, event, nativeKeyboard } = harness();
  const { token } = await handlers.get('source')(event);
  const typed = [];
  nativeKeyboard.type = async (character) => {
    typed.push(character);
    handlers.get('release')(event);
  };
  assert.equal(
    await handlers.get('control')(event, token, {
      action: 'text',
      text: '😀中文',
    }),
    false
  );
  assert.deepEqual(typed, ['😀']);
});

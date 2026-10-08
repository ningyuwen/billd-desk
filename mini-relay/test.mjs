import assert from 'node:assert/strict';
import { once } from 'node:events';
import { test } from 'node:test';
import { WebSocket } from 'ws';
import { createRelay, validControl } from './server.mjs';

function mailbox(socket) {
  const messages = [];
  const waiters = [];
  socket.on('message', (raw) => {
    const message = JSON.parse(raw.toString());
    const index = waiters.findIndex((item) => item.type === message.type);
    if (index >= 0) waiters.splice(index, 1)[0].resolve(message);
    else messages.push(message);
  });
  return {
    socket,
    send: (message) => socket.send(JSON.stringify(message)),
    count: (type) => messages.filter((message) => message.type === type).length,
    next(type) {
      const index = messages.findIndex((item) => item.type === type);
      if (index >= 0) return Promise.resolve(messages.splice(index, 1)[0]);
      return new Promise((resolve, reject) => {
        const timeout = setTimeout(
          () => reject(new Error(`No ${type} message`)),
          2000
        );
        waiters.push({
          type,
          resolve: (message) => {
            clearTimeout(timeout);
            resolve(message);
          },
        });
      });
    },
  };
}
const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const hello = {
  type: 'hello',
  code: '12345678',
  password: 'test-only-password',
};
const frame = (seq) => ({
  type: 'frame',
  seq,
  width: 640,
  height: 360,
  jpeg: '/9j/AA==',
});

async function setup(t, options) {
  const relay = createRelay(options);
  relay.server.listen(0, '127.0.0.1');
  await once(relay.server, 'listening');
  t.after(() => relay.close());
  const endpoint = `ws://127.0.0.1:${relay.server.address().port}/mini-control/`;
  const connect = async (role) => {
    const socket = new WebSocket(endpoint);
    const client = mailbox(socket);
    await once(socket, 'open');
    if (role) client.send({ ...hello, role });
    return client;
  };
  const host = await connect('host');
  await host.next('registered');
  return { relay, endpoint, host, connect };
}

test('authorization, single viewer, input validation, frame backpressure and cleanup', async (t) => {
  const { host, connect } = await setup(t);
  const wrong = await connect();
  wrong.send({ ...hello, role: 'viewer', password: 'wrong-password' });
  assert.match((await wrong.next('error')).message, /错误/);
  const viewer = await connect('viewer');
  await viewer.next('waiting');
  const request = await host.next('request');
  viewer.send({ type: 'control', data: { action: 'click', x: 0.5, y: 0.5 } });
  host.send(frame(1));
  await pause(50);
  assert.equal(host.count('control'), 0);
  assert.equal(viewer.count('frame'), 0);
  const second = await connect('viewer');
  assert.match((await second.next('error')).message, /连接/);
  host.send({ type: 'approve', requestId: 'invalid', allow: true });
  await pause(30);
  assert.equal(viewer.count('ready'), 0);
  host.send({ type: 'approve', requestId: request.requestId, allow: true });
  await viewer.next('ready');
  await host.next('ready');
  host.send(frame(1));
  assert.equal((await viewer.next('frame')).seq, 1);
  await pause(150);
  host.send(frame(2));
  await pause(30);
  assert.equal(viewer.count('frame'), 0);
  viewer.send({ type: 'ack', seq: 999 });
  await pause(30);
  assert.equal(host.count('ack'), 0);
  viewer.send({ type: 'ack', seq: 1 });
  assert.equal((await host.next('ack')).seq, 1);
  host.send(frame(2));
  assert.equal((await viewer.next('frame')).seq, 2);
  viewer.send({ type: 'control', data: { action: 'click', x: -1, y: 0 } });
  viewer.send({ type: 'control', data: { action: 'key', key: 'LeftSuper' } });
  viewer.send({
    type: 'control',
    data: { action: 'text', text: 'x'.repeat(1001) },
  });
  await pause(30);
  assert.equal(host.count('control'), 0);
  viewer.send({ type: 'control', data: { action: 'click', x: 0.2, y: 0.8 } });
  assert.deepEqual((await host.next('control')).data, {
    action: 'click',
    x: 0.2,
    y: 0.8,
  });
  viewer.socket.close();
  await host.next('viewerLeft');
  const next = await connect('viewer');
  await next.next('waiting');
  const nextRequest = await host.next('request');
  host.send({
    type: 'approve',
    requestId: nextRequest.requestId,
    allow: false,
  });
  await next.next('closed');
  await host.next('viewerLeft');
  host.socket.close();
  await pause(50);
  const replacement = await connect('host');
  await replacement.next('registered');
});

test('session expiry ends both sides', async (t) => {
  const { host, connect } = await setup(t, { sessionMs: 250 });
  const viewer = await connect('viewer');
  await viewer.next('waiting');
  const request = await host.next('request');
  host.send({ type: 'approve', requestId: request.requestId, allow: true });
  await viewer.next('ready');
  const closed = await viewer.next('closed');
  assert.ok(closed.message);
  assert.ok(host.socket.readyState >= WebSocket.CLOSING);
});

test('control allowlist rejects malformed and excessive commands', () => {
  assert.equal(validControl(null), false);
  assert.equal(validControl({ action: 'click', x: NaN, y: 0 }), false);
  assert.equal(validControl({ action: 'scroll', amount: 21 }), false);
  assert.equal(validControl({ action: 'scroll', amount: 1.5 }), false);
  assert.equal(validControl({ action: 'down', x: 0, y: 0 }), false);
  assert.equal(validControl({ action: 'text', text: '' }), false);
  assert.equal(validControl({ action: 'text', text: '中文测试' }), true);
  assert.equal(validControl({ action: 'scroll', amount: -3 }), true);
  assert.equal(validControl({ action: 'key', key: 'Enter' }), true);
});

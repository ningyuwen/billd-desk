import assert from 'node:assert/strict';
import { randomBytes, randomInt } from 'node:crypto';
import { WebSocket } from 'ws';

const endpoint = process.argv[2] || 'wss://desk.aduning.art/mini-control/';
const sockets = [];
async function client() {
  const socket = new WebSocket(endpoint);
  sockets.push(socket);
  const queue = [];
  const listeners = [];
  socket.on('message', (raw) => {
    const message = JSON.parse(raw.toString());
    const index = listeners.findIndex((item) => item.type === message.type);
    if (index < 0) queue.push(message);
    else listeners.splice(index, 1)[0].resolve(message);
  });
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(
      () => reject(new Error('WSS open timeout')),
      8000
    );
    socket.once('open', () => {
      clearTimeout(timeout);
      resolve();
    });
    socket.once('error', (error) => {
      clearTimeout(timeout);
      reject(error);
    });
  });
  return {
    send(message) {
      socket.send(JSON.stringify(message));
    },
    next(type) {
      const index = queue.findIndex((item) => item.type === type);
      if (index >= 0) return Promise.resolve(queue.splice(index, 1)[0]);
      return new Promise((resolve, reject) => {
        const timeout = setTimeout(
          () => reject(new Error(`WSS ${type} timeout`)),
          8000
        );
        listeners.push({
          type,
          resolve(message) {
            clearTimeout(timeout);
            resolve(message);
          },
        });
      });
    },
  };
}
try {
  const hello = {
    type: 'hello',
    code: `${randomInt(10000000, 100000000)}`,
    password: randomBytes(16).toString('hex'),
  };
  const host = await client();
  host.send({ ...hello, role: 'host' });
  await host.next('registered');
  const viewer = await client();
  viewer.send({ ...hello, role: 'viewer' });
  await viewer.next('waiting');
  const request = await host.next('request');
  host.send({ type: 'approve', requestId: request.requestId, allow: true });
  await viewer.next('ready');
  await host.next('ready');
  // Synthetic payload verifies transport, not real desktop capture or decoding.
  const frame = {
    type: 'frame',
    seq: 1,
    width: 1,
    height: 1,
    jpeg: '/9j/AA==',
  };
  host.send(frame);
  assert.deepEqual(await viewer.next('frame'), frame);
  viewer.send({ type: 'ack', seq: 1 });
  assert.equal((await host.next('ack')).seq, 1);
  const control = { action: 'click', x: 0.5, y: 0.5 };
  viewer.send({ type: 'control', data: control });
  assert.deepEqual((await host.next('control')).data, control);
  host.send({ type: 'disconnect' });
  await viewer.next('closed');
  await host.next('viewerLeft');
  console.log(
    'PASS: public WSS/TLS, approval, synthetic frame/ACK, control round-trip, disconnect'
  );
} finally {
  sockets.forEach((socket) => socket.terminate());
}

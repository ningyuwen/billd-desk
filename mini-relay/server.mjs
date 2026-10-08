import { randomUUID, timingSafeEqual } from 'node:crypto';
import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';
import { WebSocketServer, WebSocket } from 'ws';

const MAX_MESSAGE = 600 * 1024;
const MAX_FRAME = 560 * 1024;

export function validControl(data) {
  if (!data || typeof data !== 'object') return false;
  const point =
    Number.isFinite(data.x) &&
    Number.isFinite(data.y) &&
    data.x >= 0 &&
    data.x <= 1 &&
    data.y >= 0 &&
    data.y <= 1;
  if (['move', 'click', 'rightClick'].includes(data.action)) return point;
  if (data.action === 'scroll')
    return Number.isInteger(data.amount) && Math.abs(data.amount) <= 20;
  if (data.action === 'text')
    return (
      typeof data.text === 'string' &&
      data.text.length > 0 &&
      data.text.length <= 1000
    );
  return (
    data.action === 'key' &&
    ['Enter', 'Backspace', 'Escape', 'Tab'].includes(data.key)
  );
}

function sameSecret(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  return left.length === right.length && timingSafeEqual(left, right);
}

// TLS belongs at the reverse proxy. Bind to loopback by default; never log
// handshake payloads, passwords, desktop frames, or typed text.
export function createRelay({
  maxSessions = 20,
  sessionMs = 2 * 60 * 60 * 1000,
} = {}) {
  const sessions = new Map();
  const attempts = new Map();
  const connections = new Map();
  const server = createServer((req, res) => {
    if (req.url !== '/health') {
      res.writeHead(404);
      res.end();
      return;
    }
    res.writeHead(200, {
      'Content-Type': 'application/json',
      'Cache-Control': 'no-store',
    });
    res.end(JSON.stringify({ ok: true }));
  });
  const wss = new WebSocketServer({
    server,
    path: '/mini-control/',
    maxPayload: MAX_MESSAGE,
    perMessageDeflate: false,
  });
  const send = (socket, message) => {
    if (
      socket?.readyState !== WebSocket.OPEN ||
      socket.bufferedAmount > MAX_MESSAGE
    )
      return false;
    socket.send(JSON.stringify(message));
    return true;
  };
  const reject = (socket, message) => {
    send(socket, { type: 'error', message });
    socket.close(1008, 'Request rejected');
  };
  function dropViewer(session, message = '连接已断开') {
    const viewer = session.viewer;
    session.viewer = null;
    session.approved = false;
    session.frame = null;
    if (viewer) {
      send(viewer, { type: 'closed', message });
      viewer.close();
    }
    send(session.host, { type: 'viewerLeft' });
  }
  wss.on('connection', (socket, request) => {
    // Do not trust user-controlled forwarding headers. Keep a global ceiling
    // too, since a local proxy can hide distinct client addresses.
    const address = request.socket.remoteAddress || 'unknown';
    const count = connections.get(address) || 0;
    if (count >= 30 || wss.clients.size > 60) {
      socket.close(1013, 'Busy');
      return;
    }
    connections.set(address, count + 1);
    let session = null;
    let role = '';
    let alive = true;
    let controls = 0;
    let controlWindow = Date.now();
    const authTimeout = setTimeout(
      () => socket.close(1008, 'Handshake timeout'),
      10000
    );
    socket.on('pong', () => {
      alive = true;
    });
    socket.isAlive = () => {
      const previous = alive;
      alive = false;
      return previous;
    };
    socket.on('error', () => {
      /* Close listener releases session state. */
    });
    socket.on('message', (raw, binary) => {
      if (binary) {
        reject(socket, '消息格式不支持');
        return;
      }
      let message;
      try {
        message = JSON.parse(raw.toString());
      } catch {
        reject(socket, '消息格式错误');
        return;
      }
      if (!message || typeof message !== 'object') {
        reject(socket, '消息格式错误');
        return;
      }
      if (!role) {
        if (
          !['host', 'viewer'].includes(message.role) ||
          message.type !== 'hello' ||
          !/^\d{8}$/.test(message.code) ||
          typeof message.password !== 'string' ||
          message.password.length < 12 ||
          message.password.length > 64
        ) {
          reject(socket, '连接信息无效');
          return;
        }
        if (message.role === 'host') {
          if (sessions.has(message.code) || sessions.size >= maxSessions) {
            reject(socket, '无法创建会话，请重试');
            return;
          }
          session = {
            host: socket,
            password: message.password,
            code: message.code,
            created: Date.now(),
            viewer: null,
            approved: false,
            frame: null,
            lastFrame: 0,
            lastFrameAt: 0,
          };
          sessions.set(message.code, session);
          role = 'host';
          send(socket, { type: 'registered' });
        } else {
          const throttle = attempts.get(address) || {
            count: 0,
            at: Date.now(),
          };
          if (Date.now() - throttle.at > 60000) {
            throttle.count = 0;
            throttle.at = Date.now();
          }
          throttle.count += 1;
          attempts.set(address, throttle);
          if (throttle.count > 10) {
            reject(socket, '连接尝试过多，请稍后重试');
            return;
          }
          session = sessions.get(message.code);
          if (!session || !sameSecret(session.password, message.password)) {
            session = null;
            reject(socket, '设备代码或密码错误');
            return;
          }
          if (session.viewer) {
            session = null;
            reject(socket, '已有连接或待确认请求');
            return;
          }
          session.viewer = socket;
          session.requestId = randomUUID();
          session.requestAt = Date.now();
          role = 'viewer';
          send(socket, { type: 'waiting' });
          send(session.host, { type: 'request', requestId: session.requestId });
        }
        clearTimeout(authTimeout);
        return;
      }
      if (role === 'host') {
        if (
          message.type === 'approve' &&
          message.requestId === session.requestId &&
          session.viewer &&
          !session.approved
        ) {
          if (message.allow !== true) {
            dropViewer(session, 'Mac 拒绝了连接');
            return;
          }
          session.approved = true;
          send(session.viewer, { type: 'ready' });
          send(socket, { type: 'ready' });
        } else if (message.type === 'disconnect') {
          dropViewer(session);
        } else if (message.type === 'frame' && session.approved) {
          if (
            session.frame !== null ||
            Date.now() - session.lastFrameAt < 120 ||
            !Number.isSafeInteger(message.seq) ||
            message.seq <= session.lastFrame ||
            !Number.isInteger(message.width) ||
            !Number.isInteger(message.height) ||
            message.width < 1 ||
            message.height < 1 ||
            message.width > 1920 ||
            message.height > 1920 ||
            typeof message.jpeg !== 'string' ||
            message.jpeg.length > MAX_FRAME ||
            !/^\/9j\/[A-Za-z0-9+/=]+$/.test(message.jpeg)
          )
            return;
          if (
            send(session.viewer, {
              type: 'frame',
              seq: message.seq,
              width: message.width,
              height: message.height,
              jpeg: message.jpeg,
            })
          ) {
            session.frame = message.seq;
            session.frameAt = Date.now();
            session.lastFrame = message.seq;
            session.lastFrameAt = Date.now();
          }
        }
      } else if (session.approved && session.viewer === socket) {
        if (message.type === 'ack' && message.seq === session.frame) {
          session.frame = null;
          send(session.host, { type: 'ack', seq: message.seq });
        } else if (
          message.type === 'control' &&
          raw.length <= 8192 &&
          validControl(message.data)
        ) {
          if (Date.now() - controlWindow > 1000) {
            controls = 0;
            controlWindow = Date.now();
          }
          controls += 1;
          if (controls <= 80)
            send(session.host, { type: 'control', data: message.data });
        }
      }
    });
    socket.on('close', () => {
      clearTimeout(authTimeout);
      const remaining = (connections.get(address) || 1) - 1;
      if (remaining) connections.set(address, remaining);
      else connections.delete(address);
      if (!session) return;
      if (role === 'host' && sessions.get(session.code) === session) {
        sessions.delete(session.code);
        dropViewer(session, 'Mac 已停止共享');
      } else if (role === 'viewer' && session.viewer === socket)
        dropViewer(session);
    });
  });
  const sweep = setInterval(() => {
    sessions.forEach((session) => {
      if (Date.now() - session.created > sessionMs)
        session.host.close(1000, 'Session expired');
      else if (
        session.viewer &&
        ((!session.approved && Date.now() - session.requestAt > 30000) ||
          (session.frame !== null && Date.now() - session.frameAt > 10000))
      )
        dropViewer(session, '连接超时，请重新连接');
    });
    attempts.forEach((value, address) => {
      if (Date.now() - value.at > 60000) attempts.delete(address);
    });
  }, 1000);
  const heartbeat = setInterval(() => {
    wss.clients.forEach((socket) => {
      if (!socket.isAlive?.()) socket.terminate();
      else socket.ping();
    });
  }, 15000);
  sweep.unref();
  heartbeat.unref();
  return {
    server,
    close: async () => {
      clearInterval(sweep);
      clearInterval(heartbeat);
      wss.clients.forEach((socket) => socket.terminate());
      await new Promise((resolve) => wss.close(resolve));
      if (server.listening)
        await new Promise((resolve) => server.close(resolve));
    },
  };
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(process.argv[1]).href
) {
  const relay = createRelay();
  const port = Number(process.env.MINI_RELAY_PORT || 4311);
  relay.server.listen(port, process.env.MINI_RELAY_BIND || '127.0.0.1', () => {
    console.log(`BilldDesk mini relay listening on port ${port}`);
  });
  const shutdown = () => {
    relay.close().then(() => process.exit(0));
  };
  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

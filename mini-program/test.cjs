const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const vm = require('node:vm');
const { fit, point } = require('./lib/geometry');
const { parseInvitation } = require('./lib/invitation');

test('invitation import fills credentials and rejects incomplete or unsafe addresses', () => {
  const invitation =
    'BilldDesk 小程序连接\n中继地址：wss://desk.aduning.art/mini-control/\n连接码：12345678\n临时密码：0123456789abcdef01234567';
  assert.deepEqual(parseInvitation(invitation.replaceAll('\n', '\r\n')), {
    url: 'wss://desk.aduning.art/mini-control/',
    code: '12345678',
    password: '0123456789abcdef01234567',
  });
  assert.equal(parseInvitation(invitation.replace('wss://', 'ws://')), null);
  assert.equal(
    parseInvitation(
      invitation.replace('desk.aduning.art', 'user:secret@desk.aduning.art')
    ),
    null
  );
  assert.equal(
    parseInvitation(invitation.replace('/mini-control/', '/other/')),
    null
  );
  assert.equal(parseInvitation(invitation.slice(0, -1)), null);
  assert.equal(parseInvitation(invitation + '\n连接码：87654321'), null);
  assert.equal(parseInvitation('x'.repeat(513)), null);
});

test('letterboxing maps clicks correctly and ignores black bars', () => {
  const rectangle = fit(300, 300, 1920, 1080);
  assert.deepEqual(point(150, 150, rectangle), { x: 0.5, y: 0.5 });
  assert.equal(point(150, 10, rectangle), null);
  assert.equal(point(301, 150, rectangle), null);
});

test('viewer acknowledges only after image load; disconnect prevents late rendering', () => {
  let page;
  let write;
  let image;
  let draws = 0;
  const sent = [];
  const canvas = {
    createImage() {
      image = {};
      return image;
    },
  };
  const wx = {
    env: { USER_DATA_PATH: '/tmp' },
    createSelectorQuery() {
      return {
        select() { return this; },
        fields() { return this; },
        exec(callback) {
          callback([{ node: canvas, width: 300, height: 300 }]);
        },
      };
    },
    getWindowInfo: () => ({ pixelRatio: 2 }),
    getFileSystemManager: () => ({
      writeFile(options) {
        write = options;
      },
      unlink() {},
    }),
  };
  vm.runInNewContext(
    fs.readFileSync(`${__dirname}/pages/remote/index.js`, 'utf8'),
    {
      require: (name) =>
        name.endsWith('invitation') ? { parseInvitation } : { fit, point },
      Page(value) {
        page = value;
      },
      wx,
      clearTimeout,
    }
  );
  const task = {
    send({ data }) {
      sent.push(JSON.parse(data));
    },
    close() {},
  };
  page.task = task;
  const context = {
    scale() {},
    fillRect() {},
    drawImage() {
      draws += 1;
    },
  };
  canvas.getContext = () => context;
  page.setData = (data) => Object.assign(page.data, data);
  page.importInvitation({
    detail: {
      value:
        'BilldDesk 小程序连接\n中继地址：wss://desk.aduning.art/mini-control/\n连接码：12345678\n临时密码：0123456789abcdef01234567',
    },
  });
  assert.equal(page.data.code, '12345678');
  assert.equal(page.data.password, '0123456789abcdef01234567');
  assert.equal(page.data.invitation, '');
  page.draw({ seq: 1, width: 1920, height: 1080, jpeg: '/9j/AA==' }, task);
  assert.equal(sent.length, 0);
  assert.equal(write, undefined);
  // A Page instance has no component-only createSelectorQuery method on iOS.
  page.initCanvas(task);
  assert.equal(canvas.width, 600);
  assert.equal(canvas.height, 600);
  write.success();
  assert.equal(sent.length, 0);
  image.onload();
  assert.equal(draws, 1);
  assert.equal(sent[0].type, 'ack');
  page.draw({ seq: 2, width: 1920, height: 1080, jpeg: '/9j/AA==' }, task);
  write.success();
  page.disconnect();
  image.onload();
  assert.equal(draws, 1);
  assert.equal(page.data.password, '');
  assert.equal(page.rectangle, null);
});

test('successive frames stay fresh with a decoder that caches images by path', () => {
  let page;
  let pendingImage;
  const files = new Map();
  const decoded = new Map();
  const shown = [];
  const sent = [];
  const canvas = {
    createImage() {
      const image = {};
      Object.defineProperty(image, 'src', {
        set(path) {
          if (!decoded.has(path)) decoded.set(path, files.get(path));
          image.pixels = decoded.get(path);
          pendingImage = image;
        },
      });
      return image;
    },
  };
  vm.runInNewContext(
    fs.readFileSync(`${__dirname}/pages/remote/index.js`, 'utf8'),
    {
      require: (name) =>
        name.endsWith('invitation') ? { parseInvitation } : { fit, point },
      Page(value) { page = value; },
      wx: {
        getFileSystemManager: () => ({
          writeFile(options) {
            files.set(options.filePath, options.data);
            options.success();
          },
          unlink(options) { files.delete(options.filePath); },
        }),
      },
      clearTimeout,
    }
  );
  const task = {
    send({ data }) { sent.push(JSON.parse(data)); },
    close() {},
  };
  Object.assign(page, {
    task,
    canvas,
    filePrefix: '/synthetic/session',
    size: { width: 300, height: 150 },
    context: {
      fillRect() {},
      drawImage(image) { shown.push(image.pixels); },
    },
    setData(data) { Object.assign(this.data, data); },
  });
  for (let seq = 1; seq <= 4; seq += 1) {
    page.draw({ seq, width: 300, height: 150, jpeg: `frame-${seq}` }, task);
    assert.equal(files.size, 1);
    assert.equal(sent.length, seq - 1);
    pendingImage.onload();
    assert.equal(files.size, 0);
    assert.equal(sent.length, seq);
  }
  assert.deepEqual(shown, ['frame-1', 'frame-2', 'frame-3', 'frame-4']);
  // Resizing can decode the same sequence again; it must not reuse a cached path.
  page.draw({ seq: 4, width: 300, height: 150, jpeg: 'redraw' }, task);
  pendingImage.onload();
  assert.equal(shown.at(-1), 'redraw');
  page.draw({ seq: 5, width: 300, height: 150, jpeg: 'pending' }, task);
  page.disconnect();
  assert.equal(files.size, 0);
  pendingImage.onload();
  assert.equal(shown.length, 5);
  assert.equal(sent.length, 5);
});

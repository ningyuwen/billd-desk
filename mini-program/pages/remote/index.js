const { fit, point } = require('../../lib/geometry');
const { parseInvitation } = require('../../lib/invitation');

Page({
  data: {
    url: '',
    code: '',
    password: '',
    invitation: '',
    text: '',
    connected: false,
    connecting: false,
    status: '尚未连接',
  },
  onLoad() {
    this.setData({
      url:
        wx.getStorageSync('miniRelayUrl') ||
        'wss://desk.aduning.art/mini-control/',
    });
  },
  onHide() {
    this.disconnect();
  },
  onUnload() {
    this.disconnect();
  },
  onResize() {
    if (!this.task || !this.data.connected) return;
    this.rectangle = null;
    this.pendingFrame = this.lastFrame;
    this.initCanvas(this.task);
  },
  change(event) {
    const field = event.currentTarget.dataset.field;
    if (['url', 'code', 'password', 'text'].includes(field))
      this.setData({ [field]: event.detail.value });
  },
  importInvitation(event) {
    const value = event.detail.value;
    const invitation = parseInvitation(value);
    if (invitation) {
      this.setData({
        ...invitation,
        invitation: '',
        status: '已填入连接信息，请点请求连接',
      });
    } else {
      this.setData({
        invitation: value,
        status: value
          ? '请粘贴 Mac「复制连接信息」按钮复制的完整内容'
          : '尚未连接',
      });
    }
  },
  connect() {
    const url = this.data.url.trim();
    if (!/^wss:\/\/[a-zA-Z0-9.-]+(?::\d+)?\/mini-control\/$/.test(url)) {
      this.setData({
        status: '请填写有效的 wss:// 中继地址，路径为 /mini-control/',
      });
      return;
    }
    if (!/^\d{8}$/.test(this.data.code) || this.data.password.length < 12) {
      this.setData({ status: '请填写 Mac 显示的 8 位连接码和临时密码' });
      return;
    }
    if (this.task) this.disconnect();
    wx.setStorageSync('miniRelayUrl', url);
    this.setData({ connecting: true, status: '正在连接…' });
    const task = wx.connectSocket({
      url,
      fail: () => {
        if (this.task === task)
          this.finish('连接失败，请检查 socket 合法域名与中继服务');
      },
    });
    this.task = task;
    this.filePrefix = `${wx.env.USER_DATA_PATH}/billd-frame-${Date.now()}-${Math.floor(Math.random() * 1000000)}`;
    this.frameFiles = new Set();
    this.timeout = setTimeout(() => {
      if (this.task === task)
        this.finish('连接超时，请在 Mac 上确认请求后重试');
    }, 40000);
    task.onOpen(() => {
      if (this.task !== task) return;
      this.send({
        type: 'hello',
        role: 'viewer',
        code: this.data.code,
        password: this.data.password,
      });
    });
    task.onMessage((event) => {
      if (this.task !== task) return;
      let message;
      try {
        message = JSON.parse(event.data);
      } catch {
        this.finish('收到无法识别的消息');
        return;
      }
      if (message.type === 'waiting')
        this.setData({ status: '等待 Mac 确认，30 秒内有效' });
      if (message.type === 'ready') {
        clearTimeout(this.timeout);
        this.setData(
          {
            connected: true,
            connecting: false,
            password: '',
            invitation: '',
            status: '已连接，正在等待画面…',
          },
          () => this.initCanvas(task)
        );
      }
      if (message.type === 'frame') this.draw(message, task);
      if (['error', 'closed'].includes(message.type))
        this.finish(message.message || '连接已结束');
    });
    task.onClose(() => {
      if (this.task === task) this.finish('连接已断开');
    });
    task.onError(() => {
      if (this.task === task) this.finish('连接失败，请检查域名、证书和网络');
    });
  },
  send(message) {
    if (this.task) this.task.send({ data: JSON.stringify(message) });
  },
  finish(status) {
    const task = this.task;
    this.task = null;
    clearTimeout(this.timeout);
    this.canvas = null;
    this.rectangle = null;
    this.pendingFrame = null;
    this.lastFrame = null;
    this.setData({
      connected: false,
      connecting: false,
      password: '',
      invitation: '',
      text: '',
      status,
    });
    if (task) task.close({});
    const fs = wx.getFileSystemManager();
    const files = this.frameFiles;
    this.frameFiles = null;
    this.filePrefix = null;
    files?.forEach((path) =>
      fs.unlink({
        filePath: path,
        fail() {},
      })
    );
    files?.clear();
  },
  disconnect() {
    this.finish('已断开');
  },
  initCanvas(task) {
    wx.createSelectorQuery()
      .select('#screen')
      .fields({ node: true, size: true })
      .exec((results) => {
        if (this.task !== task) return;
        if (!results[0] || !results[0].node) {
          this.finish('无法初始化画面，请重新连接');
          return;
        }
        const view = results[0];
        const ratio = wx.getWindowInfo().pixelRatio;
        this.canvas = view.node;
        this.canvas.width = view.width * ratio;
        this.canvas.height = view.height * ratio;
        this.context = this.canvas.getContext('2d');
        this.context.scale(ratio, ratio);
        this.size = { width: view.width, height: view.height };
        if (this.pendingFrame) {
          const frame = this.pendingFrame;
          this.pendingFrame = null;
          this.draw(frame, task);
        }
      });
  },
  draw(frame, task) {
    if (!this.canvas) {
      this.pendingFrame = frame;
      return;
    }
    if (
      !Number.isInteger(frame.seq) ||
      !Number.isInteger(frame.width) ||
      !Number.isInteger(frame.height) ||
      frame.width < 1 ||
      frame.height < 1 ||
      frame.width > 1920 ||
      frame.height > 1920 ||
      typeof frame.jpeg !== 'string' ||
      frame.jpeg.length > 560 * 1024
    ) {
      this.finish('画面数据无效');
      return;
    }
    this.lastFrame = frame;
    // Native image decoders can cache by path even after the file is overwritten.
    // Give every decode a fresh path, including redraws after resizing.
    this.frameFileSerial = (this.frameFileSerial || 0) + 1;
    const path = `${this.filePrefix}-${this.frameFileSerial}.jpg`;
    const files = this.frameFiles || (this.frameFiles = new Set());
    files.add(path);
    const fs = wx.getFileSystemManager();
    const removeFile = () => {
      files.delete(path);
      fs.unlink({ filePath: path, fail() {} });
    };
    fs.writeFile({
      filePath: path,
      data: frame.jpeg,
      encoding: 'base64',
      success: () => {
        if (this.task !== task || !this.canvas) {
          removeFile();
          return;
        }
        const canvas = this.canvas;
        const image = canvas.createImage();
        image.onload = () => {
          removeFile();
          if (this.task !== task || this.canvas !== canvas) return;
          this.rectangle = fit(
            this.size.width,
            this.size.height,
            frame.width,
            frame.height
          );
          const r = this.rectangle;
          this.context.fillStyle = '#000';
          this.context.fillRect(0, 0, this.size.width, this.size.height);
          this.context.drawImage(image, r.x, r.y, r.width, r.height);
          this.send({ type: 'ack', seq: frame.seq });
          if (this.data.status !== '已连接：点画面单击，长按为右键')
            this.setData({ status: '已连接：点画面单击，长按为右键' });
        };
        image.onerror = () => {
          removeFile();
          if (this.task === task) this.finish('画面解码失败，请重新连接');
        };
        image.src = path;
      },
      fail: () => {
        removeFile();
        if (this.task === task)
          this.finish('无法写入临时画面，请检查手机可用空间');
      },
    });
  },
  touchStart(event) {
    if (event.touches.length !== 1) {
      this.touch = null;
      return;
    }
    const t = event.touches[0];
    this.touch = { x: t.x, y: t.y, time: Date.now() };
  },
  touchEnd(event) {
    const start = this.touch;
    this.touch = null;
    const end = event.changedTouches[0];
    if (!start || !end || Math.hypot(start.x - end.x, start.y - end.y) > 12)
      return;
    const p = point(end.x, end.y, this.rectangle);
    if (p)
      this.control({
        action: Date.now() - start.time >= 500 ? 'rightClick' : 'click',
        ...p,
      });
  },
  control(data) {
    if (this.data.connected && this.rectangle)
      this.send({ type: 'control', data });
  },
  scroll(event) {
    this.control({
      action: 'scroll',
      amount: Number(event.currentTarget.dataset.amount),
    });
  },
  key(event) {
    this.control({ action: 'key', key: event.currentTarget.dataset.key });
  },
  sendText() {
    if (!this.data.text || !this.rectangle) return;
    this.control({ action: 'text', text: this.data.text });
    this.setData({ text: '' });
  },
});

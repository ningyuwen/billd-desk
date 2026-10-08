<template>
  <details class="mini-host">
    <summary>小程序体验连接</summary>
    <p>主屏幕 · 最多 5 帧/秒 · 单人控制 · 每次连接需在此确认</p>
    <label>
      中继地址
      <input
        v-model="url"
        :disabled="!!socket"
        placeholder="wss://你的域名/mini-control/"
      />
    </label>
    <div class="actions">
      <button
        v-if="!socket"
        @click="start"
      >
        开启体验连接
      </button>
      <button
        v-else
        @click="stop"
      >
        停止共享
      </button>
    </div>
    <div
      v-if="socket"
      class="credentials"
    >
      <span>连接码：{{ code }}</span>
      <span>临时密码：{{ password }}</span>
      <button @click="copyConnection">复制连接信息</button>
      <span role="status">{{ copyStatus }}</span>
    </div>
    <div
      v-if="requestId"
      class="approval"
    >
      <span>有人请求查看并控制主屏幕</span>
      <button
        :disabled="accepting"
        @click="approve"
      >
        允许
      </button>
      <button
        :disabled="accepting"
        @click="reject"
      >
        拒绝
      </button>
    </div>
    <p role="status">{{ status }}</p>
  </details>
</template>

<script lang="ts" setup>
import { onUnmounted, ref, shallowRef } from 'vue';

import { IPC_EVENT } from '@/event';
import { ipcRenderer } from '@/utils';

const url = ref(
  localStorage.getItem('miniRelayUrl') || 'wss://desk.aduning.art/mini-control/'
);
const socket = shallowRef<WebSocket>();
const code = ref('');
const password = ref('');
const copyStatus = ref('');
const status = ref('开启后，把连接码和临时密码交给体验者。');
const requestId = ref('');
const accepting = ref(false);
let stream: MediaStream | undefined;
let token = '';
let video: HTMLVideoElement | undefined;
let timer: ReturnType<typeof setTimeout> | undefined;
let sequence = 0;
let inFlight = 0;
let active = false;
let inputQueue = Promise.resolve();
let queuedInputs = 0;

function releaseCapture() {
  active = false;
  token = '';
  if (timer) clearTimeout(timer);
  stream?.getTracks().forEach((track) => track.stop());
  stream = undefined;
  video?.pause();
  if (video) video.srcObject = null;
  video = undefined;
  inFlight = 0;
  void ipcRenderer?.invoke(IPC_EVENT.miniRelease);
}

function stop() {
  const previous = socket.value;
  socket.value = undefined;
  requestId.value = '';
  code.value = '';
  password.value = '';
  copyStatus.value = '';
  releaseCapture();
  previous?.close();
  status.value = '已停止共享，连接码和密码已失效。';
}

function send(message: Record<string, unknown>) {
  if (socket.value?.readyState === WebSocket.OPEN)
    socket.value.send(JSON.stringify(message));
}

async function copyConnection() {
  if (!socket.value || !code.value || !password.value) return;
  try {
    await navigator.clipboard.writeText(
      `BilldDesk 小程序连接\n中继地址：${url.value.trim()}\n连接码：${code.value}\n临时密码：${password.value}`
    );
    copyStatus.value = '已复制，在手机小程序的粘贴框粘贴即可。';
  } catch {
    copyStatus.value = '复制失败，请重试。';
  }
}

function start() {
  try {
    const endpoint = new URL(url.value.trim());
    const local = ['localhost', '127.0.0.1', '[::1]'].includes(
      endpoint.hostname
    );
    if (endpoint.protocol !== 'wss:' && !(local && endpoint.protocol === 'ws:'))
      throw new Error('公网连接需使用 wss://，本机开发可使用 ws://127.0.0.1');
    if (
      endpoint.pathname !== '/mini-control/' ||
      endpoint.username ||
      endpoint.password ||
      endpoint.search ||
      endpoint.hash
    )
      throw new Error('地址路径须为 /mini-control/，且不包含账号或参数');
    localStorage.setItem('miniRelayUrl', endpoint.href);
    const numbers = crypto.getRandomValues(new Uint32Array(8));
    code.value = Array.from(numbers, (value) => `${value % 10}`).join('');
    password.value = Array.from(
      crypto.getRandomValues(new Uint8Array(12)),
      (value) => value.toString(16).padStart(2, '0')
    ).join('');
    const connection = new WebSocket(endpoint.href);
    socket.value = connection;
    status.value = '正在连接中继…';
    connection.onopen = () => {
      if (socket.value !== connection) return;
      send({
        type: 'hello',
        role: 'host',
        code: code.value,
        password: password.value,
      });
    };
    connection.onmessage = (event) => {
      if (socket.value !== connection) return;
      let message;
      try {
        message = JSON.parse(event.data);
      } catch {
        return;
      }
      if (message.type === 'registered')
        status.value = '等待小程序连接，会话最长两小时。';
      if (message.type === 'request') {
        requestId.value = message.requestId;
        status.value = '请确认这位体验者，允许后才会发送屏幕。';
      }
      if (message.type === 'ready') {
        requestId.value = '';
        active = true;
        status.value = '小程序正在查看和控制主屏幕。';
        frames();
      }
      if (message.type === 'ack' && message.seq === inFlight) inFlight = 0;
      if (message.type === 'viewerLeft') {
        requestId.value = '';
        releaseCapture();
        status.value = '体验者已断开，等待下一次连接。';
      }
      if (message.type === 'control' && active && queuedInputs < 20) {
        const sessionToken = token;
        queuedInputs += 1;
        inputQueue = inputQueue
          .then(async () => {
            if (!active || token !== sessionToken) return;
            const result = await ipcRenderer?.invoke(
              IPC_EVENT.miniControl,
              sessionToken,
              message.data
            );
            if (!result) throw new Error('输入未执行');
          })
          .catch(() => {
            if (token === sessionToken) {
              send({ type: 'disconnect' });
              releaseCapture();
              status.value = '控制失败，请检查辅助功能权限后重新连接。';
            }
          })
          .finally(() => {
            queuedInputs -= 1;
          });
      }
      if (message.type === 'error') {
        const errorMessage = message.message;
        stop();
        status.value = errorMessage || '中继拒绝了连接';
      }
    };
    connection.onclose = () => {
      if (socket.value !== connection) return;
      stop();
      status.value = '中继连接已断开，请重新开启。';
    };
    connection.onerror = () => {
      if (socket.value === connection)
        status.value = '中继连接失败，请检查地址与服务状态。';
    };
  } catch (error) {
    status.value = error instanceof Error ? error.message : '连接失败';
  }
}

function reject() {
  send({ type: 'approve', requestId: requestId.value, allow: false });
  requestId.value = '';
}

async function approve() {
  const connection = socket.value;
  const pendingRequest = requestId.value;
  accepting.value = true;
  try {
    const source = await ipcRenderer?.invoke(IPC_EVENT.miniScreenSource);
    if (!source) throw new Error('无法获取屏幕');
    token = source.token;
    const constraints = {
      audio: false,
      video: {
        mandatory: {
          chromeMediaSource: 'desktop',
          chromeMediaSourceId: source.id,
        },
      },
    } as unknown as MediaStreamConstraints;
    const captured = await navigator.mediaDevices.getUserMedia(constraints);
    if (socket.value !== connection || requestId.value !== pendingRequest) {
      captured.getTracks().forEach((track) => track.stop());
      releaseCapture();
      return;
    }
    stream = captured;
    stream.getVideoTracks()[0].onended = () => {
      send({ type: 'disconnect' });
      releaseCapture();
      status.value = '屏幕共享已结束。';
    };
    video = document.createElement('video');
    video.muted = true;
    video.srcObject = stream;
    await video.play();
    if (socket.value !== connection || requestId.value !== pendingRequest) {
      releaseCapture();
      return;
    }
    send({ type: 'approve', requestId: pendingRequest, allow: true });
  } catch {
    reject();
    releaseCapture();
    status.value = '共享失败，请检查系统设置中 BilldDesk 的录屏权限。';
  } finally {
    accepting.value = false;
  }
}

function frames() {
  if (!active || !video || !stream) return;
  try {
    if (
      !inFlight &&
      video.videoWidth > 0 &&
      socket.value?.readyState === WebSocket.OPEN &&
      socket.value.bufferedAmount < 600 * 1024
    ) {
      const ratio = Math.min(
        1280 / video.videoWidth,
        720 / video.videoHeight,
        1
      );
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(video.videoWidth * ratio);
      canvas.height = Math.round(video.videoHeight * ratio);
      canvas
        .getContext('2d')
        ?.drawImage(video, 0, 0, canvas.width, canvas.height);
      let jpeg = canvas.toDataURL('image/jpeg', 0.6).split(',')[1];
      if (jpeg.length > 560 * 1024)
        jpeg = canvas.toDataURL('image/jpeg', 0.35).split(',')[1];
      if (jpeg.length <= 560 * 1024) {
        sequence += 1;
        inFlight = sequence;
        send({
          type: 'frame',
          seq: sequence,
          width: canvas.width,
          height: canvas.height,
          jpeg,
        });
      }
    }
  } catch {
    send({ type: 'disconnect' });
    releaseCapture();
    status.value = '画面传输失败，请重新连接。';
    return;
  }
  timer = setTimeout(frames, 200);
}

onUnmounted(stop);
</script>

<style scoped lang="scss">
.mini-host {
  padding: 14px 18px;
  margin-top: 16px;
  background: #f6f8fc;
  border: 1px solid #d8dfeb;
  border-radius: 10px;
  font-size: 13px;
  summary {
    cursor: pointer;
    font-weight: 600;
  }
  p {
    color: #526078;
    line-height: 1.6;
  }
  label {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  input {
    flex: 1;
    min-width: 0;
    padding: 7px;
  }
  button {
    padding: 7px 12px;
    cursor: pointer;
  }
  .actions,
  .approval {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    margin-top: 10px;
  }
  .credentials {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-top: 12px;
    overflow-wrap: anywhere;
  }
}
</style>

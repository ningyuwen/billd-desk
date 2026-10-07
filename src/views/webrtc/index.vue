<template>
  <div class="webrtc-wrap">
    <header
      ref="controlBarRef"
      class="control-bar connection-details"
      @mousedown.stop
      @mouseup.stop
      @keydown.stop="handleToolbarKeyDown"
      @keyup.stop
    >
      <button
        ref="detailButtonRef"
        type="button"
        class="control-button"
        aria-label="连接详情"
        aria-controls="connection-details-panel"
        :aria-expanded="showDetail"
        title="连接详情"
        @click="showDetail = !showDetail"
      >
        详情
      </button>

      <div
        id="connection-details-panel"
        class="info"
        :class="{ show: showDetail }"
      >
        <div class="details-heading">
          <span>连接详情</span>
          <button
            type="button"
            class="control-button"
            aria-label="关闭连接详情"
            @click="closeDetail"
          >
            收起
          </button>
        </div>
        <div
          class="debug-area"
          @click="handleOpenDebug"
        ></div>
        <div
          v-if="appStore.showDebug"
          class="debug-info"
        >
          <div>
            <span
              class="item"
              @click="windowReload"
              >刷新</span
            >
            <span>，</span>
            <span
              class="item"
              @click="handleOpenDevTools({ windowId })"
              >控制台</span
            >
          </div>

          <div>
            <span>窗口Id：</span>
            <span
              class="link"
              @click="handleCopy(windowId)"
            >
              {{ windowId }}
            </span>
            <span>，</span>
            <span>roomId：</span>
            <span
              class="link"
              @click="handleCopy(roomId)"
            >
              {{ roomId }}
            </span>
          </div>
          <div>
            <span>socketId：</span>
            <span
              class="link"
              @click="handleCopy(mySocketId)"
            >
              {{ mySocketId }}
            </span>
          </div>
        </div>

        <div class="link-config">
          <div class="link-item">
            <n-space>
              <div class="link-label">模式：</div>
              <n-radio
                :checked="!isWatchMode"
                @change="isWatchMode = false"
              >
                控制模式
              </n-radio>
              <n-radio
                :checked="isWatchMode"
                @change="isWatchMode = true"
              >
                观看模式
              </n-radio>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">鼠标：</div>
              <n-radio
                :checked="showCursor"
                @change="showCursor = true"
              >
                显示
              </n-radio>
              <n-radio
                :checked="!showCursor"
                @change="showCursor = false"
              >
                隐藏
              </n-radio>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">码率：</div>
              <n-radio-group v-model:value="currentMaxBitrate">
                <n-radio
                  v-for="item in maxBitrate"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </n-radio>
              </n-radio-group>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">帧率：</div>
              <n-radio-group v-model:value="currentMaxFramerate">
                <n-radio
                  v-for="item in maxFramerate"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </n-radio>
              </n-radio-group>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">画质预设：</div>
              <n-radio-group v-model:value="currentResolutionRatio">
                <n-radio
                  v-for="item in resolutionRatio"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </n-radio>
              </n-radio-group>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">视频内容：</div>
              <n-radio-group v-model:value="currentVideoContentHint">
                <n-radio
                  v-for="item in videoContentHint"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </n-radio>
              </n-radio-group>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">音频内容：</div>
              <n-radio-group v-model:value="currentAudioContentHint">
                <n-radio
                  v-for="item in audioContentHint"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </n-radio>
              </n-radio-group>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">实际尺寸：</div>
              <div class="item">
                {{ videoSettings?.width + 'x' + videoSettings?.height }}
              </div>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">解码帧率：</div>
              <div class="item">
                {{ formatMetric(receivedVideoStats?.fps, ' 帧/秒') }}
              </div>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">呈现帧率：</div>
              <div class="item">
                {{ formatMetric(receivedVideoStats?.presentedFps, ' 帧/秒') }}
              </div>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">回调帧间隔：</div>
              <div class="item">
                {{ formatMetric(receivedVideoStats?.presentationGapMs, ' ms') }}
              </div>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">呈现等待：</div>
              <div class="item">
                {{
                  formatMetric(receivedVideoStats?.receiveToPresentMs, ' ms')
                }}
              </div>
            </n-space>
          </div>
          <div class="link-item">
            呈现统计为合成器提交帧；回调帧间隔可能包含漏回调和静止时间，不能替代屏幕实测。
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">网络往返：</div>
              <div class="item">
                {{ rtcRtt }}
              </div>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">接收码率：</div>
              <div class="item">
                {{ formatMetric(receivedVideoStats?.bitrateMbps, ' Mbps') }}
              </div>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">缓冲等待：</div>
              <div class="item">
                {{ formatMetric(receivedVideoStats?.jitterBufferMs, ' ms') }}
              </div>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">单帧解码：</div>
              <div class="item">
                {{ formatMetric(receivedVideoStats?.decodeMs, ' ms') }}
              </div>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">采样丢帧：</div>
              <div class="item">
                {{ formatMetric(receivedVideoStats?.droppedFrames, ' 帧') }}
              </div>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">连接路径：</div>
              <div class="item">
                {{ receivedVideoStats?.connectionPath || '测量中' }}
              </div>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label">丢包：</div>
              <div class="item">
                {{ rtcLoss }}
              </div>
            </n-space>
          </div>
          <div class="link-item">
            <n-space>
              <div class="link-label"></div>
              <div
                class="item btn"
                @click="handleClose"
              >
                关闭连接
              </div>
            </n-space>
          </div>
        </div>
      </div>

      <nav
        v-if="isRemoteAndroid"
        class="android-controls"
        aria-label="安卓系统操作"
        @mousedown.stop
        @mouseup.stop
      >
        <button
          v-for="action in androidActions"
          :key="action.id"
          type="button"
          class="control-button"
          :disabled="!canControlAndroid"
          :title="`${action.description} · Alt+Shift+${action.key}`"
          @click="handleAndroidAction(action.id)"
        >
          {{ action.label }}
        </button>
      </nav>
    </header>

    <div
      ref="videoWrapRef"
      class="remote-video"
      :class="{ 'hide-cursor': !showCursor, watch: isWatchMode }"
      @mousedown="handleMouseDown"
      @mousemove="handleMouseMove"
      @dblclick="handleDoublelclick"
      @contextmenu="handleContextmenu"
    ></div>

    <div
      v-if="loading"
      class="loading"
    >
      正在连接...
    </div>
  </div>
</template>

<script lang="ts" setup>
import {
  computeBox,
  copyToClipBoard,
  getRandomString,
  windowReload,
} from 'billd-utils';
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ENGLISH_LETTER, NUT_KEY_MAP, WINDOW_ID_ENUM } from '@/constant';
import { IPC_EVENT } from '@/event';
import { useIpcRendererSend } from '@/hooks/use-ipcRendererSend';
import { useRTCParams } from '@/hooks/use-rtcParams';
import { useTip } from '@/hooks/use-tip';
import { useWebsocket } from '@/hooks/use-websocket';
import router, { routerName } from '@/router';
import { useAppStore } from '@/store/app';
import { useNetworkStore } from '@/store/network';
import {
  BilldDeskBehaviorEnum,
  WsBilldDeskBehaviorType,
  WsBilldDeskStartRemote,
  WsBilldDeskStartRemoteResult,
  WsChangeAudioContentHintType,
  WsChangeMaxBitrateType,
  WsChangeMaxFramerateType,
  WsChangeResolutionRatioType,
  WsChangeVideoContentHintType,
  WsConnectStatusEnum,
  WsMsgTypeEnum,
} from '@/types/websocket';
import {
  ipcRenderer,
  ipcRendererInvoke,
  ipcRendererSend,
  videoFullBox,
} from '@/utils';

const route = useRoute();
const appStore = useAppStore();
const networkStore = useNetworkStore();

const {
  initWs,
  remoteDeskUserUuid,
  remoteDeskUserPassword,
  deskUserUuid,
  deskUserPassword,
  connectStatus,
} = useWebsocket();

const {
  maxBitrate,
  maxFramerate,
  resolutionRatio,
  audioContentHint,
  videoContentHint,
} = useRTCParams();

const { handleOpenDevTools } = useIpcRendererSend();

const titlebarHeight = ref(50);
const loading = ref(true);
const isWatchMode = ref(false);
const showCursor = ref(true);
const receiverId = ref('');
const loopBilldDeskUpdateUserTimer = ref();
const showDetail = ref(false);
const controlBarRef = ref<HTMLElement>();
const detailButtonRef = ref<HTMLButtonElement>();
// Keep fine text at the source resolution, with enough bitrate for transitions.
const currentMaxBitrate = ref(maxBitrate.value[7].value);
const currentMaxFramerate = ref(maxFramerate.value[4].value);
const currentResolutionRatio = ref(resolutionRatio.value[4].value);
const currentVideoContentHint = ref(videoContentHint.value[3].value);
const currentAudioContentHint = ref(audioContentHint.value[0].value);

let clickTimer: any;
let isLongClick = false;
let remoteMouseDown = false;
const videoList = ref<HTMLVideoElement[]>([]);
const videoWrapRef = ref<HTMLDivElement>();
const videoSizes = new WeakMap<
  HTMLVideoElement,
  ReturnType<typeof videoFullBox>
>();
const windowId = ref(WINDOW_ID_ENUM.webrtc);
const roomId = ref('');
const videoMap = ref(new Map());
const mySocketId = computed(() => {
  return networkStore.wsMap.get(roomId.value)?.socketIo?.id || '';
});

const rtcRtt = computed(() => {
  const arr: string[] = [];
  networkStore.rtcMap.forEach((rtc) => {
    arr.push(formatMetric(rtc.rtt, ' ms'));
  });
  return arr.join();
});

const rtcLoss = computed(() => {
  const arr: string[] = [];
  networkStore.rtcMap.forEach((rtc) => {
    arr.push(formatMetric(rtc.loss, '%'));
  });
  return arr.join();
});

const receivedVideoStats = computed(
  () => networkStore.rtcMap.get(receiverId.value)?.videoStats
);

const androidActions = [
  { id: 'back', label: '返回', key: 'B', description: '返回上一页' },
  { id: 'home', label: '桌面', key: 'H', description: '回到桌面' },
  { id: 'recents', label: '多任务', key: 'R', description: '打开最近任务' },
  { id: 'notifications', label: '通知栏', key: 'N', description: '展开通知栏' },
  {
    id: 'quickSettings',
    label: '快捷设置',
    key: 'S',
    description: '展开状态栏快捷设置',
  },
  { id: 'dismissShade', label: '收起', key: 'C', description: '收起系统栏' },
];
const isRemoteAndroid = computed(
  () => networkStore.rtcMap.get(receiverId.value)?.remotePlatform === 'android'
);
const canControlAndroid = computed(
  () =>
    isRemoteAndroid.value &&
    !isWatchMode.value &&
    networkStore.rtcMap.get(receiverId.value)?.dataChannel?.readyState ===
      'open'
);

function handleAndroidAction(action: string) {
  if (!canControlAndroid.value) return;
  if (action === 'back')
    networkStore.rtcMap.get(receiverId.value)?.sampleInteraction('back');
  networkStore.rtcMap.get(receiverId.value)?.dataChannelSend({
    requestId: getRandomString(8),
    msgType: WsMsgTypeEnum.androidAction,
    data: { action },
  });
}

function androidShortcut(event: KeyboardEvent) {
  if (
    !isRemoteAndroid.value ||
    !event.altKey ||
    !event.shiftKey ||
    event.ctrlKey ||
    event.metaKey
  )
    return;
  const target = event.target;
  if (
    target instanceof HTMLElement &&
    (target.isContentEditable ||
      /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName))
  )
    return;
  return androidActions.find((action) => event.code === `Key${action.key}`);
}

function formatMetric(value: number | null | undefined, unit: string) {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
    ? `${Number(value.toFixed(2))}${unit}`
    : '测量中';
}

const initVideo = ref(true);
const clickNum = ref(0);
const loopGetSettingsTimer = ref();
const loopReconnectTimer = ref();
const videoSettings = ref<MediaTrackSettings>();

onMounted(() => {
  console.log('webrtc页面');
  console.log(route.query);
  if (route.query.deskUserUuid !== undefined) {
    deskUserUuid.value = String(route.query.deskUserUuid);
  } else {
    window.$message.error('设备代码为空');
    return;
  }
  if (route.query.deskUserPassword !== undefined) {
    deskUserPassword.value = String(route.query.deskUserPassword);
  } else {
    window.$message.error('临时密码为空');
    return;
  }
  if (route.query.remoteDeskUserUuid !== undefined) {
    remoteDeskUserUuid.value = String(route.query.remoteDeskUserUuid);
  } else {
    window.$message.error('远程设备代码为空');
    return;
  }
  if (route.query.remoteDeskUserPassword !== undefined) {
    remoteDeskUserPassword.value = String(route.query.remoteDeskUserPassword);
  } else {
    window.$message.error('远程设备密码为空');
    return;
  }
  if (route.query.roomId !== undefined) {
    roomId.value = String(route.query.roomId);
  }
  if (route.query.maxBitrate !== undefined) {
    currentMaxBitrate.value = Number(route.query.maxBitrate);
  }
  if (route.query.maxFramerate !== undefined) {
    currentMaxFramerate.value = Number(route.query.maxFramerate);
  }
  if (route.query.resolutionRatio !== undefined) {
    currentResolutionRatio.value = Number(route.query.resolutionRatio);
  }
  if (route.query.videoContentHint !== undefined) {
    currentVideoContentHint.value = String(route.query.videoContentHint);
  }
  if (route.query.audioContentHint !== undefined) {
    currentAudioContentHint.value = String(route.query.audioContentHint);
  }
  init();
  window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
  clearInterval(loopBilldDeskUpdateUserTimer.value);
  clearInterval(loopGetSettingsTimer.value);
  videoWrapRef.value?.removeEventListener('wheel', handleMouseWheel);
  window.removeEventListener('mouseup', handleMouseUp);
  window.removeEventListener('resize', handleResize);
  window.removeEventListener('keydown', handleKeyDown);
  window.removeEventListener('keydown', handleKeyCombination);

  window.removeEventListener('keyup', handleKeyUp);
  networkStore.removeAllWsAndRtc();
});

function handleOpenDebug() {
  if (clickNum.value < 5) {
    clickNum.value += 1;
    setTimeout(() => {
      clickNum.value = 1;
    }, 3000);
  } else {
    appStore.showDebug = true;
  }
}

function handleKeyCombination(event: KeyboardEvent) {
  if (isWatchMode.value) return;
  if (event.ctrlKey) {
    const key = event.key.toLowerCase();
    ENGLISH_LETTER.forEach((item) => {
      if (item === key) {
        console.log(`Ctrl+${key} 被按下`);
        networkStore.rtcMap
          .get(receiverId.value)
          ?.dataChannelSend<WsBilldDeskBehaviorType['data']>({
            requestId: getRandomString(8),
            msgType: WsMsgTypeEnum.billdDeskBehavior,
            data: {
              roomId: roomId.value,
              sender: mySocketId.value,
              receiver: receiverId.value,
              type: BilldDeskBehaviorEnum.keyboardPressKey,
              key: [
                NUT_KEY_MAP.ControlLeft,
                NUT_KEY_MAP[event.code] ||
                  NUT_KEY_MAP[event.key.toUpperCase()] ||
                  event.key,
              ],
              x: 0,
              y: 0,
              amount: 0,
            },
          });
      }
    });
  }
  if (event.metaKey) {
    const key = event.key.toLowerCase();
    ENGLISH_LETTER.forEach((item) => {
      if (item === key) {
        console.log(`MetaKey+${key} 被按下`);
        networkStore.rtcMap
          .get(receiverId.value)
          ?.dataChannelSend<WsBilldDeskBehaviorType['data']>({
            requestId: getRandomString(8),
            msgType: WsMsgTypeEnum.billdDeskBehavior,
            data: {
              roomId: roomId.value,
              sender: mySocketId.value,
              receiver: receiverId.value,
              type: BilldDeskBehaviorEnum.keyboardPressKey,
              key: [
                NUT_KEY_MAP.MetaLeft,
                NUT_KEY_MAP[event.code] ||
                  NUT_KEY_MAP[event.key.toUpperCase()] ||
                  event.key,
              ],
              x: 0,
              y: 0,
              amount: 0,
            },
          });
      }
    });
  }
}

function closeDetail() {
  showDetail.value = false;
  detailButtonRef.value?.focus();
}

function handleToolbarKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape' && showDetail.value) {
    event.preventDefault();
    closeDetail();
    return;
  }
  if (!showDetail.value) {
    const action = androidShortcut(event);
    if (action) {
      event.preventDefault();
      if (!event.repeat) handleAndroidAction(action.id);
    }
  }
}

function handleResize() {
  videoList.value.forEach((item) => {
    handleVideoElSize(item, false);
  });
}

function init() {
  handleInitIpcRendererOn();
  handleInitIpcRendererSend();
  handleLoopBilldDeskUpdateUserTimer();
  videoWrapRef.value?.addEventListener('wheel', handleMouseWheel);
  window.addEventListener('mouseup', handleMouseUp);
  window.addEventListener('keydown', handleKeyDown);
  window.addEventListener('keydown', handleKeyCombination);
  window.addEventListener('keyup', handleKeyUp);
  initWs({
    roomId: roomId.value,
    isAnchor: false,
    isRemoteDesk: true,
  });
  loopGetSettings();
}

watch(
  () => connectStatus.value,
  (newval) => {
    console.log('connectStatus', newval);
    if (newval === WsConnectStatusEnum.connect) {
      clearInterval(loopReconnectTimer.value);
      handleWsMsg();
    } else if (newval === WsConnectStatusEnum.disconnect) {
      console.log('disconnect');
    }
  },
  { immediate: true }
);

watch(
  [
    receiverId,
    () => networkStore.rtcMap.get(receiverId.value)?.dataChannel?.readyState,
  ],
  ([, state]) => {
    if (state !== 'open') return;
    const rtc = networkStore.rtcMap.get(receiverId.value);
    [
      { msgType: WsMsgTypeEnum.changeMaxBitrate, val: currentMaxBitrate.value },
      {
        msgType: WsMsgTypeEnum.changeMaxFramerate,
        val: currentMaxFramerate.value,
      },
      {
        msgType: WsMsgTypeEnum.changeResolutionRatio,
        val: currentResolutionRatio.value,
      },
      {
        msgType: WsMsgTypeEnum.changeVideoContentHint,
        val: currentVideoContentHint.value,
      },
      {
        msgType: WsMsgTypeEnum.changeAudioContentHint,
        val: currentAudioContentHint.value,
      },
    ].forEach(({ msgType, val }) => {
      rtc?.dataChannelSend({
        requestId: getRandomString(8),
        msgType,
        data: { live_room_id: Number(roomId.value), val },
      });
    });
  },
  { immediate: true }
);

watch(
  () => currentMaxBitrate.value,
  (newval) => {
    networkStore.rtcMap
      .get(receiverId.value)
      ?.dataChannelSend<WsChangeMaxBitrateType['data']>({
        requestId: getRandomString(8),
        msgType: WsMsgTypeEnum.changeMaxBitrate,
        data: {
          live_room_id: Number(roomId.value),
          val: newval,
        },
      });
  }
);
watch(
  () => currentMaxFramerate.value,
  (newval) => {
    networkStore.rtcMap
      .get(receiverId.value)
      ?.dataChannelSend<WsChangeMaxFramerateType['data']>({
        requestId: getRandomString(8),
        msgType: WsMsgTypeEnum.changeMaxFramerate,
        data: {
          live_room_id: Number(roomId.value),
          val: newval,
        },
      });
  }
);
watch(
  () => currentResolutionRatio.value,
  (newval) => {
    networkStore.rtcMap
      .get(receiverId.value)
      ?.dataChannelSend<WsChangeResolutionRatioType['data']>({
        requestId: getRandomString(8),
        msgType: WsMsgTypeEnum.changeResolutionRatio,
        data: {
          live_room_id: Number(roomId.value),
          val: newval,
        },
      });
  }
);
watch(
  () => currentVideoContentHint.value,
  (newval) => {
    networkStore.rtcMap
      .get(receiverId.value)
      ?.dataChannelSend<WsChangeVideoContentHintType['data']>({
        requestId: getRandomString(8),
        msgType: WsMsgTypeEnum.changeVideoContentHint,
        data: {
          live_room_id: Number(roomId.value),
          val: newval,
        },
      });
  }
);
watch(
  () => currentAudioContentHint.value,
  (newval) => {
    networkStore.rtcMap
      .get(receiverId.value)
      ?.dataChannelSend<WsChangeAudioContentHintType['data']>({
        requestId: getRandomString(8),
        msgType: WsMsgTypeEnum.changeAudioContentHint,
        data: {
          live_room_id: Number(roomId.value),
          val: newval,
        },
      });
  }
);

function handleLoopBilldDeskUpdateUserTimer() {
  clearInterval(loopBilldDeskUpdateUserTimer.value);
  loopBilldDeskUpdateUserTimer.value = setInterval(() => {
    networkStore.wsMap.get(roomId.value)?.send<WsBilldDeskStartRemote['data']>({
      requestId: getRandomString(8),
      msgType: WsMsgTypeEnum.billdDeskUpdateUser,
      data: {
        roomId: roomId.value,
        sender: mySocketId.value,
        receiver: '',
        maxBitrate: currentMaxBitrate.value,
        maxFramerate: currentMaxFramerate.value,
        resolutionRatio: currentResolutionRatio.value,
        videoContentHint: currentVideoContentHint.value,
        audioContentHint: currentAudioContentHint.value,
        deskUserUuid: deskUserUuid.value,
        deskUserPassword: deskUserPassword.value,
        remoteDeskUserUuid: remoteDeskUserUuid.value,
        remoteDeskUserPassword: remoteDeskUserPassword.value,
      },
    });
  }, 1000 * 2);
}

function handleWsMsg() {
  const ws = networkStore.wsMap.get(roomId.value);
  // 收到billdDeskStartRemoteResult
  ws?.socketIo?.on(
    WsMsgTypeEnum.billdDeskStartRemoteResult,
    (data: WsBilldDeskStartRemoteResult['data']) => {
      console.log('收到billdDeskStartRemoteResult', data);
      if (data.code !== 0) {
        useTip({
          content: data.msg,
          hiddenCancel: true,
          hiddenClose: true,
        });
      } else {
        if (data.data) {
          receiverId.value = data.data.receiver;
          appStore.remoteDesk.set(data.data.receiver, {
            deskUserUuid: data.data.deskUserUuid,
            remoteDeskUserUuid: data.data.remoteDeskUserUuid,
            audioContentHint: data.data.audioContentHint,
            videoContentHint: data.data.videoContentHint,
            sender: data.data.sender,
            isClose: false,
            maxBitrate: data.data.maxBitrate,
            maxFramerate: data.data.maxFramerate,
            resolutionRatio: data.data.resolutionRatio,
          });
        }
      }
    }
  );
  ws?.send<WsBilldDeskStartRemote['data']>({
    requestId: getRandomString(8),
    msgType: WsMsgTypeEnum.billdDeskStartRemote,
    data: {
      roomId: roomId.value,
      sender: mySocketId.value,
      receiver: '',
      maxBitrate: currentMaxBitrate.value,
      maxFramerate: currentMaxFramerate.value,
      resolutionRatio: currentResolutionRatio.value,
      videoContentHint: currentVideoContentHint.value,
      audioContentHint: currentAudioContentHint.value,
      deskUserUuid: deskUserUuid.value,
      deskUserPassword: deskUserPassword.value,
      remoteDeskUserUuid: remoteDeskUserUuid.value,
      remoteDeskUserPassword: remoteDeskUserPassword.value,
    },
  });
}

function handleInitIpcRendererSend() {}

function handleInitIpcRendererOn() {}

function loopGetSettings() {
  clearInterval(loopGetSettingsTimer.value);
  loopGetSettingsTimer.value = setInterval(() => {
    networkStore.rtcMap
      .get(receiverId.value)
      ?.localStream?.getVideoTracks()
      .forEach((item) => {
        videoSettings.value = item.getSettings();
      });
  }, 1000);
}

function handleMouseWheel(event: WheelEvent) {
  if (isWatchMode.value) return;
  event.preventDefault();
  if (event.deltaY > 0) {
    networkStore.rtcMap
      .get(receiverId.value)
      ?.dataChannelSend<WsBilldDeskBehaviorType['data']>({
        requestId: getRandomString(8),
        msgType: WsMsgTypeEnum.billdDeskBehavior,
        data: {
          roomId: roomId.value,
          sender: mySocketId.value,
          receiver: receiverId.value,
          type: BilldDeskBehaviorEnum.scrollDown,
          key: [0],
          x: 0,
          y: 0,
          amount: Math.abs(event.deltaY),
        },
      });
  } else if (event.deltaY < 0) {
    networkStore.rtcMap
      .get(receiverId.value)
      ?.dataChannelSend<WsBilldDeskBehaviorType['data']>({
        requestId: getRandomString(8),
        msgType: WsMsgTypeEnum.billdDeskBehavior,
        data: {
          roomId: roomId.value,
          sender: mySocketId.value,
          receiver: receiverId.value,
          type: BilldDeskBehaviorEnum.scrollUp,
          key: [0],
          x: 0,
          y: 0,
          amount: Math.abs(event.deltaY),
        },
      });
  }
  if (event.deltaX > 0) {
    networkStore.rtcMap
      .get(receiverId.value)
      ?.dataChannelSend<WsBilldDeskBehaviorType['data']>({
        requestId: getRandomString(8),
        msgType: WsMsgTypeEnum.billdDeskBehavior,
        data: {
          roomId: roomId.value,
          sender: mySocketId.value,
          receiver: receiverId.value,
          type: BilldDeskBehaviorEnum.scrollRight,
          key: [0],
          x: 0,
          y: 0,
          amount: Math.abs(event.deltaX),
        },
      });
  } else if (event.deltaX < 0) {
    networkStore.rtcMap
      .get(receiverId.value)
      ?.dataChannelSend<WsBilldDeskBehaviorType['data']>({
        requestId: getRandomString(8),
        msgType: WsMsgTypeEnum.billdDeskBehavior,
        data: {
          roomId: roomId.value,
          sender: mySocketId.value,
          receiver: receiverId.value,
          type: BilldDeskBehaviorEnum.scrollLeft,
          key: [0],
          x: 0,
          y: 0,
          amount: Math.abs(event.deltaX),
        },
      });
  }
}

function handleClose() {
  networkStore.removeAllWsAndRtc();
  if (!ipcRenderer) {
    router.push({ name: routerName.remote });
    setTimeout(() => {
      windowReload();
    }, 300);
  }
}

function handleVideoElSize(videoEl, setWindowBounds = false) {
  if (!videoWrapRef.value) return;
  const barHeight = controlBarRef.value?.offsetHeight || 0;
  let clientWidth = videoWrapRef.value.clientWidth;
  let clientHeight = videoWrapRef.value.clientHeight;
  if (ipcRenderer && initVideo.value) {
    initVideo.value = false;
    clientWidth = window.screen.availWidth;
    clientHeight = window.screen.availHeight - titlebarHeight.value - barHeight;
  }

  const res = computeBox({
    width: videoEl.videoWidth,
    height: videoEl.videoHeight,
    maxHeight: clientHeight,
    minHeight: clientHeight,
    maxWidth: clientWidth,
    minWidth: clientWidth,
  });

  const wrapSize = { width: clientWidth, height: clientHeight };
  const videoSize = videoSizes.get(videoEl);
  if (videoSize) {
    videoSize.changeWrapSize(wrapSize);
  } else {
    videoSizes.set(videoEl, videoFullBox({ wrapSize, videoEl }));
  }

  if (res.width && res.height && setWindowBounds) {
    ipcRendererSend({
      windowId: windowId.value,
      channel: IPC_EVENT.setWindowBounds,
      requestId: getRandomString(8),
      data: {
        width: Math.ceil(res.width),
        height: Math.ceil(res.height + titlebarHeight.value + barHeight),
      },
    });
  }
}

watch(
  () => appStore.remoteDesk.size,
  (newval) => {
    if (!newval) {
      networkStore.removeAllWsAndRtc();
    }
  }
);

watch(
  () => networkStore.rtcMap,
  (newVal) => {
    newVal.forEach((item) => {
      if (videoWrapRef.value) {
        if (videoMap.value.has(item.receiver)) {
          if (item.peerConnection?.iceConnectionState === 'connected') {
            loading.value = false;
          }
          return;
        }

        videoMap.value.set(item.receiver, 1);
        item.videoEl.addEventListener('loadedmetadata', async () => {
          const res1 = await ipcRendererInvoke({
            windowId: windowId.value,
            channel: IPC_EVENT.getWindowTitlebarHeight,
            requestId: getRandomString(8),
            data: {},
          });
          if (res1?.code === 0) {
            titlebarHeight.value = res1.data.height;
          }
          handleVideoElSize(item.videoEl, true);
        });
        videoList.value.push(item.videoEl);
        videoWrapRef.value.appendChild(item.videoEl);
      }
    });
  },
  {
    deep: true,
    immediate: true,
  }
);

function handleCopy(str) {
  copyToClipBoard(str);
  window.$message.success('复制成功');
}

function handleKeyDown(event: KeyboardEvent) {
  if (showDetail.value) {
    handleToolbarKeyDown(event);
    return;
  }
  if (isWatchMode.value) return;
  const action = androidShortcut(event);
  if (action) {
    event.preventDefault();
    if (!event.repeat) handleAndroidAction(action.id);
    return;
  }
  if (event.ctrlKey || event.metaKey) {
    return;
  }
  networkStore.rtcMap
    .get(receiverId.value)
    ?.dataChannelSend<WsBilldDeskBehaviorType['data']>({
      requestId: getRandomString(8),
      msgType: WsMsgTypeEnum.billdDeskBehavior,
      data: {
        roomId: roomId.value,
        sender: mySocketId.value,
        receiver: receiverId.value,
        type: BilldDeskBehaviorEnum.keyboardPressKey,
        key: [
          NUT_KEY_MAP[event.code] ||
            NUT_KEY_MAP[event.key.toUpperCase()] ||
            event.key,
        ],
        x: 0,
        y: 0,
        amount: 0,
      },
    });
}

function handleKeyUp(event: KeyboardEvent) {
  if (showDetail.value || isWatchMode.value) return;
  if (androidShortcut(event)) {
    event.preventDefault();
    return;
  }
  if (event.ctrlKey || event.metaKey) {
    return;
  }
  networkStore.rtcMap
    .get(receiverId.value)
    ?.dataChannelSend<WsBilldDeskBehaviorType['data']>({
      requestId: getRandomString(8),
      msgType: WsMsgTypeEnum.billdDeskBehavior,
      data: {
        roomId: roomId.value,
        sender: mySocketId.value,
        receiver: receiverId.value,
        type: BilldDeskBehaviorEnum.keyboardReleaseKey,
        key: [
          NUT_KEY_MAP[event.code] ||
            NUT_KEY_MAP[event.key.toUpperCase()] ||
            event.key,
        ],
        x: 0,
        y: 0,
        amount: 0,
      },
    });
}

function handleDoublelclick() {
  networkStore.rtcMap
    .get(receiverId.value)
    ?.dataChannelSend<WsBilldDeskBehaviorType['data']>({
      requestId: getRandomString(8),
      msgType: WsMsgTypeEnum.billdDeskBehavior,
      data: {
        roomId: roomId.value,
        sender: mySocketId.value,
        receiver: receiverId.value,
        type: BilldDeskBehaviorEnum.doubleClick,
        key: [0],
        x: 0,
        y: 0,
        amount: 0,
      },
    });
}

function handleContextmenu() {
  networkStore.rtcMap
    .get(receiverId.value)
    ?.dataChannelSend<WsBilldDeskBehaviorType['data']>({
      requestId: getRandomString(8),
      msgType: WsMsgTypeEnum.billdDeskBehavior,
      data: {
        roomId: roomId.value,
        sender: mySocketId.value,
        receiver: receiverId.value,
        type: BilldDeskBehaviorEnum.rightClick,
        key: [0],
        x: 0,
        y: 0,
        amount: 0,
      },
    });
}

function handleMouseDown(event: MouseEvent) {
  if (isWatchMode.value || event.button !== 0) return;
  if (event.target === videoWrapRef.value) return;
  networkStore.rtcMap.get(receiverId.value)?.sampleInteraction('click');
  remoteMouseDown = true;
  clickTimer = setTimeout(function () {
    console.log('长按');
    isLongClick = true;
    clearTimeout(clickTimer);
  }, 300);
  // 获取点击相对于视窗的位置
  const clickX = event.clientX;
  const clickY = event.clientY;

  // 获取目标元素的位置和尺寸信息
  // @ts-ignore
  const rect: DOMRect = event.target.getBoundingClientRect();
  // 计算点击位置相对于元素的坐标
  const xInsideElement = clickX - rect.left;
  const yInsideElement = clickY - rect.top;
  const x = (xInsideElement / rect.width) * 1000;
  const y = (yInsideElement / rect.height) * 1000;
  console.log('handleMouseDown', x, y, xInsideElement, yInsideElement);
  networkStore.rtcMap
    .get(receiverId.value)
    ?.dataChannelSend<WsBilldDeskBehaviorType['data']>({
      requestId: getRandomString(8),
      msgType: WsMsgTypeEnum.billdDeskBehavior,
      data: {
        roomId: roomId.value,
        sender: mySocketId.value,
        receiver: receiverId.value,
        key: [0],
        type: BilldDeskBehaviorEnum.pressButtonLeft,
        x,
        y,
        amount: 0,
      },
    });
}

function handleMouseMove(event: MouseEvent) {
  if (event.target === videoWrapRef.value) return;
  // 获取点击相对于视窗的位置
  const clickX = event.clientX;
  const clickY = event.clientY;

  // 获取目标元素的位置和尺寸信息
  // @ts-ignore
  const rect: DOMRect = event.target.getBoundingClientRect();
  // 计算点击位置相对于元素的坐标
  const xInsideElement = clickX - rect.left;
  const yInsideElement = clickY - rect.top;
  const x = (xInsideElement / rect.width) * 1000;
  const y = (yInsideElement / rect.height) * 1000;
  const requestId = getRandomString(8);
  console.log(
    'handleMouseMove',
    requestId,
    x,
    y,
    xInsideElement,
    yInsideElement
  );
  networkStore.rtcMap
    .get(receiverId.value)
    ?.dataChannelSend<WsBilldDeskBehaviorType['data']>({
      requestId,
      msgType: WsMsgTypeEnum.billdDeskBehavior,
      data: {
        roomId: roomId.value,
        sender: mySocketId.value,
        receiver: receiverId.value,
        type: BilldDeskBehaviorEnum.mouseMove,
        key: [0],
        x,
        y,
        amount: 0,
      },
    });
}

function handleMouseUp(event: MouseEvent) {
  if (!remoteMouseDown || event.button !== 0) return;
  remoteMouseDown = false;
  if (clickTimer) {
    clearTimeout(clickTimer);
  }
  // 获取点击相对于视窗的位置
  const clickX = event.clientX;
  const clickY = event.clientY;

  // 获取目标元素的位置和尺寸信息
  const rect = videoList.value[0]?.getBoundingClientRect();
  if (!rect?.width || !rect.height) return;
  // 计算点击位置相对于元素的坐标
  const xInsideElement = clickX - rect.left;
  const yInsideElement = clickY - rect.top;
  const x = Math.max(0, Math.min(1000, (xInsideElement / rect.width) * 1000));
  const y = Math.max(0, Math.min(1000, (yInsideElement / rect.height) * 1000));
  console.log('handleMouseUp', x, y, xInsideElement, yInsideElement);
  networkStore.rtcMap
    .get(receiverId.value)
    ?.dataChannelSend<WsBilldDeskBehaviorType['data']>({
      requestId: getRandomString(8),
      msgType: WsMsgTypeEnum.billdDeskBehavior,
      data: {
        roomId: roomId.value,
        sender: mySocketId.value,
        receiver: receiverId.value,
        key: [0],
        type: isLongClick
          ? BilldDeskBehaviorEnum.releaseButtonLeft
          : BilldDeskBehaviorEnum.releaseButtonLeft,
        x,
        y,
        amount: 0,
      },
    });
  isLongClick = false;
}
</script>

<style lang="scss" scoped>
.webrtc-wrap {
  overflow: hidden;
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  .control-bar {
    position: relative;
    z-index: 999;
    display: flex;
    align-items: center;
    flex-shrink: 0;
    gap: 6px;
    box-sizing: border-box;
    height: 40px;
    padding: 4px 8px;
    border-bottom: 1px solid #e5e7eb;
    background: #fff;
  }
  .control-button {
    flex-shrink: 0;
    min-height: 30px;
    padding: 0 8px;
    border: 0;
    border-radius: 6px;
    background: transparent;
    color: #202938;
    font: inherit;
    font-size: 12px;
    white-space: nowrap;
    cursor: pointer;
    &:hover:not(:disabled),
    &[aria-expanded='true'] {
      background: #eef2f7;
    }
    &:active:not(:disabled) {
      background: #dce4ef;
    }
    &:focus-visible {
      outline: 2px solid #2563eb;
      outline-offset: 1px;
    }
    &:disabled {
      color: #687386;
      cursor: not-allowed;
    }
  }
  .android-controls {
    display: flex;
    align-items: center;
    gap: 2px;
    min-width: 0;
    margin-left: auto;
    overflow-x: auto;
  }
  .connection-details {
    flex-shrink: 0;
    .info {
      position: absolute;
      top: calc(100% + 4px);
      left: 8px;
      display: none;
      box-sizing: border-box;
      padding: 10px;
      width: 800px;
      max-width: calc(100vw - 16px);
      max-height: calc(100vh - 52px);
      overflow-y: auto;
      border: 1px solid #e5e7eb;
      border-radius: 8px;
      background-color: white;
      .details-heading {
        position: sticky;
        top: -10px;
        z-index: 10;
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 4px 0;
        background: #fff;
        font-weight: 500;
      }
      box-shadow: rgba(100, 100, 111, 0.2) 0px 7px 29px 0px;
      .debug-area {
        position: absolute;
        bottom: 0;
        left: 0;
        width: 30px;
        height: 30px;
      }
      .debug-info {
        position: absolute;
        right: 0;
        bottom: 0;
        z-index: 99;
        padding-right: 5px;
        font-size: 12px;
        .item {
          cursor: pointer;
        }
        .link {
          color: red;
          cursor: pointer;
        }
      }
      .link-config {
        position: relative;
        z-index: 9;
        margin-top: 10px;
        .link-item {
          margin-bottom: 4px;
          .link-label {
            width: 80px;
            text-align: right;
          }
          .btn {
            padding: 2px 10px;
            border-radius: 5px;
            background-color: red;
            color: white;
            text-align: center;
            cursor: pointer;
          }
        }
      }
      &.show {
        display: block;
      }
    }
  }
  .remote-video {
    display: flex;
    flex: 1;
    align-items: center;
    justify-content: center;
    min-height: 0;
    overflow: hidden;
    line-height: 0;
    &.hide-cursor {
      cursor: none;
    }
    &.watch {
      pointer-events: none;
    }
  }
  .loading {
    position: fixed;
    top: 50%;
    left: 50%;
    font-size: 30px;
    transform: translate(-50%, -50%);
  }
}
</style>

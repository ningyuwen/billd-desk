<template>
  <div class="remote-wrap">
    <div class="container">
      <header class="page-heading">
        <h1>远程控制</h1>
        <span
          class="connection-state"
          :class="{ online: connectStatus === WsConnectStatusEnum.connect }"
          role="status"
        >
          {{
            connectStatus === WsConnectStatusEnum.connect
              ? '服务器已连接'
              : connectStatus === WsConnectStatusEnum.disconnect
                ? '服务器未连接'
                : '正在连接服务器…'
          }}
        </span>
      </header>
      <section
        class="remote-device"
        aria-labelledby="connect-heading"
      >
        <h2 id="connect-heading">连接其他设备</h2>
        <label
          class="txt"
          for="remote-code"
          >对方设备代码</label
        >
        <form
          class="info"
          @submit.prevent="startRemote"
        >
          <div
            v-on-click-outside="handleClickOutside"
            class="ipt-wrap"
          >
            <div class="ipt-top">
              <input
                id="remote-code"
                v-model="cacheStore.remoteDeskUserUuid"
                type="text"
                class="ipt"
                placeholder="输入设备代码"
                maxlength="8"
                autocomplete="off"
              />
              <button
                ref="arrowDownRef"
                class="arrow-down"
                :class="{ active: showLinkDeviceList }"
                type="button"
                aria-label="显示最近连接的设备"
                :aria-expanded="showLinkDeviceList"
                aria-controls="recent-device-options"
                @click="showLinkDeviceList = !showLinkDeviceList"
              ></button>
            </div>
            <div class="ipt-bottom">
              <div
                v-if="showLinkDeviceList"
                id="recent-device-options"
                ref="linkDeviceListRef"
                class="link-device-list"
              >
                <div
                  v-for="item in cacheStore.linkDeviceList.slice().reverse()"
                  :key="item.remoteDeskUserUuid"
                  class="link-device-item"
                >
                  <button
                    class="history-code"
                    type="button"
                    @click="
                      changeRemoteDeskUserUuid(item);
                      showLinkDeviceList = false;
                    "
                  >
                    {{ item.remoteDeskUserUuid }}
                  </button>
                  <button
                    class="history-remove"
                    type="button"
                    :aria-label="`移除设备 ${item.remoteDeskUserUuid} 的连接记录`"
                    @click="handleDelLinkDeviceList(item)"
                  >
                    移除
                  </button>
                </div>
                <div
                  v-if="!cacheStore.linkDeviceList.length"
                  class="null"
                >
                  暂无连接记录
                </div>
              </div>
            </div>
          </div>
          <button
            class="btn"
            type="submit"
            :disabled="!cacheStore.remoteDeskUserUuid.trim() || loading"
            :aria-busy="loading"
          >
            {{ loading ? '连接中…' : '连接' }}
          </button>
        </form>
        <details
          v-if="!appStore.remoteDesk.size"
          class="quality-settings"
        >
          <summary>
            画质设置
            <span
              >{{ currentResolutionRatio }}P /
              {{ currentMaxFramerate }} 帧</span
            >
          </summary>
          <div class="link-config">
            <div class="link-item">
              <span
                id="bitrate-label"
                class="link-label"
                >码率上限</span
              >
              <n-select
                v-model:value="currentMaxBitrate"
                aria-labelledby="bitrate-label"
                :options="
                  maxBitrate.map((item) => ({
                    ...item,
                    label:
                      item.value >= 1000
                        ? `${item.value / 1000} Mbps`
                        : `${item.value} kbps`,
                  }))
                "
              />
            </div>
            <div class="link-item">
              <span
                id="framerate-label"
                class="link-label"
                >帧率</span
              >
              <n-select
                v-model:value="currentMaxFramerate"
                aria-labelledby="framerate-label"
                :options="maxFramerate"
              />
            </div>
            <div class="link-item">
              <span
                id="resolution-label"
                class="link-label"
                >分辨率</span
              >
              <n-select
                v-model:value="currentResolutionRatio"
                aria-labelledby="resolution-label"
                :options="resolutionRatio"
              />
            </div>
            <div class="link-item">
              <span
                id="video-label"
                class="link-label"
                >视频内容</span
              >
              <n-select
                v-model:value="currentVideoContentHint"
                aria-labelledby="video-label"
                :options="videoContentHint"
              />
            </div>
            <div class="link-item">
              <span
                id="audio-label"
                class="link-label"
                >音频内容</span
              >
              <n-select
                v-model:value="currentAudioContentHint"
                aria-labelledby="audio-label"
                :options="audioContentHint"
              />
            </div>
          </div>
        </details>
      </section>
      <section
        class="local-device"
        aria-labelledby="local-heading"
      >
        <h2 id="local-heading">共享本机</h2>
        <div class="info">
          <div>
            <div class="txt">此设备代码</div>
            <div class="code-info">
              <span class="code">{{
                cacheStore.deskUserUuid || '连接中…'
              }}</span>
              <button
                class="ico copy"
                type="button"
                title="复制设备代码和临时密码"
                aria-label="复制设备代码和临时密码"
                @click="handleCopyRemoteInfo"
              ></button>
              <button
                class="ico refresh"
                type="button"
                title="重置设备代码"
                aria-label="重置设备代码"
                @click="handleResetDeskuuid"
              ></button>
            </div>
          </div>
          <div>
            <div class="txt">临时密码</div>
            <div class="code-info">
              <span class="code password">{{
                cacheStore.hidePwd ? '••••••••' : cacheStore.deskUserPassword
              }}</span>
              <button
                class="ico eye"
                :class="{ hide: cacheStore.hidePwd }"
                type="button"
                :title="cacheStore.hidePwd ? '显示密码' : '隐藏密码'"
                :aria-label="cacheStore.hidePwd ? '显示密码' : '隐藏密码'"
                @click="cacheStore.hidePwd = !cacheStore.hidePwd"
              ></button>
              <button
                class="ico edit"
                type="button"
                title="修改临时密码"
                aria-label="修改临时密码"
                @click="handleUpdatePassword"
              ></button>
            </div>
          </div>
        </div>
        <div
          v-if="appStore.remoteDesk.size"
          class="list"
          aria-live="polite"
        >
          <div
            v-for="(item, key) in appStore.remoteDesk"
            :key="key"
            class="item"
          >
            <span>正在被 {{ item[1].deskUserUuid }} 控制</span>
            <button
              class="del"
              type="button"
              @click="handleDel(item[1].sender)"
            >
              断开
            </button>
          </div>
        </div>
      </section>
      <MiniProgramHost v-if="ipcRenderer" />
    </div>

    <div
      v-if="appStore.showDebug"
      class="debug-info"
    >
      <div>
        <span>窗口Id：</span>
        <span
          class="link"
          @click="handleCopy(WINDOW_ID_ENUM.remote)"
        >
          {{ WINDOW_ID_ENUM.remote }}
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

      <div>
        <span>调试地址：</span>
        <input
          v-model="debugUrl"
          type="text"
        />
        <button @click="changeDebugUrl">确定</button>
      </div>
    </div>

    <PwdModalCpt
      v-if="showPwdModalCpt"
      :uuid="cacheStore.remoteDeskUserUuid"
      :pwd="pwd"
      :err-msg="errMsg"
      @close="handleClose"
      @confirm="handleConfirm"
    ></PwdModalCpt>
  </div>
</template>

<script lang="ts" setup>
import { vOnClickOutside } from '@vueuse/components';
import { copyToClipBoard, getRandomString, windowReload } from 'billd-utils';
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import {
  fetchDeskUserCreate,
  fetchDeskUserLinkVerify,
  fetchDeskUserLogin,
  fetchDeskUserUpdateByUuid,
  fetchFindReceiverByUuid,
} from '@/api/deskUser';
import { WINDOW_ID_ENUM } from '@/constant';
import { IPC_EVENT } from '@/event';
import { useIpcRendererSend } from '@/hooks/use-ipcRendererSend';
import { useRTCParams } from '@/hooks/use-rtcParams';
import { useTip } from '@/hooks/use-tip';
import { useWebsocket } from '@/hooks/use-websocket';
import { useWebRtcRemoteDesk } from '@/hooks/webrtc/remoteDesk';
import { IIpcRendererData } from '@/interface';
import router, { routerName } from '@/router';
import { useAppStore } from '@/store/app';
import { usePiniaCacheStore } from '@/store/cache';
import { useNetworkStore } from '@/store/network';
import {
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
  createNullVideo,
  handlConstraints,
  ipcRenderer,
  ipcRendererInvoke,
  ipcRendererOn,
  ipcRendererSend,
  setAudioTrackContentHints,
  setVideoTrackContentHints,
} from '@/utils';
import { WebRTCClass } from '@/utils/network/webRTC';
import MiniProgramHost from '@/views/remote/miniProgramHost.vue';
import PwdModalCpt from '@/views/remote/pwdModal.vue';

defineOptions({ name: 'RemotePage' });

const route = useRoute();
const appStore = useAppStore();
const networkStore = useNetworkStore();
const cacheStore = usePiniaCacheStore();

const { updateWebRtcRemoteDeskConfig, webRtcRemoteDesk } =
  useWebRtcRemoteDesk();
const { initWs, connectStatus } = useWebsocket();
const {
  maxBitrate,
  maxFramerate,
  resolutionRatio,
  audioContentHint,
  videoContentHint,
} = useRTCParams();
const { handleScreen, handleRtcBilldDeskBehavior } = useIpcRendererSend();

const currentMaxBitrate = ref(maxBitrate.value[7].value);
const currentMaxFramerate = ref(maxFramerate.value[4].value);
const currentResolutionRatio = ref(resolutionRatio.value[4].value);
const currentVideoContentHint = ref(videoContentHint.value[3].value);
const currentAudioContentHint = ref(audioContentHint.value[0].value);
const rtc = ref<WebRTCClass>();
const roomId = ref('');
const receiverId = ref('');
const anchorStream = ref<MediaStream>();
/** 是否控制别人 */
const isControlOther = ref(false);
const loading = ref(false);
const showPwdModalCpt = ref(false);
const showLinkDeviceList = ref(false);
const arrowDownRef = ref();
const linkDeviceListRef = ref();
const pwd = ref('');
const errMsg = ref('');
const chromeMediaSourceId = ref();
const originalPassword = ref('');
const loopBilldDeskUpdateUserTimer = ref();
const suspend = ref('');
const resume = ref('');
const debugUrl = ref('');
const position = ref({ x: 0, y: 0 });
const mySocketId = computed(() => {
  return networkStore.wsMap.get(roomId.value)?.socketIo?.id || '';
});

onUnmounted(() => {
  clearInterval(loopBilldDeskUpdateUserTimer.value);
});

onMounted(() => {
  console.log('home页面');
  console.log('route.query', route.query);
  handleInit();
});

const handleClickOutside: any = [
  () => {
    if (showLinkDeviceList.value) {
      showLinkDeviceList.value = false;
    }
  },
  { ignore: [arrowDownRef] },
];

watch(
  () => networkStore.rtcMap,
  (newval) => {
    newval.forEach((item) => {
      if (!item.cbDataChannel) return;
      // const setting = anchorStream.value?.getVideoTracks()[0].getSettings();
      item.cbDataChannel.onmessage = (event) => {
        const jsondata: {
          msgType: WsMsgTypeEnum;
          requestId: string;
          data: any;
        } = JSON.parse(event.data);
        const { msgType } = jsondata;
        if (msgType === WsMsgTypeEnum.changeMaxBitrate) {
          const { data }: { data: WsChangeMaxBitrateType['data'] } = jsondata;
          currentMaxBitrate.value = data.val;
          rtc.value?.setMaxBitrate(data.val);
        } else if (msgType === WsMsgTypeEnum.changeMaxFramerate) {
          const { data }: { data: WsChangeMaxFramerateType['data'] } = jsondata;
          if (anchorStream.value) {
            currentMaxFramerate.value = data.val;
            handlConstraints({
              frameRate: data.val,
              height: currentResolutionRatio.value,
              stream: anchorStream.value,
            });
          }
        } else if (msgType === WsMsgTypeEnum.changeResolutionRatio) {
          const { data }: { data: WsChangeResolutionRatioType['data'] } =
            jsondata;
          if (anchorStream.value) {
            currentResolutionRatio.value = data.val;
            handlConstraints({
              frameRate: currentMaxFramerate.value,
              height: data.val,
              stream: anchorStream.value,
            });
          }
        } else if (msgType === WsMsgTypeEnum.changeVideoContentHint) {
          const { data }: { data: WsChangeVideoContentHintType['data'] } =
            jsondata;
          if (anchorStream.value) {
            currentVideoContentHint.value = data.val;
            // @ts-ignore
            setVideoTrackContentHints(anchorStream.value, data.val);
          }
        } else if (msgType === WsMsgTypeEnum.changeAudioContentHint) {
          const { data }: { data: WsChangeAudioContentHintType['data'] } =
            jsondata;
          if (anchorStream.value) {
            currentAudioContentHint.value = data.val;
            // @ts-ignore
            setAudioTrackContentHints(anchorStream.value, data.val);
          }
        } else if (msgType === WsMsgTypeEnum.billdDeskBehavior) {
          const { data }: { data: WsBilldDeskBehaviorType['data'] } = jsondata;
          handleRtcBilldDeskBehavior(WINDOW_ID_ENUM.remote, data);
        }
      };
    });
  },
  {
    immediate: true,
    deep: true,
  }
);

watch(
  () => anchorStream.value,
  (newval) => {
    if (newval) {
      appStore.remoteDesk.forEach((item) => {
        if (!item.isClose) {
          handleRTC(item.sender);
        }
      });
    }
  }
);

watch(
  () => appStore.remoteDesk.size,
  (newval) => {
    if (newval) {
      if (!anchorStream.value) {
        handleScreen({ windowId: WINDOW_ID_ENUM.remote });
      }
    } else {
      handleCloseAll();
    }
  },
  {
    immediate: true,
  }
);

watch(
  () => appStore.remoteDesk,
  (newval) => {
    newval.forEach((item) => {
      if (item.isClose) {
        // window.$notification.warning({
        //   content: `${item.sender}远程连接断开`,
        //   duration: 2000,
        // });
        appStore.remoteDesk.delete(item.sender);
        return;
      }
      currentMaxBitrate.value = item.maxBitrate;
      currentMaxFramerate.value = item.maxFramerate;
      currentResolutionRatio.value = item.resolutionRatio;
      currentVideoContentHint.value = item.videoContentHint;
      currentAudioContentHint.value = item.audioContentHint;
    });
  },
  {
    deep: true,
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
        receiver: receiverId.value,
        maxBitrate: currentMaxBitrate.value,
        maxFramerate: currentMaxFramerate.value,
        resolutionRatio: currentResolutionRatio.value,
        videoContentHint: currentVideoContentHint.value,
        audioContentHint: currentAudioContentHint.value,
        deskUserUuid: cacheStore.deskUserUuid,
        deskUserPassword: cacheStore.deskUserPassword,
        remoteDeskUserUuid: cacheStore.remoteDeskUserUuid,
        remoteDeskUserPassword: cacheStore.remoteDeskUserPassword,
      },
    });
  }, 1000 * 2);
}

async function handleInit() {
  handleInitIpcRendererOn();
  handleInitIpcRendererSend();
  await initDeskUser();
  handleLoopBilldDeskUpdateUserTimer();
  initWs({
    roomId: roomId.value,
    isAnchor: false,
    isRemoteDesk: true,
  });
}

async function handleInitIpcRendererSend() {
  ipcRendererSend({
    windowId: WINDOW_ID_ENUM.remote,
    channel: IPC_EVENT.workAreaSize,
    requestId: getRandomString(8),
    data: {},
  });

  const res = await ipcRendererInvoke({
    windowId: WINDOW_ID_ENUM.remote,
    channel: IPC_EVENT.getPrimaryDisplaySize,
    requestId: getRandomString(8),
    data: {},
  });
  if (res?.code === 0) {
    appStore.primaryDisplaySize.width = res.data.width;
    appStore.primaryDisplaySize.height = res.data.height;
  }

  const res1 = await ipcRendererInvoke({
    windowId: WINDOW_ID_ENUM.remote,
    channel: IPC_EVENT.scaleFactor,
    requestId: getRandomString(8),
    data: {},
  });
  if (res1?.code === 0) {
    if (res1?.data?.platform !== 'darwin') {
      appStore.scaleFactor = res1.data.scaleFactor;
    }
  }
}

function responsePowerMonitorSuspend(_event, data: IIpcRendererData) {
  console.log('response_powerMonitorSuspend', data);
  suspend.value = `${new Date().toLocaleString()}-powerMonitorSuspend`;
}
function responsePowerMonitorResume(_event, data: IIpcRendererData) {
  console.log('response_powerMonitorResume', data);
  resume.value = `${new Date().toLocaleString()}-powerMonitorResume`;
  handleCloseAll();
}
function responseGetWindowPosition(_event, data: IIpcRendererData) {
  position.value = data.data.position;
}
function responseWorkAreaSize(_event, data: IIpcRendererData) {
  appStore.workAreaSize = {
    width: data.data.width,
    height: data.data.height,
  };
}

function responseGetScreenStream(_event, data: IIpcRendererData) {
  if (data.code !== 0 || !data.data?.stream?.id) {
    handleScreenCaptureError(
      data.msg || '无法获取屏幕，请检查录屏权限并重试。'
    );
    return;
  }
  chromeMediaSourceId.value = data.data.stream.id;
  handleDesktopStream(data.data.stream.id);
}

function handleInitIpcRendererOn() {
  ipcRendererOn(
    IPC_EVENT.response_powerMonitorSuspend,
    responsePowerMonitorSuspend
  );

  ipcRendererOn(
    IPC_EVENT.response_powerMonitorResume,
    responsePowerMonitorResume
  );

  ipcRendererOn(
    IPC_EVENT.response_getWindowPosition,
    responseGetWindowPosition
  );

  ipcRendererOn(IPC_EVENT.response_workAreaSize, responseWorkAreaSize);

  ipcRendererOn(IPC_EVENT.response_getScreenStream, responseGetScreenStream);
}

async function initDeskUser() {
  try {
    if (!cacheStore.deskUserUuid || !cacheStore.deskUserPassword) {
      const res = await fetchDeskUserCreate();
      if (res.code === 200) {
        cacheStore.deskUserUuid = res.data.uuid!;
        cacheStore.deskUserPassword = res.data.password!;
        originalPassword.value = res.data.password!;
        roomId.value = cacheStore.deskUserUuid;
      }
    } else {
      const res = await fetchDeskUserLogin({
        uuid: cacheStore.deskUserUuid,
        password: cacheStore.deskUserPassword,
      });
      if (res.code === 200) {
        originalPassword.value = cacheStore.deskUserPassword;
        roomId.value = cacheStore.deskUserUuid;
      }
    }
  } catch (error) {
    console.log(error);
  }
}

async function handleUpdatePassword() {
  try {
    cacheStore.deskUserPassword = getRandomString(8);
    // if (cacheStore.deskUserPassword === originalPassword.value) return;
    if (
      cacheStore.deskUserPassword &&
      cacheStore.deskUserPassword.length > 6 &&
      cacheStore.deskUserPassword.length < 12
    ) {
      await fetchDeskUserUpdateByUuid({
        uuid: cacheStore.deskUserUuid!,
        password: originalPassword.value,
        new_password: cacheStore.deskUserPassword!,
      });
      originalPassword.value = cacheStore.deskUserPassword;
      window.$message.success('更新临时密码成功！');
    } else {
      window.$message.warning('临时密码长度要求6-12位！');
    }
  } catch (error) {
    console.log(error);
  }
}

watch(
  () => connectStatus.value,
  (newval) => {
    console.log('connectStatus', newval);
    if (newval === WsConnectStatusEnum.connect) {
      handleWsMsg();
    }
  }
);

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
          if (data.data.receiver === mySocketId.value) {
            appStore.remoteDesk.set(data.data.sender, {
              sender: data.data.sender,
              isClose: false,
              maxBitrate: data.data.maxBitrate,
              maxFramerate: data.data.maxFramerate,
              resolutionRatio: data.data.resolutionRatio,
              videoContentHint: data.data.videoContentHint,
              audioContentHint: data.data.audioContentHint,
              deskUserUuid: data.data.deskUserUuid,
              remoteDeskUserUuid: data.data.remoteDeskUserUuid,
            });
            handleRTC(data.data.sender);
          }
        }
      }
    }
  );
}

async function handleDesktopStream(chromeMediaSourceId) {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: false,
      video: {
        // @ts-ignore
        mandatory: {
          chromeMediaSource: 'desktop',
          chromeMediaSourceId,
          maxFrameRate: 60,
        },
      },
    });
    anchorStream.value = stream;
  } catch (error) {
    console.log(error);
    handleScreenCaptureError(
      '屏幕采集失败，请确认录屏权限已开启；授权后完全退出并重新打开 BilldDesk。'
    );
  }
}

function handleScreenCaptureError(message: string) {
  handleCloseAll();
  appStore.remoteDesk.clear();
  window.$message.error(message);
}

async function handleRTC(receiver) {
  if (!anchorStream.value) return;
  try {
    await handlConstraints({
      frameRate: currentMaxFramerate.value,
      height: currentResolutionRatio.value,
      stream: anchorStream.value,
    });
    setVideoTrackContentHints(
      anchorStream.value,
      // @ts-ignore
      currentVideoContentHint.value
    );
    setAudioTrackContentHints(
      anchorStream.value,
      // @ts-ignore
      currentAudioContentHint.value
    );
    updateWebRtcRemoteDeskConfig({
      roomId: roomId.value,
      anchorStream: anchorStream.value,
    });
    rtc.value = webRtcRemoteDesk.newWebRtc({
      // 因为这里是收到offer，而offer是房主发的，所以此时的data.data.sender是房主；data.data.receiver是接收者；
      // 但是这里的nativeWebRtc的sender，得是自己，不能是data.data.sender，不要混淆
      sender: mySocketId.value,
      receiver,
      videoEl: createNullVideo(),
      deskUserUuid: cacheStore.deskUserUuid,
      remoteDeskUserUuid: cacheStore.remoteDeskUserUuid,
    });
    webRtcRemoteDesk.sendOffer({
      sender: mySocketId.value,
      receiver,
    });
  } catch (error) {
    console.log(error);
  }
}

async function handleCopyRemoteInfo() {
  if (!cacheStore.deskUserUuid || !cacheStore.deskUserPassword) {
    window.$message.warning('设备信息尚未准备好，请稍后再试');
    return;
  }
  const str = `BilldDesk:设备代码:${cacheStore.deskUserUuid};临时密码:${cacheStore.deskUserPassword}`;
  try {
    await navigator.clipboard.writeText(str);
    window.$message.success('已复制邀请信息！');
  } catch (error) {
    window.$message.error('复制邀请信息失败，请重试');
  }
}

function changeDebugUrl() {
  window.location.href = debugUrl.value;
}

async function handleResetDeskuuid() {
  cacheStore.deskUserUuid = '';
  cacheStore.deskUserPassword = '';
  await initDeskUser();
  windowReload();
}

function handleCopy(str) {
  copyToClipBoard(str);
  window.$message.success('复制成功！');
}

function handleClose() {
  showPwdModalCpt.value = false;
  loading.value = false;
}

async function handleConfirm(pwd: string) {
  errMsg.value = '';
  try {
    const res = await fetchDeskUserLinkVerify({
      uuid: cacheStore.remoteDeskUserUuid,
      password: pwd,
    });
    if (res.code == 200) {
      if (res.data.code === 1) {
        isControlOther.value = true;
        showPwdModalCpt.value = false;
        cacheStore.linkDeviceList = cacheStore.linkDeviceList.filter(
          (v) => v.remoteDeskUserUuid !== cacheStore.remoteDeskUserUuid
        );
        cacheStore.linkDeviceList.push({
          remoteDeskUserUuid: cacheStore.remoteDeskUserUuid,
          remoteDeskUserPassword: pwd,
        });
        setTimeout(() => {
          loading.value = false;
        }, 300);
        if (ipcRenderer) {
          ipcRendererSend({
            windowId: 0,
            channel: IPC_EVENT.createWindow,
            requestId: getRandomString(8),
            data: {
              route: routerName.webrtc,
              query: {
                roomId: cacheStore.remoteDeskUserUuid,
                deskUserUuid: cacheStore.deskUserUuid,
                deskUserPassword: cacheStore.deskUserPassword,
                remoteDeskUserUuid: cacheStore.remoteDeskUserUuid,
                remoteDeskUserPassword: pwd,
                receiverId: receiverId.value,
                maxBitrate: currentMaxBitrate.value,
                maxFramerate: currentMaxFramerate.value,
                resolutionRatio: currentResolutionRatio.value,
                audioContentHint: currentAudioContentHint.value,
                videoContentHint: currentVideoContentHint.value,
              },
              windowId: WINDOW_ID_ENUM.webrtc,
              minWidth: 300,
              minHeight: 300,
              useWorkAreaSize: true,
              frame: true,
            },
          });
        } else {
          networkStore.removeAllWsAndRtc();
          setTimeout(() => {
            router.push({
              name: routerName.webrtc,
              query: {
                roomId: cacheStore.remoteDeskUserUuid,
                deskUserUuid: cacheStore.deskUserUuid,
                deskUserPassword: cacheStore.deskUserPassword,
                remoteDeskUserUuid: cacheStore.remoteDeskUserUuid,
                remoteDeskUserPassword: pwd,
                receiverId: receiverId.value,
                maxBitrate: currentMaxBitrate.value,
                maxFramerate: currentMaxFramerate.value,
                resolutionRatio: currentResolutionRatio.value,
                audioContentHint: currentAudioContentHint.value,
                videoContentHint: currentVideoContentHint.value,
              },
            });
          }, 300);
        }

        const flag = cacheStore.linkDeviceList.find(
          (v) => v.remoteDeskUserUuid === cacheStore.remoteDeskUserUuid
        );
        if (!flag) {
          cacheStore.linkDeviceList.push({
            remoteDeskUserUuid: cacheStore.remoteDeskUserUuid,
            remoteDeskUserPassword: pwd,
          });
        }
      } else {
        showPwdModalCpt.value = true;
        errMsg.value = '密码错误，请重新输入';
      }
    } else {
      window.$message.error(res.message);
    }
  } catch (error) {
    console.log(error);
  }
}

function changeRemoteDeskUserUuid(item) {
  const res = cacheStore.linkDeviceList.find(
    (v) => v.remoteDeskUserUuid === item.remoteDeskUserUuid
  );
  if (res) {
    cacheStore.remoteDeskUserUuid = res.remoteDeskUserUuid;
    cacheStore.remoteDeskUserPassword = res.remoteDeskUserPassword;
  }
}

function handleDelLinkDeviceList(item) {
  cacheStore.linkDeviceList = cacheStore.linkDeviceList.filter(
    (v) => v.remoteDeskUserUuid !== item.remoteDeskUserUuid
  );
}

async function startRemote() {
  if (cacheStore.remoteDeskUserUuid === '') {
    window.$message.warning('请输入远程设备代码！');
    return;
  }
  if (cacheStore.remoteDeskUserUuid === cacheStore.deskUserUuid) {
    window.$message.warning('不能连接自己！');
    return;
  }
  try {
    loading.value = true;
    const res = await fetchFindReceiverByUuid(cacheStore.remoteDeskUserUuid);
    if (res.code === 200) {
      if (res.data.receiver !== '') {
        const old = cacheStore.linkDeviceList.find(
          (v) => v.remoteDeskUserUuid === cacheStore.remoteDeskUserUuid
        );
        if (old) {
          pwd.value = old.remoteDeskUserPassword;
          handleConfirm(pwd.value);
        } else {
          pwd.value = '';
          showPwdModalCpt.value = true;
        }
      } else {
        window.$message.info('该设备不在线');
        setTimeout(() => {
          loading.value = false;
        }, 300);
      }
    } else {
      setTimeout(() => {
        loading.value = false;
      }, 300);
      window.$message.error(res.message);
    }
  } catch (error) {
    setTimeout(() => {
      loading.value = false;
    }, 300);
    console.log(error);
  }
}

function handleCloseAll() {
  anchorStream.value = undefined;
  appStore.remoteDesk.forEach((item) => {
    networkStore.removeRtc(item.sender);
  });
}

function handleDel(sender) {
  networkStore.removeRtc(sender);
}
</script>

<style lang="scss" scoped>
.remote-wrap {
  position: relative;
  box-sizing: border-box;
  height: 100%;
  overflow-y: auto;
  .container {
    max-width: 880px;
    margin: 0 auto;
    padding: 24px 28px;
  }
  .page-heading {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 24px;
    h1 {
      margin: 0;
      font-size: 22px;
      font-weight: 600;
    }
  }
  .connection-state {
    color: var(--desk-muted);
    font-size: 12px;
    &.online {
      color: #276749;
    }
  }
  h2 {
    margin: 0 0 12px;
    font-size: 15px;
    font-weight: 600;
  }
  .txt {
    display: block;
    margin-bottom: 8px;
    color: var(--desk-muted);
    font-size: 13px;
  }
  button {
    font: inherit;
    cursor: pointer;
  }
  button:disabled {
    opacity: 0.5;
    cursor: default;
  }
  button:focus-visible,
  input:focus-visible,
  summary:focus-visible {
    outline: 2px solid var(--desk-primary);
    outline-offset: 3px;
  }
  .remote-device {
    position: relative;
    z-index: 10;
    .info {
      display: flex;
      gap: 12px;
    }
    .ipt-wrap {
      flex: 1;
      min-width: 0;
    }
    .ipt-top {
      position: relative;
      .ipt {
        box-sizing: border-box;
        width: 100%;
        height: 48px;
        padding: 0 48px 0 16px;
        border: 1px solid var(--desk-border);
        border-radius: 10px;
        background: var(--desk-surface);
        color: var(--desk-text);
        font-size: 16px;
        caret-color: var(--desk-primary);
        &::placeholder {
          color: var(--desk-muted);
          font-size: 14px;
        }
      }
      .arrow-down {
        position: absolute;
        top: 4px;
        right: 4px;
        width: 40px;
        height: 40px;
        padding: 0;
        border: 0;
        border-radius: 6px;
        background: transparent;
        &::after {
          display: block;
          width: 7px;
          height: 7px;
          margin: 0 auto;
          border-right: 1.5px solid var(--desk-muted);
          border-bottom: 1.5px solid var(--desk-muted);
          content: '';
          transform: translateY(-2px) rotate(45deg);
        }
        &:hover {
          background: var(--desk-background);
        }
        &.active::after {
          transform: translateY(2px) rotate(225deg);
        }
      }
    }
    .ipt-bottom {
      position: relative;
      .link-device-list {
        position: absolute;
        top: 8px;
        width: 100%;
        max-height: 216px;
        overflow-y: auto;
        border-radius: 10px;
        background: var(--desk-surface);
        box-shadow: 0 8px 24px rgb(35 40 48 / 14%);
        .link-device-item {
          display: flex;
          align-items: center;
        }
        .history-code {
          flex: 1;
          min-width: 0;
          padding: 14px 16px;
          border: 0;
          background: transparent;
          color: var(--desk-text);
          text-align: left;
        }
        .history-remove {
          padding: 12px;
          border: 0;
          background: transparent;
          color: var(--desk-muted);
          font-size: 12px;
        }
        .link-device-item:hover {
          background: var(--desk-background);
        }
        .null {
          padding: 16px;
          color: var(--desk-muted);
          text-align: center;
          font-size: 13px;
        }
      }
    }
    .btn {
      flex-shrink: 0;
      min-width: 96px;
      padding: 0 20px;
      border: 0;
      border-radius: 10px;
      background: var(--desk-primary);
      color: white;
      font-size: 15px;
      font-weight: 600;
      &:hover:not(:disabled) {
        background: var(--desk-primary-hover);
      }
    }
  }
  .quality-settings {
    margin-top: 16px;
    summary {
      width: fit-content;
      padding: 4px 0;
      color: var(--desk-primary);
      font-size: 13px;
      cursor: pointer;
      span {
        margin-left: 12px;
        color: var(--desk-muted);
        font-variant-numeric: tabular-nums;
      }
    }
    .link-config {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px 20px;
      padding-top: 16px;
    }
    .link-label {
      display: block;
      margin-bottom: 6px;
      color: var(--desk-muted);
      font-size: 13px;
    }
  }
  .local-device {
    margin-top: 28px;
    padding-top: 20px;
    border-top: 1px solid var(--desk-border);
    .info {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 16px;
    }
    .code-info {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 4px;
    }
    .code {
      margin-right: 8px;
      font-size: 21px;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
      overflow-wrap: anywhere;
      user-select: text;
    }
    .password {
      letter-spacing: 1px;
    }
    .ico {
      flex-shrink: 0;
      width: 32px;
      height: 32px;
      padding: 0;
      border: 0;
      border-radius: 6px;
      background-color: transparent;
      &:hover {
        background-color: var(--desk-primary-soft);
      }
      &.copy {
        @include setBackground('@/assets/img/copy.png', $size: 16px);
      }
      &.refresh {
        @include setBackground('@/assets/img/refresh.png', $size: 16px);
      }
      &.eye {
        @include setBackground('@/assets/img/view.png', $size: 16px);
      }
      &.hide {
        @include setBackground('@/assets/img/view_off.png', $size: 16px);
      }
      &.edit {
        @include setBackground('@/assets/img/edit.png', $size: 16px);
      }
    }
  }
  .list {
    margin-top: 16px;
    .item {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding: 8px 0;
      font-size: 13px;
    }
    .del {
      padding: 6px 12px;
      border: 0;
      border-radius: 6px;
      background: #fff0ed;
      color: #b42318;
      font-size: 13px;
    }
  }
  .debug-info {
    position: fixed;
    right: 0;
    bottom: 0;
    z-index: 20;
    padding-right: 5px;
    font-size: 12px;
    .link {
      color: #b42318;
      cursor: pointer;
    }
  }
  @media (max-width: 560px) {
    .container {
      padding-right: 20px;
      padding-left: 20px;
    }
    .local-device .info,
    .quality-settings .link-config {
      grid-template-columns: 1fr;
    }
    .page-heading {
      align-items: flex-start;
    }
  }
}
</style>

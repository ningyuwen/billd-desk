<template>
  <div class="setting-wrap">
    <div class="container">
      <h1>设置</h1>
      <section
        v-if="ipcRenderer"
        class="settings-section window-setting"
      >
        <span id="always-on-top-label">主窗口置顶</span>
        <n-switch
          v-model:value="cacheStore.isAlwaysOnTop"
          aria-labelledby="always-on-top-label"
        />
      </section>
      <section class="settings-section">
        <div class="section-heading">
          <h2>服务器</h2>
          <button
            class="text-button"
            type="button"
            @click="showUrlModalCpt = true"
          >
            修改配置
          </button>
        </div>
        <p class="server-address">{{ getAxiosBaseUrl() || AXIOS_BASEURL }}</p>
        <details>
          <summary>连接详情</summary>
          <div class="detail-row">
            <span>信令</span>
            <button
              class="text-button address"
              type="button"
              title="复制信令地址"
              @click="handleCopy(getWssUrl() || WEBSOCKET_URL)"
            >
              {{ getWssUrl() || WEBSOCKET_URL }}
            </button>
          </div>
          <div class="detail-row">
            <span>中继</span>
            <button
              class="text-button address"
              type="button"
              title="复制中继地址"
              @click="handleCopy(getCoturnUrl() || COTURN_URL)"
            >
              {{ getCoturnUrl() || COTURN_URL }}
            </button>
          </div>
        </details>
      </section>
      <section class="settings-section">
        <div class="section-heading">
          <h2>应用</h2>
          <button
            v-if="ipcRenderer"
            class="text-button"
            type="button"
            :disabled="checkingUpdate"
            @click="handleDeskVersionCheck"
          >
            {{ checkingUpdate ? '正在检查…' : '检查更新' }}
          </button>
        </div>
        <div class="application-row">
          <span class="version">当前版本 {{ appStore.version }}</span>
          <button
            class="text-button"
            type="button"
            @click="
              handleOpenExternal({
                windowId: WINDOW_ID_ENUM.remote,
                url: WEB_DESK_URL,
              })
            "
          >
            打开网页版 <VPIconExternalLink class="icon" />
          </button>
        </div>
      </section>
    </div>
    <UrlModalCpt
      v-if="showUrlModalCpt"
      @close="showUrlModalCpt = false"
    />
  </div>
</template>

<script lang="ts" setup>
import { copyToClipBoard } from 'billd-utils';
import { ref, watch } from 'vue';

import { fetchDeskVersionCheck } from '@/api/deskVersion';
import {
  AXIOS_BASEURL,
  COTURN_URL,
  WEB_DESK_URL,
  WEBSOCKET_URL,
  WINDOW_ID_ENUM,
} from '@/constant';
import { useIpcRendererSend } from '@/hooks/use-ipcRendererSend';
import { useAppStore } from '@/store/app';
import { usePiniaCacheStore } from '@/store/cache';
import { ipcRenderer } from '@/utils';
import {
  getAxiosBaseUrl,
  getCoturnUrl,
  getWssUrl,
} from '@/utils/localStorage/app';

import UrlModalCpt from './urlModal.vue';

const appStore = useAppStore();
const cacheStore = usePiniaCacheStore();
const showUrlModalCpt = ref(false);
const checkingUpdate = ref(false);
const { handleOpenExternal, handlesetAlwaysOnTop } = useIpcRendererSend();

function handleCopy(str) {
  copyToClipBoard(str);
  window.$message.success('已复制地址');
}
watch(
  () => cacheStore.isAlwaysOnTop,
  () => {
    handlesetAlwaysOnTop({
      windowId: WINDOW_ID_ENUM.remote,
      flag: cacheStore.isAlwaysOnTop,
    });
  },
  { immediate: true }
);

async function handleDeskVersionCheck() {
  if (checkingUpdate.value) return;
  checkingUpdate.value = true;
  try {
    const res = await fetchDeskVersionCheck(appStore.version);
    if (res.code !== 200 || !res.data) {
      window.$message.error('检查更新失败，请稍后重试');
      return;
    }
    appStore.updateModalInfo = res.data;
    if (
      appStore.updateModalInfo?.checkUpdate === 2 ||
      appStore.updateModalInfo?.isUpdate === 2
    )
      window.$message.success('当前是最新版本');
  } catch {
    window.$message.error('无法连接更新服务，请稍后重试');
  } finally {
    checkingUpdate.value = false;
  }
}
</script>

<style lang="scss" scoped>
.setting-wrap {
  box-sizing: border-box;
  height: 100%;
  overflow-y: auto;
  .container {
    max-width: 880px;
    margin: 0 auto;
    padding: 24px 28px;
  }
  h1 {
    margin: 0 0 20px;
    font-size: 22px;
    font-weight: 600;
  }
  h2 {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
  }
  button {
    font: inherit;
    cursor: pointer;
  }
  button:disabled {
    opacity: 0.5;
    cursor: default;
  }
  .settings-section {
    padding: 18px 0;
    border-top: 1px solid var(--desk-border);
  }
  .section-heading,
  .window-setting,
  .application-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
  }
  .window-setting {
    padding-top: 0;
    border: 0;
    font-size: 14px;
  }
  .text-button {
    padding: 4px 0;
    border: 0;
    background: transparent;
    color: var(--desk-primary);
    text-align: left;
    font-size: 13px;
  }
  .text-button:hover:not(:disabled) {
    text-decoration: underline;
    text-underline-offset: 3px;
  }
  .server-address {
    margin: 12px 0 0;
    color: var(--desk-muted);
    font-size: 14px;
    overflow-wrap: anywhere;
    user-select: text;
  }
  .icon {
    width: 12px;
    height: 12px;
    margin-left: 4px;
    vertical-align: middle;
  }
  details {
    margin-top: 12px;
  }
  summary {
    width: fit-content;
    padding: 4px 0;
    color: var(--desk-muted);
    font-size: 13px;
    cursor: pointer;
  }
  .detail-row {
    display: flex;
    align-items: baseline;
    gap: 16px;
    margin-top: 8px;
    color: var(--desk-muted);
    font-size: 13px;
  }
  .address {
    min-width: 0;
    overflow-wrap: anywhere;
    user-select: text;
  }
  .application-row {
    flex-wrap: wrap;
    margin-top: 12px;
  }
  .version {
    color: var(--desk-muted);
    font-size: 14px;
  }
  @media (max-width: 560px) {
    .container {
      padding: 20px;
    }
  }
}
</style>

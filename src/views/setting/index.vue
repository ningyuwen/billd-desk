<template>
  <div class="setting-wrap">
    <div class="container">
      <h1>设置</h1>
      <section
        v-if="ipcRenderer"
        class="settings-section"
      >
        <h2>窗口</h2>
        <div class="setting-row">
          <span id="always-on-top-label">主窗口置顶</span>
          <n-switch
            v-model:value="cacheStore.isAlwaysOnTop"
            aria-labelledby="always-on-top-label"
          />
        </div>
      </section>
      <section class="settings-section">
        <div class="section-heading">
          <h2>私有服务器</h2>
          <button
            class="text-button"
            type="button"
            @click="showUrlModalCpt = true"
          >
            修改
          </button>
        </div>
        <button
          class="server-address"
          type="button"
          @click="
            handleOpenExternal({
              windowId: WINDOW_ID_ENUM.remote,
              url: getAxiosBaseUrl() || AXIOS_BASEURL,
            })
          "
        >
          {{ getAxiosBaseUrl() || AXIOS_BASEURL }}
          <VPIconExternalLink class="icon" />
        </button>
        <details class="connection-details">
          <summary>连接详情</summary>
          <div class="detail-row">
            <span>信令地址</span>
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
            <span>中继地址</span>
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
          <h2>关于 {{ PRODUCT_NAME }}</h2>
          <button
            v-if="ipcRenderer"
            class="text-button"
            type="button"
            @click="handleDeskVersionCheck"
          >
            检查更新
          </button>
        </div>
        <p class="version">版本 {{ appStore.version }}</p>
        <details class="project-details">
          <summary>项目与下载</summary>
          <div class="project-content">
            <div class="detail-row">
              <span>构建时间</span><span>{{ appStore.lastBuildDate }}</span>
            </div>
            <div class="detail-row">
              <span>作者微信</span
              ><button
                class="text-button"
                type="button"
                @click="handleCopy(AUTHOR_INFO.wechat)"
              >
                {{ AUTHOR_INFO.wechat }}
              </button>
            </div>
            <div class="detail-row">
              <span>作者 QQ</span
              ><button
                class="text-button"
                type="button"
                @click="handleCopy(AUTHOR_INFO.qq)"
              >
                {{ AUTHOR_INFO.qq }}
              </button>
            </div>
            <button
              class="text-button"
              type="button"
              @click="
                handleOpenExternal({
                  windowId: WINDOW_ID_ENUM.remote,
                  url: AUTHOR_INFO.github,
                })
              "
            >
              项目源码 <VPIconExternalLink class="icon" />
            </button>
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
            <button
              class="text-button"
              type="button"
              @click="
                handleOpenExternal({
                  windowId: WINDOW_ID_ENUM.remote,
                  url: COMMON_URL.privatizationDeployment,
                })
              "
            >
              私有化部署说明 <VPIconExternalLink class="icon" />
            </button>
            <h3>下载客户端</h3>
            <div class="client-list">
              <button
                v-for="(item, index) in clientList"
                :key="index"
                class="client-btn"
                type="button"
                @click="
                  jumpToDownload({
                    windowId: WINDOW_ID_ENUM.remote,
                    url: item.url,
                  })
                "
              >
                {{ item.label }} <span>{{ item.ext }}</span>
              </button>
            </div>
          </div>
        </details>
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
  AUTHOR_INFO,
  AXIOS_BASEURL,
  COMMON_URL,
  COTURN_URL,
  PRODUCT_NAME,
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
const { handleOpenExternal, handlesetAlwaysOnTop } = useIpcRendererSend();

const clientList = ref<
  {
    label: string;
    ext: string;
    url: string;
  }[]
>([]);

function handleCopy(str) {
  copyToClipBoard(str);
  window.$message.success('复制成功！');
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

watch(
  () => appStore.deskVersionInfo,
  (newval) => {
    if (newval) {
      Object.keys(newval).forEach((item) => {
        if (item.indexOf('download_linux') !== -1) {
          const arr = item.split('_');
          const bit = arr[2] === 'arm' ? 'arm' : `${arr[2]}bit`;
          clientList.value.push({
            label: `${arr[1]}(${bit})`,
            ext: arr[3],
            url: newval[item],
          });
        }
        if (item.indexOf('download_windows') !== -1) {
          const arr = item.split('_');
          const bit = arr[2] === 'arm' ? 'arm' : `${arr[2]}bit`;
          clientList.value.push({
            label: `${arr[1]}(${bit})`,
            ext: arr[3],
            url: newval[item],
          });
        }
        if (item.indexOf('download_macos') !== -1) {
          const arr = item.split('_');
          clientList.value.push({
            label: `${arr[1]}`,
            ext: arr[2],
            url: newval[item],
          });
        }
      });
    }
  },
  { immediate: true, deep: true }
);

function jumpToDownload({ windowId, url }) {
  if (!url || url === '') {
    window.$message.info('敬请期待！');
    return;
  }
  handleOpenExternal({
    windowId,
    url,
  });
}

async function handleDeskVersionCheck() {
  const res = await fetchDeskVersionCheck(appStore.version);
  if (res.code === 200 && res.data) {
    appStore.updateModalInfo = res.data;
    if (appStore.updateModalInfo?.checkUpdate === 2) {
      window.$message.success('当前不需要更新');
    } else if (appStore.updateModalInfo?.isUpdate === 2) {
      window.$message.success('当前是最新版本');
    }
  }
}
</script>

<style lang="scss" scoped>
.setting-wrap {
  box-sizing: border-box;
  height: 100vh;
  overflow-y: auto;
  .container {
    max-width: 880px;
    margin: 0 auto;
    padding: calc(#{$top-system-bar-height} + 16px) 32px 24px;
  }
  h1 {
    margin: 0 0 24px;
    font-size: 24px;
    font-weight: 600;
  }
  h2 {
    margin: 0;
    font-size: 17px;
    font-weight: 600;
  }
  h3 {
    margin: 12px 0 0;
    font-size: 14px;
    font-weight: 600;
  }
  button {
    font: inherit;
    cursor: pointer;
  }
  .settings-section {
    padding: 20px 0;
    border-top: 1px solid var(--desk-border);
  }
  .section-heading {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }
  .setting-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    margin-top: 16px;
    font-size: 14px;
  }
  .text-button {
    padding: 6px 0;
    border: 0;
    background: transparent;
    color: var(--desk-primary);
    text-align: left;
    font-size: 13px;
  }
  .text-button:hover {
    text-decoration: underline;
    text-underline-offset: 3px;
  }
  .server-address {
    margin-top: 12px;
    padding: 0;
    border: 0;
    background: transparent;
    color: var(--desk-text);
    text-align: left;
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
    margin-top: 16px;
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
    flex-wrap: wrap;
    align-items: baseline;
    gap: 8px 16px;
    margin-top: 12px;
    color: var(--desk-muted);
    font-size: 13px;
    overflow-wrap: anywhere;
  }
  .address {
    min-width: 0;
    overflow-wrap: anywhere;
    user-select: text;
  }
  .version {
    margin: 12px 0 0;
    color: var(--desk-muted);
    font-size: 14px;
  }
  .project-content {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
    padding-top: 8px;
  }
  .client-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .client-btn {
    padding: 10px 12px;
    border: 0;
    border-radius: 8px;
    background: var(--desk-primary-soft);
    color: var(--desk-primary);
    font-size: 13px;
    span {
      margin-left: 4px;
    }
  }
  @media (max-width: 720px) {
    .container {
      padding-right: 20px;
      padding-left: 20px;
    }
  }
}
</style>

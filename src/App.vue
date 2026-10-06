<template>
  <n-config-provider :theme-overrides="themeOverrides">
    <n-message-provider :max="3">
      <n-modal-provider>
        <n-dialog-provider>
          <router-view></router-view>
          <NaiveModal />
          <NaiveMessage />
        </n-dialog-provider>
      </n-modal-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script lang="ts" setup>
import { GlobalThemeOverrides, NConfigProvider } from 'naive-ui';
import { onMounted } from 'vue';

import {
  fetchDeskVersionByVersion,
  fetchDeskVersionCheck,
  fetchDeskVersionLatest,
} from '@/api/deskVersion';
import { APP_BUILD_INFO, WINDOW_ID_ENUM } from '@/constant';
import { useIpcRendererSend } from '@/hooks/use-ipcRendererSend';
import { useAppStore } from '@/store/app';
import { usePiniaCacheStore } from '@/store/cache';
import { ipcRenderer } from '@/utils';

const appStore = useAppStore();
const cacheStore = usePiniaCacheStore();

const { handlesetAlwaysOnTop, handleOpenDevTools } = useIpcRendererSend();
const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#8a5a00',
    primaryColorHover: '#714a00',
    primaryColorPressed: '#714a00',
    primaryColorSuppl: '#8a5a00',
    borderRadius: '8px',
  },
};

onMounted(() => {
  console.log('当前地址栏', location.href);
  appStore.version = APP_BUILD_INFO.pkgVersion;
  appStore.lastBuildDate = APP_BUILD_INFO.lastBuildDate;
  handlesetAlwaysOnTop({
    windowId: WINDOW_ID_ENUM.remote,
    flag: cacheStore.isAlwaysOnTop,
  });
  getClient();
  if (ipcRenderer) {
    if (import.meta.env.DEV) {
      handleOpenDevTools({ windowId: WINDOW_ID_ENUM.remote });
    }
    handleDeskVersionCheck();
  }
});

async function handleDeskVersionCheck() {
  try {
    const res = await fetchDeskVersionCheck(appStore.version);
    if (res.code === 200 && res.data) {
      appStore.updateModalInfo = res.data;
    }
  } catch (error) {
    console.log(error);
  }
}

async function getClient() {
  try {
    let res;
    if (ipcRenderer) {
      res = await fetchDeskVersionByVersion(appStore.version);
    } else {
      res = await fetchDeskVersionLatest();
    }
    if (res.code === 200 && res.data) {
      appStore.deskVersionInfo = res.data;
    }
  } catch (error) {
    console.log(error);
  }
}
</script>

<style lang="scss" scoped></style>

<style lang="scss">
:root {
  --desk-primary: #8a5a00;
  --desk-primary-hover: #714a00;
  --desk-primary-soft: #f5edd9;
  --desk-background: #f5f6f8;
  --desk-surface: #fff;
  --desk-text: #232830;
  --desk-muted: #59616d;
  --desk-border: #d3d7de;
}
#app {
  color: var(--desk-text);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  user-select: none;
}
.layout {
  ::selection {
    background: var(--desk-primary-soft);
    color: var(--desk-text);
  }
  :focus-visible {
    outline: 2px solid var(--desk-primary);
    outline-offset: 3px;
  }
  input {
    caret-color: var(--desk-primary);
  }
  * {
    scrollbar-width: thin;
    scrollbar-color: #bec4cc transparent;
  }
}
</style>

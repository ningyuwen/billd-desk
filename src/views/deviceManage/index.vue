<template>
  <div class="devices-wrap">
    <div class="container">
      <h1>最近连接</h1>
      <div
        v-if="!cacheStore.linkDeviceList.length"
        class="empty-state"
      >
        <h2>暂无连接记录</h2>
        <p>连接过的设备会显示在这里。</p>
      </div>
      <div
        v-else
        class="link-device-list"
      >
        <div
          v-for="item in cacheStore.linkDeviceList.slice().reverse()"
          :key="item.remoteDeskUserUuid"
          class="link-device-item"
        >
          <span class="device-code">{{ item.remoteDeskUserUuid }}</span>
          <button
            class="remove"
            type="button"
            :aria-label="`移除设备 ${item.remoteDeskUserUuid} 的连接记录`"
            @click="handleDelLinkDeviceList(item)"
          >
            移除记录
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { usePiniaCacheStore } from '@/store/cache';

const cacheStore = usePiniaCacheStore();

function handleDelLinkDeviceList(item) {
  cacheStore.linkDeviceList = cacheStore.linkDeviceList.filter(
    (v) => v.remoteDeskUserUuid !== item.remoteDeskUserUuid
  );
}
</script>

<style lang="scss" scoped>
.devices-wrap {
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
  .empty-state {
    padding: 24px 0;
    border-top: 1px solid var(--desk-border);
    h2 {
      margin: 0 0 8px;
      font-size: 17px;
      font-weight: 500;
    }
    p {
      margin: 0;
      color: var(--desk-muted);
      font-size: 14px;
    }
  }
  .link-device-item {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 16px 0;
    border-top: 1px solid var(--desk-border);
  }
  .device-code {
    font-size: 18px;
    font-weight: 500;
    font-variant-numeric: tabular-nums;
    user-select: text;
  }
  .remove {
    padding: 8px 12px;
    border: 0;
    border-radius: 6px;
    background: transparent;
    color: var(--desk-muted);
    font: inherit;
    font-size: 13px;
    cursor: pointer;
    &:hover {
      background: #fff0ed;
      color: #b42318;
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

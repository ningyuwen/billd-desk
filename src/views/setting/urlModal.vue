<template>
  <n-modal
    :show="true"
    preset="card"
    title="服务器配置"
    class="server-dialog"
    :style="{ width: '440px', maxWidth: 'calc(100vw - 40px)' }"
    :bordered="false"
    :mask-closable="false"
    @close="emits('close')"
    @esc="emits('close')"
  >
    <form
      class="server-form"
      @submit.prevent="handleConfirm"
    >
      <div class="field">
        <label for="api-address">服务地址</label
        ><input
          id="api-address"
          ref="iptRef"
          v-model="axiosBaseUrl"
          type="url"
          placeholder="https://example.com/api"
          required
        />
      </div>
      <div class="field">
        <label for="signal-address">信令地址</label
        ><input
          id="signal-address"
          v-model="wssUrl"
          type="text"
          placeholder="wss://example.com"
          required
        />
      </div>
      <div class="field">
        <label for="relay-address">中继地址</label
        ><input
          id="relay-address"
          v-model="coturnUrl"
          type="text"
          placeholder="turn:example.com:3478"
          required
        />
      </div>
      <p class="hint">保存后将重新连接服务器。</p>
      <div class="actions">
        <n-button @click="emits('close')">取消</n-button
        ><n-button
          type="primary"
          attr-type="submit"
          >保存并重连</n-button
        >
      </div>
    </form>
  </n-modal>
</template>

<script lang="ts" setup>
import { windowReload } from 'billd-utils';
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { AXIOS_BASEURL, COTURN_URL, WEBSOCKET_URL } from '@/constant';
import { routerName } from '@/router';
import {
  getAxiosBaseUrl,
  getCoturnUrl,
  getWssUrl,
  setAxiosBaseUrl,
  setCoturnUrl,
  setWssUrl,
} from '@/utils/localStorage/app';

const wssUrl = ref('');
const axiosBaseUrl = ref('');
const coturnUrl = ref('');
const iptRef = ref<HTMLInputElement>();
const emits = defineEmits(['confirm', 'close']);
const router = useRouter();
onMounted(() => {
  axiosBaseUrl.value = getAxiosBaseUrl() || AXIOS_BASEURL;
  wssUrl.value = getWssUrl() || WEBSOCKET_URL;
  coturnUrl.value = getCoturnUrl() || COTURN_URL;
  iptRef.value?.focus();
});
async function handleConfirm() {
  const api = axiosBaseUrl.value.trim();
  const signal = wssUrl.value.trim();
  const relay = coturnUrl.value.trim();
  try {
    if (
      !['http:', 'https:'].includes(new URL(api).protocol) ||
      !['ws:', 'wss:'].includes(new URL(signal).protocol) ||
      !/^(turns?|stuns?):\S+$/i.test(relay)
    )
      throw new Error('address');
  } catch {
    window.$message.error('请填写有效的服务、信令和中继地址');
    return;
  }
  setAxiosBaseUrl(api);
  setWssUrl(signal);
  setCoturnUrl(relay);
  emits('confirm');
  emits('close');
  await router.replace({ name: routerName.remote });
  windowReload();
}
</script>

<style lang="scss" scoped>
.server-form {
  .field + .field {
    margin-top: 16px;
  }
  label {
    display: block;
    margin-bottom: 6px;
    color: var(--desk-muted);
    font-size: 13px;
  }
  input {
    box-sizing: border-box;
    width: 100%;
    height: 40px;
    padding: 0 12px;
    border: 1px solid var(--desk-border);
    border-radius: 8px;
    background: var(--desk-surface);
    color: var(--desk-text);
    font: inherit;
    font-size: 14px;
  }
  input:focus-visible {
    outline: 2px solid var(--desk-primary);
    outline-offset: 2px;
  }
  .hint {
    margin: 16px 0;
    color: var(--desk-muted);
    font-size: 13px;
  }
  .actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
}
</style>

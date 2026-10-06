<template>
  <n-modal
    :show="true"
    preset="card"
    title="连接设备"
    :style="{ width: '380px', maxWidth: 'calc(100vw - 40px)' }"
    :bordered="false"
    :mask-closable="false"
    @close="emits('close')"
    @esc="emits('close')"
  >
    <form
      class="password-form"
      @submit.prevent="handleConfirm"
    >
      <p class="device-code">{{ uuid }}</p>
      <label for="connection-password">连接密码</label>
      <div class="password-field">
        <input
          id="connection-password"
          ref="iptRef"
          v-model="password"
          :type="hidePwd ? 'password' : 'text'"
          maxlength="12"
          autocomplete="off"
          placeholder="输入对方的连接密码"
        />
        <button
          type="button"
          :aria-label="hidePwd ? '显示连接密码' : '隐藏连接密码'"
          @click="hidePwd = !hidePwd"
        >
          {{ hidePwd ? '显示' : '隐藏' }}
        </button>
      </div>
      <p
        v-if="errMsg"
        class="error"
        role="alert"
      >
        {{ errMsg }}
      </p>
      <div class="actions">
        <n-button @click="emits('close')">取消</n-button
        ><n-button
          type="primary"
          attr-type="submit"
          :disabled="!password"
          >连接</n-button
        >
      </div>
    </form>
  </n-modal>
</template>

<script lang="ts" setup>
import { onMounted, ref } from 'vue';

const hidePwd = ref(true);
const password = ref('');
const iptRef = ref<HTMLInputElement>();
const props = withDefaults(
  defineProps<{ uuid?: string; pwd?: string; errMsg: string }>(),
  { uuid: '', pwd: '', errMsg: '' }
);
const emits = defineEmits(['confirm', 'close']);
onMounted(() => {
  password.value = props.pwd;
  iptRef.value?.focus();
});
function handleConfirm() {
  if (password.value.length >= 6 && password.value.length <= 12)
    emits('confirm', password.value);
  else window.$message.warning('连接密码长度应为 6–12 位');
}
</script>

<style lang="scss" scoped>
.password-form {
  .device-code {
    margin: 0 0 20px;
    color: var(--desk-text);
    font-size: 20px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }
  label {
    display: block;
    margin-bottom: 6px;
    color: var(--desk-muted);
    font-size: 13px;
  }
  .password-field {
    display: flex;
    align-items: center;
    border: 1px solid var(--desk-border);
    border-radius: 8px;
  }
  .password-field:focus-within {
    outline: 2px solid var(--desk-primary);
    outline-offset: 2px;
  }
  input {
    box-sizing: border-box;
    width: 100%;
    min-width: 0;
    height: 42px;
    padding: 0 12px;
    border: 0;
    outline: none;
    border-radius: 8px;
    background: transparent;
    color: var(--desk-text);
    font: inherit;
  }
  .password-field button {
    flex-shrink: 0;
    align-self: stretch;
    padding: 0 12px;
    border: 0;
    background: transparent;
    color: var(--desk-primary);
    font: inherit;
    font-size: 13px;
    white-space: nowrap;
    cursor: pointer;
  }
  .error {
    margin: 12px 0 0;
    color: #b42318;
    font-size: 13px;
  }
  .actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 24px;
  }
}
</style>

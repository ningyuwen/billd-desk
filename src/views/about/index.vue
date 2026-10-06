<template>
  <div class="about-wrap">
    <div class="item logo"></div>
    <div class="item name">{{ PRODUCT_NAME }}</div>
    <div class="item version">v{{ appStore.version }}</div>
  </div>
</template>

<script lang="ts" setup>
import { PRODUCT_NAME } from '@/constant';
import { useAppStore } from '@/store/app';

const appStore = useAppStore();
</script>

<style lang="scss" scoped>
.about-wrap {
  position: relative;
  overflow: hidden;
  box-sizing: border-box;
  width: 100vw;
  height: 100vh;
  color: #666;
  font-size: 16px;
  .item {
    margin: 0 auto;
    text-align: center;
  }
  .logo {
    margin-top: 50px;
    width: 100px;
    height: 100px;
    border-radius: 10px;

    @include setBackground('@/assets/img/logo.png');
  }
  .name {
    padding-top: 15px;
    font-size: 16px;
  }
  .version {
    padding-top: 20px;
    padding-bottom: 20px;
    font-size: 14px;
  }
}
</style>

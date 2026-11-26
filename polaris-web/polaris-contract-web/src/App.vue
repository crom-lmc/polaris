<script setup lang="ts">
import { theme as antdTheme } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { computed } from 'vue'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
const themeConfig = computed(() => ({
  ...themeStore.antTheme,
  algorithm: themeStore.mode === 'dark' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm
}))
</script>

<template>
  <a-config-provider :theme="themeConfig" :locale="zhCN">
    <router-view />
  </a-config-provider>
</template>

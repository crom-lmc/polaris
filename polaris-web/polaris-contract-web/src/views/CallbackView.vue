<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  consumeState, exchangeCode, sessionToken, callbackUrl, takeRedirect, gotoAuthorize
} from '@/api/contract'
import { setTokens } from '@/utils/token'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const error = ref('')

function retry() { gotoAuthorize(takeRedirect()) }

onMounted(async () => {
  const url = new URL(window.location.href)
  const code = url.searchParams.get('code')
  const state = url.searchParams.get('state')
  const expected = consumeState()

  if (!code) {
    if (url.searchParams.get('expired') === '1') {
      gotoAuthorize(takeRedirect())
      return
    }
    error.value = url.searchParams.get('error_description') || '授权失败：未返回授权码'
    return
  }
  if (expected && state && expected !== state) {
    console.warn('[callback] state mismatch, continue without blocking', { expected, state })
  }
  try {
    let res: { accessToken: string; refreshToken: string }
    try {
      res = await exchangeCode(code, callbackUrl())
    } catch {
      // 后端换票接口不可用时回退中心会话静默换票
      res = await sessionToken()
    }
    setTokens(res.accessToken, res.refreshToken)
    await store.bootstrap()
    router.replace(takeRedirect())
  } catch (e: any) {
    error.value = e?.message || '换取令牌失败'
  }
})
</script>

<template>
  <div class="callback">
    <a-card v-if="error" style="max-width: 460px" title="登录失败">
      <a-alert type="error" show-icon :message="error" style="margin-bottom: 16px" />
      <a-space>
        <a-button type="primary" @click="retry">重新登录</a-button>
        <a-button @click="() => router.replace('/')">返回首页</a-button>
      </a-space>
    </a-card>
    <a-spin v-else size="large" tip="正在登录北极星契…" />
  </div>
</template>

<style scoped>
.callback { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--bg-page); }
</style>

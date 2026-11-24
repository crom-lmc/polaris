<script setup lang="ts">
import { appName } from '@/utils/appTitle'
const year = new Date().getFullYear()
</script>

<template>
  <footer class="footer">
    <div class="container footer-inner">
      <div class="left">
        <div class="brand-small">{{ appName }}</div>
        <div class="muted">内容由北极星源维护 · 单点登录由北极星钥提供</div>
      </div>
      <div class="right muted">© {{ year }} {{ appName }}</div>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  border-top: 1px solid var(--line);
  background: var(--bg-soft);
  margin-top: 40px;
}

.footer-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28px 24px;
}

.brand-small {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 4px;
}
</style>

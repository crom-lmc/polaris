<template>
  <a-layout class="shell">
    <a-layout-header class="header">
      <div class="logo" :class="{ 'is-collapsed': collapsed }">
        <menu-outlined class="menu-toggle" :title="collapsed ? '展开菜单' : '收起菜单'" @click="toggleCollapse" />
        <span class="logo-text">{{ appName }}</span>
      </div>
      <div class="right">
        <AppCenterNav current-code="audit" />
        <a-button
          type="text"
          class="theme-toggle"
          :title="theme.mode === 'dark' ? '切换到白天模式' : '切换到黑夜模式'"
          @click="theme.toggle()"
        >
          <ThemeToggleIcon :mode="theme.mode" />
        </a-button>
        <a-dropdown :trigger="['hover']">
          <span class="who">
            {{ store.user?.nickname || store.user?.username || '未登录' }}
            <down-outlined class="caret" />
          </span>
          <template #overlay>
            <a-menu @click="onUserMenuClick">
              <a-menu-item key="logout"><logout-outlined class="mi" />退出登录</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </a-layout-header>
    <a-layout class="body">
      <a-layout-sider
        class="sider"
        :theme="theme.mode === 'dark' ? 'dark' : 'light'"
        width="208"
        collapsible
        :collapsed="collapsed"
        :trigger="null"
        @collapse="(v: boolean) => (collapsed = v)"
      >
        <a-menu mode="inline" :selected-keys="selectedKeys" @click="handleMenuClick">
          <a-menu-item v-if="store.can('audit:overview:view')" key="overview">
            <template #icon><dashboard-outlined /></template>
            概览
          </a-menu-item>
          <a-menu-item v-if="store.can('audit:events:list')" key="events">
            <template #icon><audit-outlined /></template>
            审计事件
          </a-menu-item>
          <a-menu-item v-if="store.can('audit:login:list')" key="login-audit">
            <template #icon><login-outlined /></template>
            登录审计
          </a-menu-item>
          <a-menu-item v-if="store.can('audit:system-log:list')" key="system-logs">
            <template #icon><file-text-outlined /></template>
            系统日志
          </a-menu-item>
          <a-menu-item v-if="store.can('audit:system-log:list')" key="log-files">
            <template #icon><file-outlined /></template>
            日志文件
          </a-menu-item>
          <a-menu-item v-if="store.can('audit:setting:view')" key="settings">
            <template #icon><setting-outlined /></template>
            全局设置
          </a-menu-item>
        </a-menu>
      </a-layout-sider>
      <a-layout-content class="content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AuditOutlined,
  DashboardOutlined,
  DownOutlined,
  FileOutlined,
  FileTextOutlined,
  LoginOutlined,
  LogoutOutlined,
  MenuOutlined,
  SettingOutlined
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { logout } from '@/api/auth'
import { clearTokens } from '@/utils/token'
import { appName } from '@/utils/appTitle'
import ThemeToggleIcon from '@/components/ThemeToggleIcon.vue'
import AppCenterNav from '@shared/AppCenterNav.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const theme = useThemeStore()

const collapsed = ref(false)

function toggleCollapse() {
  collapsed.value = !collapsed.value
}

const selectedKeys = computed(() => [route.name as string])

function handleMenuClick({ key }: { key: string }) {
  router.push({ name: key })
}

async function onUserMenuClick({ key }: { key: string }) {
  if (key === 'logout') {
    try {
      await logout()
    } finally {
      clearTokens()
      window.location.href = '/login'
    }
  }
}
</script>

<style scoped>
/* 与 sso-web / file-web 等应用保持同一套侧栏骨架：顶栏+侧栏固定，仅内容区滚动 */
.shell { height: 100vh; overflow: hidden; }
/* 双保险：外壳已 overflow hidden，这里再钉死 body 不撑高，彻底杜绝页面级纵向滚动条 */
.body { min-height: 0; overflow: hidden; }
.header {
  display: flex; align-items: center; justify-content: space-between; padding: 0;
  flex: 0 0 auto; background: var(--header-bg); color: var(--header-text);
  border-bottom: 1px solid var(--header-border); line-height: 1;
}
.logo {
  display: flex; align-items: center; gap: 6px; padding-left: 24px;
  color: var(--header-text); font-weight: 600; font-size: 14px;
  flex: 0 0 auto; white-space: nowrap; transition: padding-left 0.2s;
}
.logo.is-collapsed { padding-left: 29px; }
.logo-text { white-space: nowrap; }
.menu-toggle {
  display: inline-flex; align-items: center; justify-content: center;
  width: 22px; height: 22px; font-size: 16px; border-radius: 5px;
  cursor: pointer; transition: background 0.2s, color 0.2s; color: var(--header-text);
}
.menu-toggle:hover { background: var(--header-hover); }
.right { display: flex; align-items: center; gap: 8px; line-height: normal; padding-right: 16px; }
.sider { background: var(--sider-bg) !important; }
/* 菜单项左缩进与顶栏 logo 对齐 */
.sider :deep(.ant-menu-inline.ant-menu-root:not(.ant-menu-inline-collapsed) > .ant-menu-item),
.sider :deep(.ant-menu-inline.ant-menu-root:not(.ant-menu-inline-collapsed) > .ant-menu-submenu > .ant-menu-submenu-title) {
  padding-inline-start: 24px;
}
.sider :deep(.ant-menu) { background: transparent !important; }
.sider :deep(.ant-menu-item:not(.ant-menu-item-selected):not(:hover)),
.sider :deep(.ant-menu-submenu-title:not(:hover)) { background: transparent !important; }
.right .theme-toggle {
  color: var(--header-text); display: inline-flex; align-items: center; justify-content: center;
  width: 32px; height: 32px; min-width: 32px; padding: 0; line-height: 1;
}
.right .theme-toggle :deep(svg) { display: block; }
.right .who {
  color: var(--header-text); font-size: 13px; cursor: pointer; display: inline-flex;
  align-items: center; gap: 4px; height: 32px; padding: 0 10px; border-radius: 6px; transition: background 0.2s;
}
.right .who:hover { background: var(--header-hover); }
.right .caret { font-size: 11px; opacity: 0.7; }
.mi { margin-right: 8px; }
.content { padding: 16px; overflow-y: auto; overflow-x: hidden; display: flex; flex-direction: column; }
.sider :deep(.ant-layout-sider-children) { height: 100%; overflow-y: auto; }
</style>

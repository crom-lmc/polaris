<template>
  <a-layout class="shell">
    <a-layout-header class="header">
      <div class="logo" :class="{ 'is-collapsed': collapsed }">
        <menu-outlined v-if="!mobile" class="menu-toggle" :title="collapsed ? '展开菜单' : '收起菜单'" @click="toggleCollapse" />
        <span class="logo-text">{{ appName }}</span>
      </div>
      <strong v-if="mobile" class="mobile-title">{{ pageTitle }}</strong>
      <div class="right">
        <a-dropdown v-if="mobile && moreMenus.length" :trigger="['click']">
          <a-button type="text" class="more-btn" aria-label="更多菜单"><more-outlined /></a-button>
          <template #overlay>
            <a-menu @click="handleMenuClick">
              <a-menu-item v-for="m in moreMenus" :key="m.path">
                <component :is="m.icon" /> {{ m.title }}
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <AppCenterNav v-if="!mobile" current-code="contract" />
        <a-button type="text" class="theme-toggle"
                  :title="theme.mode === 'dark' ? '切换到白天模式' : '切换到黑夜模式'" @click="theme.toggle()">
          <ThemeToggleIcon :mode="theme.mode" />
        </a-button>
        <a-dropdown :trigger="['hover']">
          <span class="who">{{ store.displayName || '未登录' }} <down-outlined class="caret" /></span>
          <template #overlay>
            <a-menu @click="onUserMenuClick">
              <a-menu-item key="logout"><logout-outlined class="mi" />退出登录</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </a-layout-header>
    <a-layout class="body">
      <a-layout-sider v-if="!mobile" class="sider" :theme="theme.mode === 'dark' ? 'dark' : 'light'"
                      width="208" collapsible :collapsed="collapsed" :trigger="null"
                      @collapse="(v: boolean) => (collapsed = v)">
        <a-menu mode="inline" :selected-keys="selectedKeys" @click="handleMenuClick">
          <a-menu-item v-for="m in menus" v-show="store.can(m.perm)" :key="m.path">
            <template #icon><component :is="m.icon" /></template>
            {{ m.title }}
          </a-menu-item>
        </a-menu>
      </a-layout-sider>
      <a-layout-content class="content" :class="{ 'has-tabs': mobile }">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  AppstoreOutlined, DownOutlined, EditOutlined, FileTextOutlined, LogoutOutlined,
  MenuOutlined, MoreOutlined, ProjectOutlined, ShoppingCartOutlined, TeamOutlined, WalletOutlined
} from '@ant-design/icons-vue'
import { Modal } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { gotoAuthorize, logoutSso } from '@/api/contract'
import { getRefreshToken } from '@/utils/token'
import { appName } from '@/utils/appTitle'
import { useSessionGuard } from '@/composables/useSessionGuard'
import ThemeToggleIcon from '@/components/ThemeToggleIcon.vue'
import AppCenterNav from '@shared/AppCenterNav.vue'
import { useResponsiveCollapsed } from '@shared/useResponsiveCollapsed'

const router = useRouter()
const route = useRoute()
const store = useUserStore()
const theme = useThemeStore()

const collapsed = useResponsiveCollapsed()
useSessionGuard()

const menus = [
  { path: '/purchase-plan', title: '采购计划', icon: ShoppingCartOutlined, perm: 'contract:plan:list' },
  { path: '/partner', title: '往来单位维护', icon: TeamOutlined, perm: 'contract:partner:list' },
  { path: '/material', title: '物料库', icon: AppstoreOutlined, perm: 'contract:material:list' },
  { path: '/material-maintain', title: '物料维护', icon: EditOutlined, perm: 'contract:material-maintain:list' },
  { path: '/project', title: '项目维护', icon: ProjectOutlined, perm: 'contract:project:list' },
  { path: '/contract', title: '合同台账', icon: FileTextOutlined, perm: 'contract:ledger:list' },
  { path: '/payment-plan', title: '合同付款计划', icon: WalletOutlined, perm: 'contract:payment:list' }
]

const mobile = ref(false)
const mq = window.matchMedia('(max-width: 767px)')
const syncMobile = () => { mobile.value = mq.matches }
onMounted(() => { syncMobile(); mq.addEventListener('change', syncMobile) })
onBeforeUnmount(() => mq.removeEventListener('change', syncMobile))

// 窄屏侧栏收起时，标题旁的更多菜单放主流程入口
const moreMenus = computed(() => menus.filter((m) => store.can(m.perm)))
const pageTitle = computed(() => (route.meta.title as string) || appName.value)
const selectedKeys = computed(() => [route.path])

function toggleCollapse() { collapsed.value = !collapsed.value }
function handleMenuClick({ key }: { key: string }) { router.push(key as string) }
function onUserMenuClick({ key }: { key: string }) {
  if (key !== 'logout') return
  Modal.confirm({
    title: '退出登录',
    content: '退出后需要重新登录才能继续使用北极星契，确定吗？',
    okText: '确定退出',
    cancelText: '取消',
    okButtonProps: { danger: true },
    centered: true,
    onOk: async () => {
      try { await logoutSso(getRefreshToken()) } catch { /* 忽略：本地照样退出 */ }
      store.logout()
      gotoAuthorize('/')
    }
  })
}
</script>

<style scoped>
.shell { height: 100vh; overflow: hidden; }
.body { min-height: 0; }
.header {
  display: flex; align-items: center; justify-content: space-between; padding: 0;
  flex: 0 0 auto; background: var(--header-bg); color: var(--header-text);
  border-bottom: 1px solid var(--header-border);
}
.logo {
  display: flex; align-items: center; gap: 6px; padding-left: 24px;
  color: var(--header-text); font-weight: 600; font-size: 14px;
  flex: 0 0 auto; white-space: nowrap; transition: padding-left 0.2s;
}
.logo.is-collapsed { padding-left: 29px; }
.menu-toggle {
  display: inline-flex; align-items: center; justify-content: center;
  width: 22px; height: 22px; font-size: 16px; border-radius: 5px;
  cursor: pointer; transition: background 0.2s, color 0.2s; color: var(--header-text);
}
.menu-toggle:hover { background: var(--header-hover); }
.right { display: flex; align-items: center; gap: 8px; line-height: normal; padding-right: 16px; }
.sider { background: var(--sider-bg) !important; }
.sider :deep(.ant-menu) { background: transparent !important; }
.mobile-title {
  flex: 1 1 auto; min-width: 0; text-align: center; font-size: 14px; color: var(--header-text);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.right .more-btn, .right .theme-toggle {
  color: var(--header-text); display: inline-flex; align-items: center; justify-content: center;
  width: 32px; height: 32px; min-width: 32px; padding: 0; line-height: 1;
}
.right .theme-toggle :deep(svg) { display: block; }
.right .who {
  color: var(--header-text); font-size: 13px; cursor: pointer; display: inline-flex;
  align-items: center; gap: 4px; padding: 4px 8px; border-radius: 6px;
}
.right .who:hover { background: var(--header-hover); }
.right .caret { font-size: 11px; opacity: 0.7; }
.mi { margin-right: 8px; }
.content { padding: 16px; overflow-y: auto; overflow-x: hidden; display: flex; flex-direction: column; }
.content.has-tabs { padding-bottom: 16px; }
@media (max-width: 767px) {
  .logo { padding-left: 12px; font-size: 13px; gap: 4px; }
  .mobile-title { font-size: 13px; padding: 0 4px; }
  .right { padding-right: 12px; gap: 4px; flex: 0 0 auto; }
  .right .who { padding: 4px 2px; font-size: 12px; white-space: nowrap; }
}
</style>

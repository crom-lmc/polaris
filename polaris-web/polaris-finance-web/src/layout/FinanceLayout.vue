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
        <AppCenterNav v-if="!mobile" current-code="finance" />
        <a-button type="text" class="theme-toggle"
                  :title="theme.mode === 'dark' ? '切换到白天模式' : '切换到黑夜模式'" @click="theme.toggle()">
          <ThemeToggleIcon :mode="theme.mode" />
        </a-button>
        <a-dropdown :trigger="['hover']">
          <span class="who">
            {{ store.displayName || '未登录' }}
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
      <a-layout-sider v-if="!mobile" class="sider" :theme="theme.mode === 'dark' ? 'dark' : 'light'"
                      width="208" collapsible :collapsed="collapsed" :trigger="null" @collapse="(v: boolean) => (collapsed = v)">
        <a-menu mode="inline" :selected-keys="selectedKeys" :open-keys="openKeys"
                @openChange="handleOpenChange" @click="handleMenuClick">
          <template v-for="m in menus" :key="m.path">
            <a-sub-menu v-if="m.children?.length && (!m.perm || store.can(m.perm))" :key="m.path">
              <template #icon><component :is="m.icon" /></template>
              <template #title>{{ m.title }}</template>
              <a-menu-item v-for="c in m.children" v-show="!c.perm || store.can(c.perm)" :key="c.path">
                {{ c.title }}
              </a-menu-item>
            </a-sub-menu>
            <a-menu-item v-else v-show="!m.perm || store.can(m.perm)" :key="m.path">
              <template #icon><component :is="m.icon" /></template>
              {{ m.title }}
            </a-menu-item>
          </template>
        </a-menu>
      </a-layout-sider>
      <a-layout-content class="content" :class="{ 'has-tabs': mobile }">
        <router-view />
      </a-layout-content>
    </a-layout>
    <nav v-if="mobile" class="mobile-tabs">
      <a v-for="m in mobileMenus" v-show="!m.perm || store.can(m.perm)" :key="m.path"
         :class="{ active: route.path === m.path }" @click="router.push(m.path)">
        <component :is="m.icon" />
        <span>{{ m.tabLabel || m.short }}</span>
      </a>
    </nav>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  AimOutlined, ApartmentOutlined, BarChartOutlined, BookOutlined, DashboardOutlined,
  DownOutlined, FileTextOutlined, LogoutOutlined, MenuOutlined, MoreOutlined,
  PieChartOutlined, SwapOutlined, WalletOutlined
} from '@ant-design/icons-vue'
import { Modal } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { gotoAuthorize, logoutSso } from '@/api/finance'
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

interface AppMenuItem {
  path: string
  title: string
  short?: string
  tabLabel?: string
  icon: any
  perm: string
  children?: AppMenuItem[]
}

const menus: AppMenuItem[] = [
  { path: '/overview', title: '财务概览', short: '概览', icon: DashboardOutlined, perm: 'finance:overview:view' },
  { path: '/voucher', title: '凭证管理', short: '凭证', icon: FileTextOutlined, perm: 'finance:voucher:list' },
  { path: '/expense', title: '费用报销', short: '报销', tabLabel: '费用报销', icon: WalletOutlined, perm: 'finance:expense:list' },
  { path: '/budget', title: '预算管理', short: '预算', icon: PieChartOutlined, perm: 'finance:budget:view' },
  { path: '/ar-ap', title: '应收应付', short: '往来', tabLabel: '应收应付', icon: SwapOutlined, perm: 'finance:ar-ap:list' },
  { path: '/report', title: '财务报表', short: '报表', icon: BarChartOutlined, perm: 'finance:report:view' },
  {
    path: '/master-data', title: '主数据维护', short: '主数据', icon: BookOutlined, perm: 'finance:master:list',
    children: [
      { path: '/account-subject', title: '会计科目', short: '会计科目', icon: BookOutlined, perm: 'finance:account-subject:list' },
      { path: '/budget-subject', title: '预算科目', short: '预算科目', icon: AimOutlined, perm: 'finance:budget-subject:list' }
    ]
  },
  { path: '/lineage', title: '数据口径', short: '口径', icon: ApartmentOutlined, perm: 'finance:lineage:view' }
]

const mobile = ref(false)
const mq = window.matchMedia('(max-width: 767px)')
const syncMobile = () => { mobile.value = mq.matches }
onMounted(() => { syncMobile(); mq.addEventListener('change', syncMobile) })
onBeforeUnmount(() => mq.removeEventListener('change', syncMobile))

const flatMenus = computed(() => menus.flatMap((m) => m.children ?? [m]))
const mobileMenus = computed(() => flatMenus.value.filter((m) => ['/overview', '/voucher', '/expense', '/ar-ap'].includes(m.path)))
const moreMenus = computed(() => flatMenus.value.filter((m) => !mobileMenus.value.includes(m) && (!m.perm || store.can(m.perm))))
const pageTitle = computed(() => (route.meta.title as string) || appName.value)
const selectedKeys = computed(() => [route.path])
const openKeys = ref<string[]>([])
watch(() => route.path, (p) => {
  const parent = menus.find((m) => m.children?.some((c) => c.path === p))
  if (parent && !openKeys.value.includes(parent.path)) openKeys.value = [...openKeys.value, parent.path]
}, { immediate: true })

function toggleCollapse() { collapsed.value = !collapsed.value }
function handleOpenChange(keys: any) { openKeys.value = keys as string[] }
function handleMenuClick({ key }: { key: string }) { router.push(key as string) }
function onUserMenuClick({ key }: { key: string }) {
  if (key === 'logout') {
    Modal.confirm({
      title: '退出登录',
      content: '退出后需要重新登录才能继续使用北极星账，确定吗？',
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
}
</script>

<style scoped>
.shell { height: 100vh; overflow: hidden; }
.body { min-height: 0; }
.header {
  display: flex; align-items: center; justify-content: space-between; padding: 0; flex: 0 0 auto;
  background: var(--header-bg); color: var(--header-text); border-bottom: 1px solid var(--header-border);
}
.logo {
  display: flex; align-items: center; gap: 6px; padding-left: 24px; color: var(--header-text);
  font-weight: 600; font-size: 14px; flex: 0 0 auto; white-space: nowrap; transition: padding-left 0.2s;
}
.logo.is-collapsed { padding-left: 29px; }
.menu-toggle {
  display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px;
  font-size: 16px; border-radius: 5px; cursor: pointer; color: var(--header-text);
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
.content.has-tabs { padding-bottom: 72px; }
.mobile-tabs {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 20; height: 56px; display: flex;
  background: var(--panel-bg); border-top: 1px solid var(--border);
}
.mobile-tabs a {
  flex: 1 1 0; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 2px; font-size: 11px; color: var(--muted); cursor: pointer;
}
.mobile-tabs a.active { color: var(--primary); font-weight: 600; }
.mobile-tabs a .anticon { font-size: 17px; }
@media (max-width: 767px) {
  .logo { padding-left: 12px; font-size: 13px; gap: 4px; }
  .mobile-title { font-size: 13px; padding: 0 4px; }
  .right { padding-right: 12px; gap: 4px; flex: 0 0 auto; }
  .right .theme-toggle { width: 28px; min-width: 28px; }
  .right .who { padding: 4px 2px; font-size: 12px; white-space: nowrap; }
}
</style>

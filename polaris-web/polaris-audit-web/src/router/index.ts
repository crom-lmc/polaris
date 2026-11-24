import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import AuditLayout from '@/layout/AuditLayout.vue'
import { useUserStore } from '@/stores/user'
import { gotoAuthorize } from '@/api/auth'
import { renderAppTitle, resolveAppName } from '@/utils/appTitle'

/**
 * 统一审计中心路由表。
 * meta.perm 与后端 @Permit 的 audit:* 权限码一一对应，超管自动放行。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/callback',
      name: 'callback',
      component: () => import('@/views/CallbackView.vue'),
      meta: { title: '登录回调', public: true }
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { title: '登录', public: true }
    },
    {
      path: '/',
      component: AuditLayout,
      redirect: '/overview',
      children: [
        {
          path: 'overview',
          name: 'overview',
          component: () => import('@/views/OverviewView.vue'),
          meta: { title: '概览', perm: 'audit:overview:view' }
        },
        {
          path: 'events',
          name: 'events',
          component: () => import('@/views/EventsView.vue'),
          meta: { title: '审计事件', perm: 'audit:events:list' }
        },
        {
          // 不能用 path: 'login'：登录页已占用 /login，会与子路由冲突导致点击菜单跳回登录页
          path: 'login-audit',
          name: 'login-audit',
          component: () => import('@/views/LoginAuditView.vue'),
          meta: { title: '登录审计', perm: 'audit:login:list' }
        },
        {
          path: 'system-logs',
          name: 'system-logs',
          component: () => import('@/views/SystemLogEntriesView.vue'),
          meta: { title: '系统日志', perm: 'audit:system-log:list' }
        },
        {
          path: 'log-files',
          name: 'log-files',
          component: () => import('@/views/SystemLogsView.vue'),
          meta: { title: '日志文件', perm: 'audit:system-log:list' }
        }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/overview' }
  ]
})

router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const store = useUserStore()
  const loggedIn = await store.bootstrap()
  if (!loggedIn) {
    // 标准单点：跳统一账号中心签授权码，回跳 /callback 换票（与其他业务应用一致）。
    // 加 ?local=1 可走本地密码登录兜底（SSO 不可用时）。
    if (to.query.local === '1') {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    gotoAuthorize(to.fullPath)
    return false
  }
  // 应用名取登录接口登记的实际名称（北极星眼…），只取一次；就绪后刷新标签
  void resolveAppName('audit').then(() => renderAppTitle(to.meta.title as string | undefined))
  const perm = to.meta.perm as string | undefined
  if (perm && !store.can(perm)) {
    return { path: '/overview', query: { denied: perm } }
  }
  return true
})

router.afterEach((to) => {
  renderAppTitle(to.meta.title as string | undefined)
})

export default router

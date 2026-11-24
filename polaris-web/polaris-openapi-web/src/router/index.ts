import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { gotoAuthorize } from '@/api/openapi'
import { renderAppTitle, resolveAppName } from '@/utils/appTitle'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/callback', component: () => import('@/views/CallbackView.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/layout/OpenapiLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '概览', perm: 'openapi:dashboard' } },
        { path: 'endpoints', component: () => import('@/views/EndpointView.vue'), meta: { title: 'API 管理', perm: 'openapi:endpoint' } },
        { path: 'apps', component: () => import('@/views/AppView.vue'), meta: { title: '对接应用', perm: 'openapi:app' } },
        { path: 'authz', component: () => import('@/views/AuthzView.vue'), meta: { title: '应用授权', perm: 'openapi:app:auth' } },
        { path: 'channels', component: () => import('@/views/ChannelView.vue'), meta: { title: '出站渠道', perm: 'openapi:channel' } },
        { path: 'logs', component: () => import('@/views/LogView.vue'), meta: { title: '调用日志', perm: 'openapi:log' } },
        { path: 'stats', component: () => import('@/views/StatView.vue'), meta: { title: '统计报表', perm: 'openapi:stat' } },
        { path: 'settings', component: () => import('@/views/SettingView.vue'), meta: { title: '平台设置', perm: 'openapi:setting' } },
        { path: 'no-access', component: () => import('@/views/NoAccessView.vue'), meta: { title: '无权限', public: true } }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach(async (to) => {
  renderAppTitle(to.meta.title as string | undefined)
  if (to.meta.public) return true
  const store = useUserStore()
  const loggedIn = await store.bootstrap()
  if (!loggedIn) {
    gotoAuthorize(to.fullPath)
    return false
  }
  // 应用名取登录接口登记的实际名称（北极星桥…），只取一次；就绪后刷新标签
  void resolveAppName('openapi').then(() => renderAppTitle(to.meta.title as string | undefined))
  if (to.meta.perm && !store.can(to.meta.perm as string)) {
    return '/no-access'
  }
  return true
})

export default router

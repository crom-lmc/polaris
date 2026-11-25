import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { gotoAuthorize } from '@/api/file'
import { renderAppTitle, resolveAppName } from '@/utils/appTitle'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/callback', component: () => import('@/views/CallbackView.vue'), meta: { public: true } },
    { path: '/share/access', component: () => import('@/views/ShareAccessView.vue'), meta: { title: '文件分享', public: true } },
    {
      path: '/',
      component: () => import('@/layout/FileLayout.vue'),
      children: [
        { path: '', redirect: '/overview' },
        { path: 'overview', component: () => import('@/views/OverviewView.vue'), meta: { title: '概览', perm: 'file:overview' } },
        { path: 'files', component: () => import('@/views/FilesView.vue'), meta: { title: '文件管理', perm: 'file:files' } },
        { path: 'recycle', component: () => import('@/views/RecycleView.vue'), meta: { title: '回收站', perm: 'file:files' } },
        { path: 'shares', component: () => import('@/views/SharesView.vue'), meta: { title: '分享管理', perm: 'file:download' } },
        { path: 'policy', component: () => import('@/views/PolicyView.vue'), meta: { title: '存储策略', perm: 'file:policy' } },
        { path: 'attachment-setting', component: () => import('@/views/AttachmentSettingsView.vue'), meta: { title: '附件设置', perm: 'file:attachment-setting' } },
        { path: 'no-access', component: () => import('@/views/NoAccessView.vue'), meta: { title: '无权限', public: true } }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/overview' }
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
  // 应用名取登录接口登记的实际名称（北极星云…），只取一次；就绪后刷新标签
  void resolveAppName('file').then(() => renderAppTitle(to.meta.title as string | undefined))
  if (to.meta.perm && !store.can(to.meta.perm as string)) {
    return '/no-access'
  }
  return true
})

export default router

import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { IS_PREVIEW } from '@/api/portal'
import { renderAppTitle, resolveAppName } from '@/utils/appTitle'

const router = createRouter({
  history: createWebHistory('/portal/'),
  routes: [
    { path: '/', component: () => import('@/views/HomeView.vue'), meta: { title: '首页' } },
    { path: '/article/:id', component: () => import('@/views/ArticleView.vue'), meta: { title: '文章详情' } },
    { path: '/list', component: () => import('@/views/ArticleListView.vue'), meta: { title: '内容列表' } },
    { path: '/careers', component: () => import('@/views/CareersView.vue'), meta: { title: '加入我们' } },
    {
      path: '/careers/progress',
      component: () => import('@/views/JobProgressView.vue'),
      meta: { title: '投递进度' }
    },
    {
      path: '/careers/:id',
      component: () => import('@/views/JobDetailView.vue'),
      meta: { title: '职位详情' }
    },
    { path: '/callback', component: () => import('@/views/CallbackView.vue'), meta: { public: true, title: '登录中' } },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ],
  scrollBehavior() {
    return { top: 0 }
  }
})

router.beforeEach(async (to) => {
  renderAppTitle(to.meta.title as string | undefined)
  // 后台嵌入的布局预览：只渲染首页，任何跳转都拦下来，避免跑出预览框
  if (IS_PREVIEW) return to.path === '/' ? true : false
  // 门户大多页面公开；仅在进入时静默尝试恢复登录态（不强制）
  const store = useUserStore()
  if (!store.ready) {
    store.bootstrap().catch(() => {})
  }
  // 应用名取登录接口登记的实际名称（北极星门…），登录后就绪即刷新标签；失败保留兜底
  void resolveAppName('portal').then(() => renderAppTitle(to.meta.title as string | undefined))
  return true
})

export default router

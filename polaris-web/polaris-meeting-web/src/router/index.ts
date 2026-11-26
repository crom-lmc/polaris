import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { gotoAuthorize } from '@/api/meeting'
import { renderAppTitle, resolveAppName } from '@/utils/appTitle'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/callback', component: () => import('@/views/CallbackView.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/layout/MeetingLayout.vue'),
      children: [
        { path: '', redirect: '/overview' },
        { path: 'overview', component: () => import('@/views/OverviewView.vue'), meta: { title: '概览', perm: 'meeting:overview:view' } },
        { path: 'location', component: () => import('@/views/LocationView.vue'), meta: { title: '位置维护', perm: 'meeting:room:list' } },
        { path: 'rooms', component: () => import('@/views/RoomsView.vue'), meta: { title: '会议室维护', perm: 'meeting:room:list' } },
        { path: 'booking', component: () => import('@/views/BookingView.vue'), meta: { title: '会议室预定', perm: 'meeting:booking:view' } },
        { path: 'my-bookings', component: () => import('@/views/MyBookingsView.vue'), meta: { title: '我的预定', perm: 'meeting:booking:list' } },
        { path: 'no-shows', component: () => import('@/views/NoShowsView.vue'), meta: { title: '爽约记录', perm: 'meeting:noshow:view' } },
        { path: 'usage', component: () => import('@/views/UsageView.vue'), meta: { title: '会议室使用率', perm: 'meeting:usage:view' } },
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
  void resolveAppName('meeting').then(() => renderAppTitle(to.meta.title as string | undefined))
  if (to.meta.perm && !store.can(to.meta.perm as string)) {
    return '/no-access'
  }
  // 手机端只开放预定主流程：概览/维护/爽约记录/使用率即使直接输 URL 也回到预定页
  if (typeof window !== 'undefined' && window.matchMedia('(max-width: 767px)').matches &&
      ['/overview', '/location', '/rooms', '/no-shows', '/usage'].includes(to.path)) {
    return '/booking'
  }
  return true
})

export default router

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
  return true
})

export default router

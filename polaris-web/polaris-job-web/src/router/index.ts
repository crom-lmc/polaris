import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { renderAppTitle, resolveAppName } from '@/utils/appTitle'

const routes: RouteRecordRaw[] = [
  {
    path: '/callback',
    name: 'Callback',
    component: () => import('@/views/callback/CallbackView.vue')
  },
  {
    path: '/',
    component: () => import('@/layouts/JobLayout.vue'),
    children: [
      { path: '', redirect: '/jobs' },
      // 统一登录回调的默认落点仍是 /dashboard，这里直接并入任务管理
      { path: 'dashboard', redirect: '/jobs' },
      { path: 'jobs', name: 'JobList', component: () => import('@/views/jobs/JobListView.vue'), meta: { title: '任务管理' } },
      { path: 'logs', name: 'LogList', component: () => import('@/views/logs/LogListView.vue'), meta: { title: '调度日志' } },
      { path: 'executors', name: 'ExecutorList', component: () => import('@/views/executors/ExecutorListView.vue'), meta: { title: '执行器' } },
      { path: 'alarms', name: 'AlarmList', component: () => import('@/views/alarms/AlarmView.vue'), meta: { title: '报警配置' } }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to) => {
  renderAppTitle(to.meta.title as string | undefined)
  // 应用名取登录接口登记的实际名称（北极星轨…），只取一次；就绪后刷新标签，失败保留兜底
  void resolveAppName('job').then(() => renderAppTitle(to.meta.title as string | undefined))
  return true
})

export default router

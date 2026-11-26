import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { gotoAuthorize } from '@/api/finance'
import { renderAppTitle, resolveAppName } from '@/utils/appTitle'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/callback', component: () => import('@/views/CallbackView.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/layout/FinanceLayout.vue'),
      children: [
        { path: '', redirect: '/overview' },
        { path: 'overview', component: () => import('@/views/OverviewView.vue'), meta: { title: '财务概览', perm: 'finance:overview:view' } },
        { path: 'voucher', component: () => import('@/views/VoucherView.vue'), meta: { title: '凭证管理', perm: 'finance:voucher:list' } },
        { path: 'expense', component: () => import('@/views/ExpenseView.vue'), meta: { title: '费用报销', perm: 'finance:expense:list' } },
        { path: 'budget', component: () => import('@/views/BudgetView.vue'), meta: { title: '预算管理', perm: 'finance:budget:view' } },
        { path: 'ar-ap', component: () => import('@/views/ArApView.vue'), meta: { title: '应收应付', perm: 'finance:ar-ap:list' } },
        { path: 'report', component: () => import('@/views/ReportView.vue'), meta: { title: '财务报表', perm: 'finance:report:view' } },
        { path: 'account-subject', component: () => import('@/views/MasterDataView.vue'), meta: { title: '会计科目', perm: 'finance:master:list' } },
        { path: 'budget-subject', component: () => import('@/views/MasterDataView.vue'), meta: { title: '预算科目', perm: 'finance:master:list' } },
        { path: 'lineage', component: () => import('@/views/LineageView.vue'), meta: { title: '数据口径' } },
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
  void resolveAppName('finance').then(() => renderAppTitle(to.meta.title as string | undefined))
  if (to.meta.perm && !store.can(to.meta.perm as string)) {
    return '/no-access'
  }
  return true
})

export default router

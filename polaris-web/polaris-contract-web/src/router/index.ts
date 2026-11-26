import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { gotoAuthorize } from '@/api/contract'
import { renderAppTitle, resolveAppName } from '@/utils/appTitle'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/callback', component: () => import('@/views/CallbackView.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/layout/ContractLayout.vue'),
      children: [
        { path: '', redirect: '/purchase-plan' },
        { path: 'purchase-plan', component: () => import('@/views/PurchasePlanView.vue'), meta: { title: '采购计划', perm: 'contract:plan:list' } },
        { path: 'partner', component: () => import('@/views/PartnerView.vue'), meta: { title: '往来单位维护', perm: 'contract:partner:list' } },
        { path: 'material', component: () => import('@/views/MaterialView.vue'), meta: { title: '物料库', perm: 'contract:material:list' } },
        { path: 'material-maintain', component: () => import('@/views/MaterialMaintainView.vue'), meta: { title: '物料维护', perm: 'contract:material-maintain:list' } },
        { path: 'project', component: () => import('@/views/ProjectView.vue'), meta: { title: '项目维护', perm: 'contract:project:list' } },
        { path: 'contract', component: () => import('@/views/ContractLedgerView.vue'), meta: { title: '合同台账', perm: 'contract:ledger:list' } },
        { path: 'payment-plan', component: () => import('@/views/PaymentPlanView.vue'), meta: { title: '合同付款计划', perm: 'contract:payment:list' } },
        { path: 'no-access', component: () => import('@/views/NoAccessView.vue'), meta: { title: '无权限', public: true } }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/purchase-plan' }
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
  void resolveAppName('contract').then(() => renderAppTitle(to.meta.title as string | undefined))
  if (to.meta.perm && !store.can(to.meta.perm as string)) return '/no-access'
  return true
})

export default router

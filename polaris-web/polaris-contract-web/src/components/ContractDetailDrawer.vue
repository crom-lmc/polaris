<script setup lang="ts">
/**
 * 合同详情抽屉（纯只读，全站复用）
 * 合同台账点「查看」、采购计划点「关联合同号」都打开本组件。
 */
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useIsMobile } from '@/composables/useIsMobile'
import { getContract, getContractByNo, listContractChanges, type ContractVO, type ChangeLogVO } from '@/api/contract'
import { yuan } from '@/utils/format'
import ContractItemsEditor from '@/components/ContractItemsEditor.vue'
import PaymentTermsEditor from '@/components/PaymentTermsEditor.vue'
import PaymentPlanEditor from '@/components/PaymentPlanEditor.vue'

const props = defineProps<{ open: boolean; contractId?: string | null; contractNo?: string | null }>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const isMobile = useIsMobile()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 900))
const descColumn = computed(() => (isMobile.value ? 1 : 2))

const typeLabel: Record<string, string> = { PURCHASE: '采购合同', SALES: '销售合同' }
const statusLabel: Record<string, string> = {
  DRAFT: '草稿', PENDING: '审批中', EFFECTIVE: '已生效', EXECUTING: '执行中', COMPLETED: '已完成', TERMINATED: '已终止'
}
const statusColor: Record<string, string> = {
  DRAFT: 'default', PENDING: 'orange', EFFECTIVE: 'blue', EXECUTING: 'geekblue', COMPLETED: 'green', TERMINATED: 'red'
}
const sourceLabel: Record<string, string> = { DIRECT: '直接新建', FROM_PLAN: '计划转入' }

const detailLoading = ref(false)
const detail = ref<ContractVO | null>(null)
const partnerLabel = computed(() => detail.value?.type === 'SALES' ? '客户' : '供应商')
const changeOpen = ref(false)
const changeLoading = ref(false)
const changes = ref<ChangeLogVO[]>([])

/** 合同执行闭环步骤条：与销售/采购流程对齐 */
const flowSteps = computed(() => detail.value?.type === 'SALES'
  ? ['销售合同', '合同清单', '验收', '应收单', '凭证', '账表']
  : ['采购计划', '合同与清单', '付款计划', '应付单', '凭证', '账表'])
const flowCurrent = computed(() => {
  const s = detail.value?.status
  if (!s) return 0
  if (s === 'DRAFT') return 1
  if (s === 'PENDING' || s === 'EFFECTIVE') return 2
  if (s === 'EXECUTING') return 3
  return 4
})

watch(() => [props.open, props.contractId, props.contractNo], ([open, id, no]) => {
  if (!open || (!id && !no)) return
  detailLoading.value = true
  const req = no ? getContractByNo(String(no)) : getContract(String(id))
  req.then((v) => { detail.value = v })
    .catch((e: any) => { message.error(e?.message || '加载合同详情失败'); detail.value = null })
    .finally(() => { detailLoading.value = false })
}, { immediate: true })

async function openChanges() {
  if (!detail.value) return
  changeOpen.value = true
  changeLoading.value = true
  try { changes.value = await listContractChanges(detail.value.id) }
  catch (e: any) { message.error(e?.message || '加载变更记录失败') }
  finally { changeLoading.value = false }
}

function close() { emit('update:open', false) }
</script>

<template>
  <a-drawer :open="open" title="合同详情 · 清单 · 付款计划" :width="drawerWidth" placement="right" @update:open="emit('update:open', $event)">
    <a-spin :spinning="detailLoading">
      <template v-if="detail">
        <a-steps :current="flowCurrent" size="small" style="margin-bottom: 16px"
                 :items="flowSteps.map((title) => ({ title }))" />
        <a-card class="panel-card" size="small" title="合同信息">
          <a-descriptions :column="descColumn" bordered size="small">
            <a-descriptions-item label="合同编号">{{ detail.no }}</a-descriptions-item>
            <a-descriptions-item label="合同名称">{{ detail.name }}</a-descriptions-item>
            <a-descriptions-item label="类型">{{ typeLabel[detail.type] || detail.type }}</a-descriptions-item>
            <a-descriptions-item :label="partnerLabel">{{ detail.partnerName || detail.partnerCode }}</a-descriptions-item>
            <a-descriptions-item label="归属部门">{{ detail.dept || '-' }} · {{ detail.owner || '-' }}</a-descriptions-item>
            <a-descriptions-item label="所属项目">{{ detail.projectName || '未关联' }}</a-descriptions-item>
            <a-descriptions-item label="签订 / 生效 / 到期">{{ detail.signedDate || '-' }} / {{ detail.effectiveDate || '-' }} / {{ detail.endDate || '-' }}</a-descriptions-item>
            <a-descriptions-item label="质保到期日">{{ detail.warrantyDueDate || '-' }}</a-descriptions-item>
            <a-descriptions-item label="备注">{{ detail.note || '-' }}</a-descriptions-item>
            <a-descriptions-item label="来源">
              <a-tag :color="detail.contractSource === 'FROM_PLAN' ? 'blue' : 'default'">
                {{ sourceLabel[detail.contractSource] || detail.contractSource || '直接新建' }}
              </a-tag>
              <span v-if="detail.planNo" class="muted" style="margin-left: 8px">计划号 {{ detail.planNo }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="合同金额">{{ yuan(detail.amount) }}</a-descriptions-item>
            <a-descriptions-item label="已入账 / 在途 / 待支付">{{ yuan(detail.paidAmount) }} / {{ yuan(detail.inFlightAmount) }} / {{ yuan(detail.pendingAmount) }}</a-descriptions-item>
            <a-descriptions-item label="执行进度">{{ detail.progress }}%</a-descriptions-item>
            <a-descriptions-item label="变更次数">
              {{ detail.changeCount || 0 }} 次
              <a v-if="detail.changeCount" @click="openChanges">查看记录</a>
            </a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="statusColor[detail.status] || 'default'">{{ statusLabel[detail.status] || detail.status }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="合同附件">
              <template v-if="detail.fileIds?.length">
                <a v-for="(url, idx) in detail.attachmentUrls || []" :key="url" :href="url" target="_blank" style="margin-right: 12px">附件 {{ idx + 1 }}</a>
                <span v-if="!detail.attachmentUrls?.length" class="muted">{{ detail.fileIds.length }} 个（签名链接不可用）</span>
              </template>
              <span v-else class="muted">无附件</span>
            </a-descriptions-item>
          </a-descriptions>
        </a-card>

        <div class="drawer-section-title">合同清单</div>
        <ContractItemsEditor :contract-no="detail.no" :contract-status="detail.status" :contract-type="detail.type" readonly />

        <PaymentTermsEditor :contract-no="detail.no" :contract-status="detail.status" :contract-type="detail.type" readonly />

        <div class="drawer-section-title">付款计划</div>
        <PaymentPlanEditor :contract-no="detail.no" :remaining-amount="detail.pendingAmount" :contract-status="detail.status" readonly />
      </template>
    </a-spin>
    <template #footer>
      <div class="drawer-footer"><a-button @click="close">关闭</a-button></div>
    </template>
  </a-drawer>

  <a-modal v-model:open="changeOpen" title="合同变更记录" :footer="null" :width="720">
    <a-spin :spinning="changeLoading">
      <a-table :data-source="changes" row-key="id" size="small" :pagination="false" :scroll="{ x: 760, y: 420 }">
        <a-table-column title="变更单号" data-index="no" key="no" width="170" />
        <a-table-column title="变更类型" data-index="changeType" key="changeType" width="100" />
        <a-table-column title="变更原因" data-index="reason" key="reason">
          <template #default="{ record }">
            <a-tooltip :title="record.reason"><span class="cell-ellipsis">{{ record.reason || '-' }}</span></a-tooltip>
          </template>
        </a-table-column>
        <a-table-column title="操作人" data-index="operator" key="operator" width="120" />
        <a-table-column title="时间" data-index="createdAt" key="createdAt" width="160" />
      </a-table>
      <div v-if="!changes.length && !changeLoading" class="panel-hint" style="padding: 16px 0; text-align: center">暂无变更记录</div>
    </a-spin>
  </a-modal>
</template>

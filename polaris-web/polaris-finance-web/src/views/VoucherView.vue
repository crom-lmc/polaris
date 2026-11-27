<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import dayjs, { type Dayjs } from 'dayjs'
import { message, Modal } from 'ant-design-vue'
import {
  listVouchers, getVoucher, createVoucher, postVoucher, submitVoucherReview, reviewVoucher,
  deleteVoucher, subjectOptions, fetchContractOptions, fetchPublicDict, uploadVoucherAttachment,
  type VoucherVO, type VoucherSave, type SubjectVO, type ContractOptionVO, type DictOption
} from '@/api/finance'
import AttachmentUpload from '@shared/components/AttachmentUpload.vue'
import { useUserStore } from '@/stores/user'
import { useOrgDirectory, filterOption as orgFilterOption } from '@/composables/useOrgDirectory'
import { dateOf, fmtDate, sourceLabel, statusColor, statusLabel, yuan } from '@/utils/format'

const route = useRoute()
const store = useUserStore()
const canEdit = () => store.can('finance:voucher:edit')
const { deptOptions, load: loadOrgDirectory } = useOrgDirectory()
const filterOption = orgFilterOption

const loading = ref(false)
const rows = ref<VoucherVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref((route.query.keyword as string) || '')
const status = ref<string>()
const sourceType = ref<string>()
const range = ref<[Dayjs, Dayjs] | null>(null)

const subjects = ref<SubjectVO[]>([])
const contractOptions = ref<{ label: string; value: string }[]>([])
const voucherTypeOptions = ref<{ label: string; value: string }[]>([])
const statusOptions = [
  { label: '草稿', value: 'DRAFT' }, { label: '待审核', value: 'REVIEW' }, { label: '已入账', value: 'POSTED' }
]
const sourceOptions = [
  { label: '手工凭证', value: 'MANUAL' }, { label: '报销单', value: 'EXPENSE' },
  { label: '应收单', value: 'AR' }, { label: '应付单', value: 'AP' }
]

const columns = [
  { title: '凭证号', dataIndex: 'no', key: 'no', width: 170 },
  { title: '凭证类型', key: 'voucherType', width: 110 },
  { title: '摘要', key: 'summary', width: 240 },
  { title: '来源单据', key: 'source', width: 190 },
  { title: '会计科目', dataIndex: 'subject', key: 'subject', width: 170 },
  { title: '金额', key: 'amount', align: 'right' as const, width: 120 },
  { title: '状态', key: 'status', align: 'center' as const, width: 100 },
  { title: '审核人 / 过账人', key: 'operators', width: 140 },
  { title: '附件', key: 'attachments', width: 90 },
  { title: '制单人', dataIndex: 'creator', key: 'creator', width: 90 },
  { title: '日期', dataIndex: 'date', key: 'date', width: 110 },
  { title: '操作', key: 'action', width: 180 }
]
const pagination = () => ({
  current: page.value, pageSize: size.value, total: total.value,
  showSizeChanger: true, showTotal: (t: number) => `共 ${t} 条`
})

async function load() {
  loading.value = true
  try {
    const res = await listVouchers({
      keyword: keyword.value || undefined,
      status: status.value,
      sourceType: sourceType.value,
      from: dateOf(range.value?.[0]),
      to: dateOf(range.value?.[1]),
      page: page.value,
      size: size.value
    })
    rows.value = res.list || []
    total.value = res.total
  } catch (e: any) {
    message.error(e?.message || '加载凭证失败')
  } finally {
    loading.value = false
  }
}
function onTableChange(p: any) {
  const sizeChanged = p.pageSize !== size.value
  page.value = sizeChanged ? 1 : p.current
  size.value = p.pageSize
  load()
}
function reset() {
  keyword.value = ''
  status.value = undefined
  sourceType.value = undefined
  range.value = null
  page.value = 1
  load()
}
watch([keyword, status, sourceType, range], () => { page.value = 1; load() })

onMounted(async () => {
  try { subjects.value = await subjectOptions() } catch { /* 下拉可空，不阻断列表 */ }
  try {
    const dicts: DictOption[] = await fetchPublicDict('finance.voucher_type') || []
    voucherTypeOptions.value = dicts.map((d) => ({ label: d.label, value: d.value }))
  } catch { /* 字典可空，不阻断列表 */ }
  try {
    const list: ContractOptionVO[] = await fetchContractOptions() || []
    contractOptions.value = list.map((c) => ({ label: c.name ? `${c.no} · ${c.name}` : c.no, value: c.no }))
  } catch { /* 合同下拉可空，不阻断列表 */ }
  await loadOrgDirectory()
  load()
})

const traceOpen = ref(false)
const trace = ref<VoucherVO | null>(null)
const voucherTypeLabel = (v?: string) => voucherTypeOptions.value.find((o) => o.value === v)?.label || v || '记账凭证'
async function openTrace(id: string) {
  traceOpen.value = true
  try {
    trace.value = await getVoucher(id)
  } catch (e: any) {
    message.error(e?.message || '加载凭证详情失败')
    trace.value = null
  }
}

async function onSubmitReview(row: VoucherVO) {
  try {
    await submitVoucherReview(row.id)
    message.success(`凭证 ${row.no} 已提交审核`)
    load()
  } catch (e: any) { message.error(e?.message || '提交审核失败') }
}
async function onReview(row: VoucherVO) {
  try {
    await reviewVoucher(row.id)
    message.success(`凭证 ${row.no} 审核通过`)
    load()
  } catch (e: any) { message.error(e?.message || '审核失败') }
}
async function onPost(row: VoucherVO) {
  try {
    await postVoucher(row.id)
    message.success(`凭证 ${row.no} 已过账`)
    load()
  } catch (e: any) { message.error(e?.message || '过账失败') }
}
function onDelete(row: VoucherVO) {
  Modal.confirm({
    title: '删除凭证', content: `确定删除草稿凭证 ${row.no} 吗？`, centered: true,
    onOk: async () => {
      try { await deleteVoucher(row.id); message.success('已删除'); load() }
      catch (e: any) { message.error(e?.message || '删除失败') }
    }
  })
}

const drawerOpen = ref(false)
const saving = ref(false)
const form = reactive<VoucherSave>({
  voucherType: 'JZ', sourceType: 'MANUAL', sourceNo: '', summary: '', subject: '', amount: 0,
  date: dayjs().format('YYYY-MM-DD'), creator: '', dept: '', contractNo: '', fileIds: []
})
function openCreate() {
  Object.assign(form, {
    voucherType: 'JZ', sourceType: 'MANUAL', sourceNo: '', summary: '', subject: '', amount: 0,
    date: dayjs().format('YYYY-MM-DD'), creator: store.displayName, dept: '', contractNo: '', fileIds: []
  })
  drawerOpen.value = true
}
async function submit() {
  if (!form.summary?.trim() || !form.subject || !form.amount || form.amount <= 0 || !form.date) {
    message.warning('请填写摘要、会计科目、金额与凭证日期')
    return
  }
  saving.value = true
  try {
    await createVoucher({ ...form, amount: Number(form.amount) })
    drawerOpen.value = false
    message.success('凭证已创建')
    page.value = 1
    load()
  } catch (e: any) { message.error(e?.message || '创建凭证失败') }
  finally { saving.value = false }
}
</script>

<template>
  <section class="ft-page">
    <section class="ft-head toolbar">
      <h2>凭证管理</h2>
      <span class="divider" />
      <a-input-search v-model:value="keyword" placeholder="凭证号 / 摘要 / 来源单据" style="width: 220px" allow-clear />
      <a-select v-model:value="status" :options="statusOptions" placeholder="状态" style="width: 110px" allow-clear />
      <a-select v-model:value="sourceType" :options="sourceOptions" placeholder="来源类型" style="width: 130px" allow-clear />
      <a-range-picker v-model:value="range" format="YYYY-MM-DD" />
      <a-button @click="reset">重置</a-button>
      <span class="grow" />
      <a-button v-if="canEdit()" type="primary" @click="openCreate">＋ 新增凭证</a-button>
    </section>
    <section class="ft-scroll ft-bottom">
      <a-table class="ft-table" style="--ft-min-width: 1740px" row-key="id" :columns="columns" :data-source="rows"
               :loading="loading" :pagination="pagination()" @change="onTableChange"
               :class="{ 'tbl-empty-mode': !loading && !rows.length }"
               :scroll="rows.length ? { x: 1740 } : undefined">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'voucherType'">
            {{ voucherTypeLabel(record.voucherType) }}
          </template>
          <template v-else-if="column.key === 'summary'">
            <a-tooltip :title="record.summary"><span class="cell-ellipsis">{{ record.summary }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'amount'">{{ yuan(record.amount) }}</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'operators'">
            {{ record.reviewer || '-' }} / {{ record.poster || '-' }}
          </template>
          <template v-else-if="column.key === 'attachments'">
            <a v-if="record.fileIds?.length && record.attachmentUrls?.[0]" :href="record.attachmentUrls[0]" target="_blank">凭证（{{ record.fileIds.length }}）</a>
            <span v-else-if="record.fileIds?.length" class="muted">{{ record.fileIds.length }} 个</span>
            <span v-else class="muted">-</span>
          </template>
          <template v-else-if="column.key === 'source'">
            <a-tooltip v-if="record.sourceNo" :title="`${sourceLabel(record.sourceType)} · ${record.sourceNo}`">
              <a class="cell-ellipsis" @click="openTrace(record.id)">{{ sourceLabel(record.sourceType) }} · {{ record.sourceNo }}</a>
            </a-tooltip>
            <span v-else class="muted">{{ sourceLabel(record.sourceType) }}</span>
          </template>
          <template v-else-if="column.key === 'subject'">
            <a-tooltip :title="record.subject"><span class="cell-ellipsis">{{ record.subject || '-' }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'date'">{{ fmtDate(record.date) }}</template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a @click="openTrace(record.id)">追溯</a>
              <a v-if="canEdit() && record.status === 'DRAFT'" @click="onSubmitReview(record)">提交审核</a>
              <a v-if="canEdit() && record.status === 'REVIEW'" @click="onReview(record)">审核</a>
              <a v-if="canEdit() && (record.status === 'DRAFT' || record.status === 'REVIEW')" @click="onPost(record)">过账</a>
              <a v-if="canEdit() && record.status === 'DRAFT'" @click="onDelete(record)">删除</a>
            </a-space>
          </template>
        </template>
        <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="暂无凭证" /></div></template>
      </a-table>
    </section>

    <a-drawer v-model:open="drawerOpen" title="新增凭证" :width="520" placement="right" class="drawer-body-flex">
      <a-form layout="vertical">
        <a-form-item label="凭证类型">
          <a-select v-model:value="form.voucherType" :options="voucherTypeOptions" placeholder="记账凭证" style="width: 100%" />
        </a-form-item>
        <a-form-item label="凭证日期" required>
          <a-date-picker v-model:value="form.date" value-format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
        <a-form-item label="来源类型" required>
          <a-select v-model:value="form.sourceType" :options="sourceOptions" />
        </a-form-item>
        <a-form-item v-if="form.sourceType !== 'MANUAL'" label="来源单据号">
          <a-input v-model:value="form.sourceNo" placeholder="如 BX-20261126-000001" />
        </a-form-item>
        <a-form-item label="摘要" required>
          <a-textarea v-model:value="form.summary" :rows="3" placeholder="请输入凭证摘要" />
        </a-form-item>
        <a-form-item label="会计科目" required>
          <a-select v-model:value="form.subject" :options="subjects.map((s) => ({ label: `${s.code} ${s.name}`, value: s.name }))"
                    show-search option-filter-prop="label" placeholder="请选择会计科目" />
        </a-form-item>
        <a-form-item label="金额" required>
          <a-input-number v-model:value="form.amount" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>
        <a-form-item label="制单人">
          <a-input :value="form.creator || store.displayName" readonly placeholder="默认当前登录人，不可修改" />
        </a-form-item>
        <a-form-item label="归属部门">
          <a-select v-model:value="form.dept" :options="deptOptions" placeholder="从组织架构选择" show-search
                    :filter-option="filterOption" allow-clear style="width: 100%" />
        </a-form-item>
        <a-form-item label="关联合同号">
          <a-select v-model:value="form.contractNo" :options="contractOptions" placeholder="请选择关联合同（选填）"
                    show-search :filter-option="filterOption" allow-clear style="width: 100%" />
        </a-form-item>
        <AttachmentUpload v-model="form.fileIds" label="凭证附件" button-text="上传凭证附件"
                          :upload="uploadVoucherAttachment" />
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="drawerOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submit">确定</a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer v-model:open="traceOpen" title="凭证详情 / 来源追溯" :width="520" placement="right">
      <a-descriptions v-if="trace" :column="1" bordered size="small">
        <a-descriptions-item label="凭证号">{{ trace.no }}</a-descriptions-item>
        <a-descriptions-item label="凭证类型">{{ voucherTypeLabel(trace.voucherType) }}</a-descriptions-item>
        <a-descriptions-item label="来源类型">{{ sourceLabel(trace.sourceType) }}</a-descriptions-item>
        <a-descriptions-item label="来源单据">{{ trace.sourceNo || '-' }}</a-descriptions-item>
        <a-descriptions-item label="摘要">{{ trace.summary }}</a-descriptions-item>
        <a-descriptions-item label="会计科目">{{ trace.subject }}</a-descriptions-item>
        <a-descriptions-item label="金额">{{ yuan(trace.amount) }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ statusLabel(trace.status) }}</a-descriptions-item>
        <a-descriptions-item label="关联合同">{{ trace.contractNo || '-' }}</a-descriptions-item>
        <a-descriptions-item label="归属部门">{{ trace.dept || '-' }}</a-descriptions-item>
        <a-descriptions-item label="制单人">{{ trace.creator || '-' }}</a-descriptions-item>
        <a-descriptions-item label="审核人 / 过账人">{{ trace.reviewer || '-' }} / {{ trace.poster || '-' }}</a-descriptions-item>
        <a-descriptions-item label="日期">{{ fmtDate(trace.date) }}</a-descriptions-item>
        <a-descriptions-item label="凭证附件">
          <template v-if="trace.fileIds?.length">
            <a v-for="(url, idx) in trace.attachmentUrls || []" :key="url" :href="url" target="_blank" style="margin-right: 10px">附件 {{ idx + 1 }}</a>
            <span v-if="!trace.attachmentUrls?.length" class="muted">{{ trace.fileIds.length }} 个（签名链接不可用）</span>
          </template>
          <span v-else class="muted">无附件</span>
        </a-descriptions-item>
      </a-descriptions>
      <p class="hint trace-note">单据链路：源单据 → 审批通过 → 生成凭证 → 过账入表 → 账表与报表。</p>
      <template #footer>
        <div class="drawer-footer"><a-button type="primary" @click="traceOpen = false">关闭</a-button></div>
      </template>
    </a-drawer>
  </section>
</template>

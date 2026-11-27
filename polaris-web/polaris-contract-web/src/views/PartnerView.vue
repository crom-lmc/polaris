<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/composables/useIsMobile'
import {
  listPartners, getPartner, savePartner, updatePartner, togglePartner, deletePartner, fetchPublicDict,
  type PartnerVO, type PartnerSave, type DictOption
} from '@/api/contract'

const store = useUserStore()
const isMobile = useIsMobile()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 760))

const loading = ref(false)
const rows = ref<PartnerVO[]>([])
const total = ref(0)
const query = reactive({
  code: '', keyword: '', role: undefined as string | undefined,
  type: undefined as string | undefined, status: undefined as string | undefined,
  page: 1, size: 20
})

const roleLabel: Record<string, string> = { SUPPLIER: '供应商', CUSTOMER: '客户', BOTH: '两者' }
const subjectTypeLabel: Record<string, string> = { COMPANY: '企业', PERSON: '个人' }
const statusLabel: Record<string, string> = { COOPERATING: '合作中', PENDING: '待准入', STOPPED: '已停用' }
const statusColor: Record<string, string> = { COOPERATING: 'green', PENDING: 'orange', STOPPED: 'default' }
const roleOptions = [
  { label: '供应商', value: 'SUPPLIER' }, { label: '客户', value: 'CUSTOMER' }, { label: '两者', value: 'BOTH' }
]
const subjectTypeOptions = [{ label: '企业', value: 'COMPANY' }, { label: '个人', value: 'PERSON' }]
const statusOptions = Object.entries(statusLabel).map(([value, label]) => ({ value, label }))
const ratingOptions = [{ label: 'A 级', value: 'A' }, { label: 'B 级', value: 'B' }, { label: 'C 级', value: 'C' }]
const taxpayerOptions = [{ label: '一般纳税人', value: '一般纳税人' }, { label: '小规模纳税人', value: '小规模纳税人' }]
/** 企业类型字典（settings contract.enterprise_type）；个人主体固定 PERSON */
const enterpriseTypeOptions = ref<DictOption[]>([])
const enterpriseTypeLabel = (v?: string) => enterpriseTypeOptions.value.find((o) => o.value === v)?.label || v || '-'
/** 结算账期字典（复用 finance.credit_period：30/60/90/180/365），存量「30 天账期」文本兼容映射 */
const termsOptions = ref<DictOption[]>([])
function normalizeTerms(v?: string) {
  if (!v) return ''
  const raw = String(v).trim()
  if (termsOptions.value.some((o) => o.value === raw)) return raw
  const days = raw.match(/\d+/)?.[0]
  return days && termsOptions.value.some((o) => o.value === days) ? days : raw
}
const termsLabel = (v?: string) => {
  const normalized = normalizeTerms(v)
  return termsOptions.value.find((o) => o.value === normalized)?.label || v || '-'
}
const bankEditable = computed(() => store.can('contract:partner:bank:view'))
/** 后端不提供 type 查询参数，企业类型按当前页数据本地过滤 */
const displayRows = computed(() => (query.type ? rows.value.filter((r) => r.type === query.type) : rows.value))

const stats = computed(() => ({
  total: total.value,
  supplier: rows.value.filter((r) => r.role === 'SUPPLIER' || r.role === 'BOTH').length,
  customer: rows.value.filter((r) => r.role === 'CUSTOMER' || r.role === 'BOTH').length
}))

const pagination = computed(() => ({
  current: query.page, pageSize: query.size, total: total.value, showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`, onChange: (p: number, size: number) => {
    query.page = p; query.size = size; load()
  }
}))

async function load() {
  loading.value = true
  try {
    const res = await listPartners({
      code: query.code || undefined, keyword: query.keyword || undefined,
      role: query.role, status: query.status, page: query.page, size: query.size
    })
    rows.value = res.records || []
    total.value = res.total || 0
  } catch (e: any) { message.error(e?.message || '加载往来单位失败') }
  finally { loading.value = false }
}
function resetAndLoad() { query.page = 1; load() }

const columns = [
  { title: '单位编码', dataIndex: 'code', key: 'code', width: 120 },
  { title: '单位名称', key: 'name', width: 200 },
  { title: '角色', key: 'role', align: 'center' as const, width: 90 },
  { title: '主体类型', key: 'subjectType', align: 'center' as const, width: 100 },
  { title: '企业类型', dataIndex: 'type', key: 'type', width: 110 },
  { title: '信用代码 / 身份证号', dataIndex: 'creditCode', key: 'creditCode', width: 200 },
  { title: '法定代表人', dataIndex: 'legalPerson', key: 'legalPerson', width: 100 },
  { title: '联系人', dataIndex: 'contact', key: 'contact', width: 100 },
  { title: '联系电话', dataIndex: 'phone', key: 'phone', width: 130 },
  { title: '结算账期', dataIndex: 'terms', key: 'terms', width: 130 },
  { title: '评级', key: 'rating', align: 'center' as const, width: 80 },
  { title: '合作状态', key: 'status', align: 'center' as const, width: 110 },
  { title: '操作', key: 'action', width: 260 }
]

/* 详情 */
const detailOpen = ref(false)
const detail = ref<PartnerVO | null>(null)
async function openDetail(id: string) {
  try { detail.value = await getPartner(id); detailOpen.value = true }
  catch (e: any) { message.error(e?.message || '加载详情失败') }
}
function switchStatus(row: PartnerVO) {
  const next = row.status === 'COOPERATING' ? 'STOPPED' : 'COOPERATING'
  Modal.confirm({
    centered: true, title: '切换合作状态',
    content: `确定将「${row.name}」切换为${statusLabel[next]}吗？`,
    onOk: async () => {
      try { await togglePartner(row.id, next); message.success('状态已更新'); load(); if (detailOpen.value && detail.value) detail.value = await getPartner(row.id) }
      catch (e: any) { message.error(e?.message || '操作失败') }
    }
  })
}
function remove(row: PartnerVO) {
  Modal.confirm({
    centered: true, title: '删除往来单位', content: `确定删除「${row.name}」吗？已被合同引用的单位不可删除。`,
    okButtonProps: { danger: true },
    onOk: async () => { try { await deletePartner(row.id); message.success('已删除'); load() } catch (e: any) { message.error(e?.message || '删除失败') } }
  })
}

/* 新增 / 编辑 */
const editOpen = ref(false)
const editing = ref<string | null>(null)
const saving = ref(false)
const emptyForm = (): PartnerSave => ({
  code: '', name: '', subjectType: 'COMPANY', role: 'SUPPLIER', type: '',
  creditCode: '', legalPerson: '', registeredCapital: '', establishDate: undefined, taxpayerType: '一般纳税人',
  address: '', remark: '', contact: '', phone: '', terms: '30', rating: 'B',
  creditLimit: 0, creditUsed: 0, status: 'PENDING',
  bank: '', accountName: '', bankAccount: ''
})
const form = reactive<PartnerSave>(emptyForm())
const descColumn = computed(() => (isMobile.value ? 1 : 2))
/** 主体类型=个人：企业类型自动带出「个人」且不可修改；切回企业时清空让用户重新选 */
const isPerson = computed(() => form.subjectType === 'PERSON')
watch(() => form.subjectType, (val) => {
  if (val === 'PERSON') form.type = 'PERSON'
  else if (form.type === 'PERSON') form.type = ''
})
function openEdit(row?: PartnerVO) {
  detailOpen.value = false
  editing.value = row?.id || null
  Object.assign(form, emptyForm())
  if (row) {
    Object.assign(form, {
      code: row.code, name: row.name, subjectType: row.subjectType, role: row.role, type: row.type,
      creditCode: row.creditCode || '', legalPerson: row.legalPerson || '', registeredCapital: row.registeredCapital || '',
      establishDate: row.establishDate || undefined, taxpayerType: row.taxpayerType || '', address: row.address || '',
      remark: row.remark || '', contact: row.contact || '', phone: row.phone || '', terms: normalizeTerms(row.terms),
      rating: row.rating || 'B', creditLimit: row.creditLimit ?? 0, creditUsed: row.creditUsed ?? 0,
      status: row.status || 'PENDING', bank: row.bank || '', accountName: row.accountName || '',
      // 无明文权限时后端返回脱敏串，不可回写，留空且禁用
      bankAccount: bankEditable.value ? (row.bankAccount || '') : ''
    })
  }
  editOpen.value = true
}
async function submit() {
  const missing: string[] = []
  const requireText = (value: unknown, label: string) => {
    if (value == null || (typeof value === 'string' && !value.trim())) missing.push(label)
  }
  // 编码由 settings 单号规则自动生成（GYS-yyyy-XXXXXX），新增/编辑均不可手输，无需校验
  requireText(form.name, '单位名称')
  requireText(form.subjectType, '主体类型')
  requireText(form.role, '业务角色')
  requireText(form.type, '企业类型')
  requireText(form.creditCode, form.subjectType === 'PERSON' ? '身份证号' : '统一社会信用代码')
  if (form.subjectType !== 'PERSON') {
    requireText(form.legalPerson, '法定代表人')
    requireText(form.registeredCapital, '注册资本')
    requireText(form.establishDate, '成立日期')
    requireText(form.taxpayerType, '纳税人性质')
  }
  requireText(form.address, form.subjectType === 'PERSON' ? '住址' : '注册地址')
  requireText(form.contact, '联系人')
  requireText(form.phone, '联系电话')
  requireText(form.terms, '结算账期')
  if (termsOptions.value.length && !termsOptions.value.some((o) => o.value === form.terms)) missing.push('结算账期（需选字典档位）')
  requireText(form.rating, '评级')
  requireText(form.status, '合作状态')
  // 授信额度/已占用额度是数字，0 合法，用 == null 判空
  if (form.role === 'CUSTOMER' || form.role === 'BOTH') {
    if (form.creditLimit == null) missing.push('授信额度')
    if (form.creditUsed == null) missing.push('已占用额度')
  }
  requireText(form.bank, '开户行')
  requireText(form.accountName, '账户名称')
  // 编辑态且无银行明文权限时账号被禁用且后端返回脱敏串（前端留空），豁免校验，否则该角色永远无法保存
  if (!editing.value || bankEditable.value) requireText(form.bankAccount, '银行账号')
  if (missing.length) { message.warning(`请填写必填项：${missing.join('、')}`); return }
  saving.value = true
  try {
    const payload: PartnerSave = editing.value
      ? { ...form, code: form.code.trim(), name: form.name.trim() }
      : { ...form, code: '', name: form.name.trim() }
    if (editing.value) await updatePartner(editing.value, payload)
    else await savePartner(payload)
    editOpen.value = false
    message.success('往来单位已保存')
    resetAndLoad()
  } catch (e: any) { message.error(e?.message || '保存失败') }
  finally { saving.value = false }
}
onMounted(() => {
  load()
  fetchPublicDict('contract.enterprise_type').then((list) => { enterpriseTypeOptions.value = list || [] })
    .catch(() => { message.warning('企业类型字典加载失败，请检查配置中心') })
  fetchPublicDict('finance.credit_period').then((list) => { termsOptions.value = list || [] })
    .catch(() => { message.warning('结算账期字典加载失败，请检查配置中心') })
})
</script>

<template>
  <section class="ft-page">
    <section class="ft-head toolbar">
      <h2>往来单位维护</h2>
      <span class="divider" />
      <a-input-search v-model:value="query.code" placeholder="单位编码" style="width: 150px" allow-clear @search="resetAndLoad" />
      <a-input-search v-model:value="query.keyword" placeholder="名称 / 联系人" style="width: 180px" allow-clear @search="resetAndLoad" />
      <a-select v-model:value="query.role" :options="roleOptions" placeholder="全部角色" style="width: 120px" allow-clear @change="resetAndLoad" />
      <a-select v-model:value="query.type" :options="enterpriseTypeOptions" placeholder="企业类型" style="width: 130px" allow-clear @change="resetAndLoad" />
      <a-select v-model:value="query.status" :options="statusOptions" placeholder="合作状态" style="width: 120px" allow-clear @change="resetAndLoad" />
      <span class="grow" />
      <a-button v-if="store.can('contract:partner:edit')" type="primary" @click="openEdit()">＋ 新增客户/供应商</a-button>
    </section>
    <section class="ft-scroll ft-bottom">
      <div class="stat-grid">
        <div class="stat-card"><span class="st-label">往来单位档案</span><strong class="st-value">{{ stats.total }} 家</strong><span class="st-foot"><b>统一准入与账期维护</b></span></div>
        <div class="stat-card"><span class="st-label">供应商</span><strong class="st-value">{{ stats.supplier }} 家</strong><span class="st-foot"><b>本页 · 可用于采购合同</b></span></div>
        <div class="stat-card"><span class="st-label">客户</span><strong class="st-value">{{ stats.customer }} 家</strong><span class="st-foot"><b>本页 · 可用于销售合同</b></span></div>
      </div>
      <a-table class="ft-table partner-table" row-key="id" :columns="columns" :data-source="displayRows" :loading="loading"
               :pagination="pagination" :class="{ 'tbl-empty-mode': !loading && !displayRows.length }"
               :scroll="displayRows.length ? { x: 1750 } : undefined">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <a-tooltip :title="record.name"><span class="cell-ellipsis">{{ record.name }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'creditCode'">
            <a-tooltip :title="record.creditCode"><span class="cell-ellipsis">{{ record.creditCode || '-' }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'role'">
            <a-tag :color="record.role === 'SUPPLIER' ? 'blue' : record.role === 'CUSTOMER' ? 'green' : 'purple'">{{ roleLabel[record.role] || record.role }}</a-tag>
          </template>
          <template v-else-if="column.key === 'subjectType'">
            <a-tag :color="record.subjectType === 'PERSON' ? 'purple' : 'default'">{{ subjectTypeLabel[record.subjectType] || '企业' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'type'">{{ enterpriseTypeLabel(record.type) }}</template>
          <template v-else-if="column.key === 'terms'">{{ termsLabel(record.terms) }}</template>
          <template v-else-if="column.key === 'rating'"><a-tag>{{ record.rating }} 级</a-tag></template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor[record.status] || 'default'">{{ statusLabel[record.status] || record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <span class="action-links">
              <a @click="openDetail(record.id)">详情</a>
              <template v-if="store.can('contract:partner:edit')">
                <a @click="openEdit(record)">编辑</a>
                <a @click="switchStatus(record)">{{ record.status === 'COOPERATING' ? '停用' : '启用合作' }}</a>
                <a @click="remove(record)">删除</a>
              </template>
            </span>
          </template>
        </template>
        <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="暂无往来单位" /></div></template>
      </a-table>
    </section>

    <a-drawer v-model:open="detailOpen" title="往来单位详情" :width="drawerWidth" placement="right">
      <template v-if="detail">
        <a-card class="panel-card" size="small" title="基本信息">
          <a-descriptions :column="descColumn" bordered size="small">
            <a-descriptions-item label="单位编码">{{ detail.code }}</a-descriptions-item>
            <a-descriptions-item label="单位名称">{{ detail.name }}</a-descriptions-item>
            <a-descriptions-item label="主体类型">{{ subjectTypeLabel[detail.subjectType] || '企业' }}</a-descriptions-item>
            <a-descriptions-item label="业务角色">{{ roleLabel[detail.role] || detail.role }}</a-descriptions-item>
            <a-descriptions-item label="企业类型">{{ enterpriseTypeLabel(detail.type) }}</a-descriptions-item>
            <a-descriptions-item :label="detail.subjectType === 'PERSON' ? '身份证号' : '统一社会信用代码'">{{ detail.creditCode || '-' }}</a-descriptions-item>
            <template v-if="detail.subjectType !== 'PERSON'">
              <a-descriptions-item label="法定代表人">{{ detail.legalPerson || '-' }}</a-descriptions-item>
              <a-descriptions-item label="注册资本">{{ detail.registeredCapital || '-' }}</a-descriptions-item>
              <a-descriptions-item label="成立日期">{{ detail.establishDate || '-' }}</a-descriptions-item>
              <a-descriptions-item label="纳税人性质">{{ detail.taxpayerType || '-' }}</a-descriptions-item>
            </template>
            <a-descriptions-item :label="detail.subjectType === 'PERSON' ? '住址' : '注册地址'">{{ detail.address || '-' }}</a-descriptions-item>
            <a-descriptions-item label="备注">{{ detail.remark || '-' }}</a-descriptions-item>
          </a-descriptions>
        </a-card>
        <a-card class="panel-card" size="small" title="联系与结算信息">
          <a-descriptions :column="descColumn" bordered size="small">
            <a-descriptions-item label="联系人 / 电话">{{ detail.contact || '-' }} · {{ detail.phone || '-' }}</a-descriptions-item>
            <a-descriptions-item label="结算账期">{{ termsLabel(detail.terms) }}</a-descriptions-item>
            <a-descriptions-item label="评级">{{ detail.rating || '-' }} 级</a-descriptions-item>
            <a-descriptions-item label="合作状态">{{ statusLabel[detail.status] || detail.status }}</a-descriptions-item>
            <template v-if="detail.role === 'CUSTOMER' || detail.role === 'BOTH'">
              <a-descriptions-item label="授信额度">{{ detail.creditLimit ? detail.creditLimit + ' 元' : '不限制' }}</a-descriptions-item>
              <a-descriptions-item label="已占用额度">{{ detail.creditUsed || 0 }} 元</a-descriptions-item>
            </template>
          </a-descriptions>
        </a-card>
        <a-card class="panel-card" size="small" title="银行信息">
          <a-descriptions :column="descColumn" bordered size="small">
            <a-descriptions-item label="开户行">{{ detail.bank || '-' }}</a-descriptions-item>
            <a-descriptions-item label="账户名称">{{ detail.accountName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="银行账号">{{ detail.bankAccount || '-' }}</a-descriptions-item>
          </a-descriptions>
          <div class="panel-hint" style="margin-top: 8px">
            <a-tag :color="detail.bankMasked ? 'orange' : 'green'">{{ detail.bankMasked ? '已脱敏' : '已授权查看明文' }}</a-tag>
            银行账号按 <code>contract:partner:bank:view</code> 权限由后端脱敏展示。
          </div>
        </a-card>
      </template>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="detailOpen = false">关闭</a-button>
          <a-button v-if="store.can('contract:partner:edit') && detail" type="primary" @click="openEdit(detail)">编辑档案</a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer v-model:open="editOpen" :title="`${editing ? '编辑' : '新增'}客户/供应商`" :width="drawerWidth" placement="right">
      <a-form layout="vertical">
        <a-card class="panel-card" size="small" title="基本信息">
          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item :label="editing ? '单位编码' : '单位编码（自动生成）'">
                <a-input v-model:value="form.code" disabled
                         :placeholder="editing ? '' : '保存时按规则自动生成'" />
              </a-form-item>
            </a-col>
            <a-col :span="12"><a-form-item label="单位名称" required><a-input v-model:value="form.name" /></a-form-item></a-col>
          </a-row>
          <a-row :gutter="12">
            <a-col :span="12"><a-form-item label="主体类型" required><a-select v-model:value="form.subjectType" :options="subjectTypeOptions" /></a-form-item></a-col>
            <a-col :span="12"><a-form-item label="业务角色" required><a-select v-model:value="form.role" :options="roleOptions" /></a-form-item></a-col>
          </a-row>
          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item label="企业类型" required>
                <a-select v-model:value="form.type" :options="enterpriseTypeOptions" :disabled="isPerson"
                          :placeholder="isPerson ? '个人（主体类型带出）' : '请选择企业类型'" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item :label="form.subjectType === 'PERSON' ? '身份证号' : '统一社会信用代码'" required>
                <a-input v-model:value="form.creditCode" :maxlength="18" />
              </a-form-item>
            </a-col>
          </a-row>
          <template v-if="form.subjectType !== 'PERSON'">
            <a-row :gutter="12">
              <a-col :span="12"><a-form-item label="法定代表人" required><a-input v-model:value="form.legalPerson" /></a-form-item></a-col>
              <a-col :span="12"><a-form-item label="注册资本" required><a-input v-model:value="form.registeredCapital" /></a-form-item></a-col>
            </a-row>
            <a-row :gutter="12">
              <a-col :span="12">
                <a-form-item label="成立日期" required>
                  <a-date-picker v-model:value="form.establishDate" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="12"><a-form-item label="纳税人性质" required><a-select v-model:value="form.taxpayerType" :options="taxpayerOptions" /></a-form-item></a-col>
            </a-row>
          </template>
          <a-form-item :label="form.subjectType === 'PERSON' ? '住址' : '注册地址'" required><a-input v-model:value="form.address" /></a-form-item>
          <a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item>
        </a-card>
        <a-card class="panel-card" size="small" title="联系与结算信息">
          <a-row :gutter="12">
            <a-col :span="12"><a-form-item label="联系人" required><a-input v-model:value="form.contact" /></a-form-item></a-col>
            <a-col :span="12"><a-form-item label="联系电话" required><a-input v-model:value="form.phone" /></a-form-item></a-col>
          </a-row>
          <a-form-item label="结算账期" required>
            <a-select v-model:value="form.terms" :options="termsOptions" placeholder="从字典选择" show-search
                      option-filter-prop="label" style="width: 100%" />
          </a-form-item>
          <a-row :gutter="12">
            <a-col :span="12"><a-form-item label="评级" required><a-select v-model:value="form.rating" :options="ratingOptions" /></a-form-item></a-col>
            <a-col :span="12"><a-form-item label="合作状态" required><a-select v-model:value="form.status" :options="statusOptions" /></a-form-item></a-col>
          </a-row>
          <template v-if="form.role === 'CUSTOMER' || form.role === 'BOTH'">
            <a-row :gutter="12">
              <a-col :span="12">
                <a-form-item label="授信额度" required>
                  <a-input-number v-model:value="form.creditLimit" :min="0" :precision="2" style="width: 100%" placeholder="0 表示不限制" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="已占用额度" required>
                  <a-input-number v-model:value="form.creditUsed" :min="0" :precision="2" style="width: 100%" />
                </a-form-item>
              </a-col>
            </a-row>
          </template>
        </a-card>
        <a-card class="panel-card" size="small" title="银行信息">
          <a-form-item label="开户行" required><a-input v-model:value="form.bank" /></a-form-item>
          <a-form-item label="账户名称" required><a-input v-model:value="form.accountName" /></a-form-item>
          <a-form-item label="银行账号" :required="!editing || bankEditable">
            <a-input v-model:value="form.bankAccount" :disabled="!!editing && !bankEditable"
                     :placeholder="editing && !bankEditable ? '无明文权限，如需修改请联系管理员' : '对公 / 个人账户'" />
          </a-form-item>
        </a-card>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="editOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submit">保存</a-button>
        </div>
      </template>
    </a-drawer>
  </section>
</template>

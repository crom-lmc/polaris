<template>
  <div class="ft-page">
    <div class="toolbar">
      <a-input-search
        v-model:value="keyword"
        placeholder="登录用户名"
        style="width: 240px"
        allow-clear
        @search="handleSearch"
      />
      <a-select v-model:value="loginType" style="width: 160px" :options="typeOptions" @change="handleSearch" />
      <a-button @click="load">刷新</a-button>
    </div>

    <a-alert
      type="info"
      show-icon
      style="margin-bottom: 12px"
      message="登录审计的操作人填尝试登录的用户名——失败时用户可能不存在也照记，便于排查撞库；不记为 SYSTEM。"
    />
    <a-alert v-if="errorMsg" :message="errorMsg" type="warning" show-icon style="margin-bottom: 12px" />

    <section class="ft-scroll ft-bottom login-audit-table">
      <a-table
        :columns="columns"
        :data-source="records"
        :loading="loading"
        row-key="id"
        :pagination="pagination"
        :scroll="{ x: 1140 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'loginType'">
            {{ loginTypeLabel(record.loginType) }}
          </template>
          <template v-else-if="column.key === 'result'">
            <a-tag :color="record.result === 'fail' ? 'red' : 'green'">
              {{ record.result === 'fail' ? '失败' : '成功' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'failReason'">
            <a-tooltip v-if="record.failReason" :title="record.failReason">
              <span class="cell-ellipsis muted">{{ record.failReason }}</span>
            </a-tooltip>
            <span v-else class="muted">-</span>
          </template>
          <template v-else-if="column.key === 'userAgent'">
            <a-tooltip v-if="record.userAgent" :title="record.userAgent">
              <span class="cell-ellipsis ua">{{ record.userAgent }}</span>
            </a-tooltip>
            <span v-else class="muted">-</span>
          </template>
        </template>
      </a-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchLoginLogs, type LoginLog } from '@/api/audit'

const columns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '用户名', dataIndex: 'username', key: 'username', width: 150 },
  { title: '登录方式', dataIndex: 'loginType', key: 'loginType', width: 110 },
  { title: '结果', key: 'result', width: 90 },
  { title: '失败原因', key: 'failReason', width: 240 },
  { title: 'IP', dataIndex: 'clientIp', key: 'clientIp', width: 140 },
  { title: '客户端', key: 'userAgent', width: 240 }
]
// 登录方式编码 → 中文名；表格与筛选下拉共用，未知编码回退原值
const loginTypeNames: Record<string, string> = {
  password: '密码',
  email: '邮箱验证码'
}
function loginTypeLabel(code?: string): string {
  return code ? (loginTypeNames[code] || code) : '-'
}
const typeOptions = [
  { label: '全部方式', value: '' },
  ...Object.entries(loginTypeNames).map(([value, label]) => ({ label, value }))
]

const records = ref<LoginLog[]>([])
const loading = ref(false)
const errorMsg = ref('')
const keyword = ref('')
const loginType = ref('')
const total = ref(0)
const current = ref(1)
const pageSize = ref(20)

const pagination = computed(() => ({
  current: current.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`
}))

onMounted(load)

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await fetchLoginLogs({
      page: current.value,
      size: pageSize.value,
      keyword: keyword.value || undefined,
      loginType: loginType.value || undefined
    })
    records.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e: any) {
    errorMsg.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function handleSearch() { current.value = 1; load() }
function handleTableChange(pag: any) { current.value = pag.current; pageSize.value = pag.pageSize; load() }
</script>

<style scoped>
.login-audit-table { --ft-min-width: 1140px; }
.toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.muted { color: var(--muted); }
.ua { font-size: 12px; }
/* 表头按列宽单行展示，避免「登录方式 / 失败原因」被挤成竖排 */
:deep(.ant-table-thead > tr > th) { white-space: nowrap; }
/* 长文本按列宽自适应省略，完整内容由 a-tooltip 悬浮展示 */
.cell-ellipsis { display: block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>

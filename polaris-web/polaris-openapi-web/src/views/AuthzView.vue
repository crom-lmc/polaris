<template>
  <div class="ft-page authz-page">
    <div class="ft-head filter-bar">
      <a-select
        v-model:value="appFilter"
        show-search
        allow-clear
        placeholder="应用"
        style="width: 220px"
        :options="appOptions"
        :loading="appsLoading"
        option-filter-prop="label"
      />
      <a-input-search v-model:value="keyword" placeholder="搜 API 编码 / 名称 / 权限码" style="width: 280px" allow-clear />
      <a-button v-if="canEdit" type="primary" @click="openGrant">新增授权</a-button>
      <a-button @click="load">刷新</a-button>
    </div>

    <section class="ft-scroll ft-bottom">
      <a-table
        class="ft-table"
        :columns="columns"
        :data-source="filteredRows"
        :loading="loading"
        :row-key="(record: AuthzRow) => `${record.appCode}:${record.apiCode}`"
        :pagination="{ pageSize: 10, showSizeChanger: true, showTotal: (t: number) => `共 ${t} 条` }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'appCode'">
            <span class="mono">{{ record.appCode }}</span>
          </template>
          <template v-else-if="column.key === 'direction'">
            <a-tag :color="record.direction === 1 ? 'green' : 'blue'">{{ record.directionLabel }}</a-tag>
          </template>
          <template v-else-if="column.key === 'operationType'">
            <a-tag v-if="record.operationType">{{ record.operationType }}</a-tag>
            <span v-else class="muted">-</span>
          </template>
          <template v-else-if="column.key === 'apiCode'">
            <span class="mono">{{ record.apiCode }}</span>
          </template>
          <template v-else-if="column.key === 'path'">
            <span class="mono">{{ record.path }}</span>
          </template>
          <template v-else-if="column.key === 'requiredPerm'">
            <span class="mono">{{ record.requiredPerm }}</span>
          </template>
          <template v-else-if="column.key === 'endpointStatus'">
            <a-tag :color="record.endpointStatus === 1 ? 'green' : 'default'">
              {{ record.endpointStatus === 1 ? '启用' : '停用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'green' : 'default'">
              {{ record.status === 1 ? '启用' : '停用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button v-if="canEdit" type="link" size="small" @click="doToggle(record)">
              {{ record.status === 1 ? '停用' : '启用' }}
            </a-button>
            <a-tooltip v-if="canEdit && record.status === 1" title="请先停用授权，再删除">
              <a-button type="link" size="small" danger disabled>删除</a-button>
            </a-tooltip>
            <a-popconfirm
              v-else-if="canEdit"
              title="确定删除这条接口授权吗？删除后需重新授权。"
              ok-text="删除"
              cancel-text="取消"
              @confirm="doRevoke(record)"
            >
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </template>
        </template>
        <template #emptyText>
          <div class="authz-empty">
            <a-empty :image-style="{ height: '48px' }" description="暂无授权记录" />
          </div>
        </template>
      </a-table>
    </section>

    <!-- 新增授权 -->
    <a-drawer
      v-model:open="modalOpen"
      title="新增授权"
      width="900"
      :body-style="{ paddingBottom: '80px' }"
    >
      <a-form layout="vertical">
        <a-form-item label="第三方应用" required>
          <a-select
            v-model:value="form.appCode"
            show-search
            option-filter-prop="label"
            :options="appOptions"
            :loading="appsLoading"
            placeholder="选择对接应用"
            @change="onAppChange"
          />
        </a-form-item>
        <a-form-item label="选择接口" required :extra="`已选 ${selectedApiCodes.length} 个，每个接口独立授权，已授权的接口不可重复选择`">
          <a-input-search v-model:value="apiKeyword" placeholder="搜 API 编码 / 名称" style="margin-bottom: 10px" allow-clear />
          <div class="api-picker">
            <a-table
              :columns="pickerColumns"
              :data-source="pickableEndpoints"
              :row-selection="{ selectedRowKeys: selectedApiCodes, onChange: onSelectChange, getCheckboxProps }"
              :pagination="false"
              size="small"
              row-key="apiCode"
              :scroll="{ y: 420 }"
            >
              <template #bodyCell="{ column, record, index }">
                <template v-if="column.key === 'idx'">{{ index + 1 }}</template>
                <template v-else-if="column.key === 'apiCode'"><span class="mono">{{ record.apiCode }}</span></template>
                <template v-else-if="column.key === 'direction'">
                  <a-tag :color="record.direction === 1 ? 'green' : 'blue'">
                    {{ record.direction === 1 ? '出·对外提供' : '进·调用第三方' }}
                  </a-tag>
                </template>
              </template>
            </a-table>
          </div>
        </a-form-item>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="modalOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="doGrant">确认授权</a-button>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  listApps,
  listAuthz,
  listEndpoints,
  grantAuthz,
  revokeAuthz,
  toggleAuthz,
  type AppInfo,
  type ApiEndpoint,
  type AuthzRow
} from '@/api/openapi'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const canEdit = computed(() => store.can('openapi:app:auth'))

const columns = [
  { title: '应用编码', dataIndex: 'appCode', key: 'appCode', width: 150 },
  { title: '应用名称', dataIndex: 'appName', key: 'appName', width: 150, ellipsis: true },
  { title: '方向', dataIndex: 'directionLabel', key: 'direction', width: 130 },
  { title: '操作类型', dataIndex: 'operationType', key: 'operationType', width: 100 },
  { title: 'API 编码', dataIndex: 'apiCode', key: 'apiCode', width: 170 },
  { title: 'API 名称', dataIndex: 'apiName', key: 'apiName', width: 130, ellipsis: true },
  { title: '方法', dataIndex: 'method', key: 'method', width: 80 },
  { title: '路径', dataIndex: 'path', key: 'path', width: 200, ellipsis: true },
  { title: '所需权限码', dataIndex: 'requiredPerm', key: 'requiredPerm', width: 190, ellipsis: true },
  { title: '接口状态', dataIndex: 'endpointStatus', key: 'endpointStatus', width: 100 },
  { title: '授权状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '操作', key: 'action', width: 150 }
]

const pickerColumns = [
  { title: '#', key: 'idx', width: 44 },
  { title: 'API 编码', dataIndex: 'apiCode', key: 'apiCode', width: 160 },
  { title: '名称', dataIndex: 'apiName', key: 'apiName', width: 130, ellipsis: true },
  { title: '方向', dataIndex: 'direction', key: 'direction', width: 130 },
  { title: '方法', dataIndex: 'method', key: 'method', width: 76 },
  { title: '路径', dataIndex: 'path', key: 'path', ellipsis: true }
]

const rows = ref<AuthzRow[]>([])
const apps = ref<AppInfo[]>([])
const endpoints = ref<ApiEndpoint[]>([])
const loading = ref(false)
const appsLoading = ref(false)
const appFilter = ref<string | undefined>(undefined)
const keyword = ref('')

const appOptions = computed(() =>
  apps.value.map((a) => ({ label: `${a.name}（${a.code}）`, value: a.code }))
)

const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return rows.value.filter((r) => {
    if (appFilter.value && r.appCode !== appFilter.value) return false
    if (!kw) return true
    return (r.apiCode + r.apiName + r.requiredPerm).toLowerCase().includes(kw)
  })
})

async function load() {
  loading.value = true
  try {
    rows.value = await listAuthz()
  } catch (e: any) {
    message.error(e?.message || '加载授权列表失败')
  } finally {
    loading.value = false
  }
}

async function loadApps() {
  appsLoading.value = true
  try {
    apps.value = await listApps()
  } catch (e: any) {
    message.error(e?.message || '加载应用列表失败')
  } finally {
    appsLoading.value = false
  }
}

/* ---------------- 新增授权 ---------------- */
const modalOpen = ref(false)
const saving = ref(false)
const apiKeyword = ref('')
const selectedApiCodes = ref<string[]>([])
const form = reactive({ appCode: '' })

/** 已授权集合：按所选应用过滤，已授权的接口不参与重复选择（一行一个接口） */
const grantedApiCodes = computed(() => {
  if (!form.appCode) return new Set<string>()
  return new Set(
    rows.value.filter((r) => r.appCode === form.appCode).map((r) => r.apiCode)
  )
})

const pickableEndpoints = computed(() => {
  const kw = apiKeyword.value.trim().toLowerCase()
  return endpoints.value.filter((e) => {
    if (!e.requiredPerm) return false // 没配权限码的接口无法授权
    if (kw && !(e.apiCode + e.apiName).toLowerCase().includes(kw)) return false
    return true
  })
})

function getCheckboxProps(record: ApiEndpoint) {
  return { disabled: grantedApiCodes.value.has(record.apiCode) }
}

function onSelectChange(keys: readonly (string | number)[]) {
  selectedApiCodes.value = keys.map(String)
}

function onAppChange() {
  selectedApiCodes.value = []
  apiKeyword.value = ''
}

async function openGrant() {
  form.appCode = ''
  selectedApiCodes.value = []
  apiKeyword.value = ''
  modalOpen.value = true
  if (!apps.value.length) await loadApps()
  if (!endpoints.value.length) {
    try {
      endpoints.value = await listEndpoints()
    } catch (e: any) {
      message.error(e?.message || '加载接口列表失败')
    }
  }
}

async function doGrant() {
  if (!form.appCode) {
    message.warning('请选择第三方应用')
    return
  }
  if (!selectedApiCodes.value.length) {
    message.warning('请选择要授权的接口')
    return
  }
  saving.value = true
  try {
    const selected = selectedApiCodes.value.length
    const res = await grantAuthz(form.appCode, selectedApiCodes.value)
    message.success(res?.created
      ? `已选 ${selected} 个接口，新增 ${res.created} 条授权（已授权的自动跳过）`
      : '所选接口均已授权，无新增')
    modalOpen.value = false
    await load()
  } catch (e: any) {
    message.error(e?.message || '授权失败')
  } finally {
    saving.value = false
  }
}

function doToggle(row: AuthzRow) {
  void (async () => {
    const next = row.status === 1 ? 2 : 1
    try {
      await toggleAuthz(row.appCode, row.apiCode, next)
      message.success(`${next === 1 ? '已启用' : '已停用'}该接口授权（第三方下次换票生效）`)
      await load()
    } catch (e: any) {
      message.error(e?.message || '操作失败')
    }
  })()
}

function doRevoke(row: AuthzRow) {
  void (async () => {
    try {
      const res = await revokeAuthz(row.appCode, row.apiCode)
      message.success(res?.deleted ? '已删除授权' : '该授权已不存在')
      await load()
    } catch (e: any) {
      message.error(e?.message || '删除失败')
    }
  })()
}

onMounted(() => {
  void load()
  void loadApps()
})
</script>

<style scoped>
.authz-page {
  --ft-min-width: 1400px;
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.api-picker {
  border: 1px solid var(--border-color, #303030);
  border-radius: 6px;
}
.authz-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: max(320px, calc(100vh - 320px));
  padding: 24px 0;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
.muted {
  color: var(--muted);
}
</style>

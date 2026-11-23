<template>
  <div class="ft-page app-page">
    <div class="ft-head app-head">
      <a-alert
        class="app-tip"
        type="info"
        show-icon
        message="这里维护允许接入的第三方应用（如用友、金蝶）。新增后把应用编码与密钥交给对方，再到「应用授权」为其配置可调用的接口；Polaris 内部系统不在此列。"
      />
      <a-button v-if="canCreate" type="primary" @click="openCreate">新增对接应用</a-button>
    </div>

    <section class="ft-scroll ft-bottom">
      <a-table
        class="ft-table"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        row-key="code"
        :expanded-row-keys="expandedKeys"
        @expanded-rows-change="onExpanded"
      >
        <template #expandIcon="{ expanded, onExpand, record }">
          <a-button type="link" size="small" @click="onExpand(record, $event)">
            {{ expanded ? '▾' : '▸' }}
          </a-button>
        </template>
        <template #expandedRowRender="{ record }">
          <EndpointList :app="record" />
        </template>
        <template #emptyText>
          <div class="app-empty">
            <a-empty :image-style="{ height: '48px' }" description="暂无对接应用，点击右上角「新增对接应用」创建" />
          </div>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'code'">
            <span class="mono">{{ record.code }}</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'green' : 'default'">
              {{ record.status === 1 ? '启用' : '停用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'remark'">
            <span>{{ record.remark || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button v-if="canEdit" type="link" size="small" @click="openEdit(record)">编辑</a-button>
            <a-button v-if="canToggle" type="link" size="small" @click="toggle(record)">
              {{ record.status === 1 ? '停用' : '启用' }}
            </a-button>
            <a-button v-if="canReset" type="link" size="small" @click="viewSecret(record)">查看密钥</a-button>
            <a-button v-if="canReset" type="link" size="small" @click="resetSecret(record)">重置密钥</a-button>
            <a-tooltip v-if="canDelete && hasAuth(record.code)" title="该应用已授权接口，请先到「应用授权」撤销全部授权后再删除">
              <span><a-button type="link" size="small" danger disabled>删除</a-button></span>
            </a-tooltip>
            <a-button v-else-if="canDelete" type="link" size="small" danger @click="remove(record)">删除</a-button>
          </template>
        </template>
      </a-table>
    </section>

    <!-- 新增 / 编辑 -->
    <a-drawer
      v-model:open="editOpen"
      :title="editing ? '编辑对接应用' : '新增对接应用'"
      width="640"
      :body-style="{ paddingBottom: '80px' }"
    >
      <a-form :model="form" layout="vertical" style="margin-top: 8px">
        <a-form-item label="应用编码" required>
          <a-input
            v-model:value="form.code"
            :disabled="editing"
            placeholder="如 yonyou / kingdee（2-64 位小写字母/数字/下划线/中划线）"
          />
          <div v-if="!editing" class="field-tip">创建后不可修改，是第三方换取令牌的 app_code</div>
        </a-form-item>
        <a-form-item label="应用名称" required>
          <a-input v-model:value="form.name" placeholder="如 用友 / 金蝶" />
        </a-form-item>
        <a-form-item label="访问地址">
          <a-input v-model:value="form.baseUrl" placeholder="https://partner.example.com（选填）" />
        </a-form-item>
        <a-form-item label="回调地址">
          <a-input v-model:value="form.redirectUri" placeholder="https://partner.example.com/callback（选填）" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="form.sort" :min="0" style="width: 120px" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="form.remark" :rows="2" placeholder="选填" />
        </a-form-item>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="editOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submitEdit">确定</a-button>
        </div>
      </template>
    </a-drawer>

    <!-- 密钥：新建/重置时仅显示一次；查看模式可随时调阅（明文已存库） -->
    <a-drawer
      v-model:open="secretOpen"
      :title="secretMode === 'view' ? '查看应用密钥' : '应用密钥（仅显示一次）'"
      width="480"
      :footer="null"
    >
      <a-alert
        v-if="secretMode === 'view'"
        type="info"
        show-icon
        message="该密钥已存库，可随时回来查看；如怀疑泄露请点「重置密钥」"
        style="margin-bottom: 12px"
      />
      <a-alert
        v-else
        type="warning"
        show-icon
        message="请立即复制并保存，关闭后将无法再次查看"
        style="margin-bottom: 12px"
      />
      <a-input-group compact>
        <a-input :value="newSecret" readonly class="mono" style="width: calc(100% - 76px)" />
        <a-button type="primary" style="width: 76px" @click="copySecret">复制</a-button>
      </a-input-group>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Modal, message } from 'ant-design-vue'
import {
  createApp,
  deleteApp,
  getAppSecret,
  listApps,
  listAuthz,
  resetAppSecret,
  toggleApp,
  updateApp,
  type AppInfo,
  type AppSavePayload
} from '@/api/openapi'
import { useUserStore } from '@/stores/user'
import EndpointList from '@/components/AppEndpointList.vue'

const store = useUserStore()
const canCreate = computed(() => store.can('openapi:app:create'))
const canEdit = computed(() => store.can('openapi:app:edit'))
const canDelete = computed(() => store.can('openapi:app:delete'))
const canToggle = computed(() => store.can('openapi:app:toggle'))
const canReset = computed(() => store.can('openapi:app:secret:reset'))

const columns = [
  { title: '应用编码', dataIndex: 'code', key: 'code', width: 180 },
  { title: '应用名称', dataIndex: 'name', key: 'name', width: 180 },
  { title: '状态', key: 'status', width: 90 },
  { title: '备注', dataIndex: 'remark', key: 'remark' },
  { title: '操作', key: 'action', width: 360 }
]

const rows = ref<AppInfo[]>([])
const loading = ref(false)
const expandedKeys = ref<string[]>([])
/** 仍有接口授权的应用：删除按钮置灰，需先撤销授权。 */
const authorizedAppCodes = ref<Set<string>>(new Set())
function hasAuth(code: string) {
  return authorizedAppCodes.value.has(code)
}

function onExpanded(keys: readonly (string | number)[]) {
  expandedKeys.value = keys.map(String)
}

const editOpen = ref(false)
const editing = ref(false)
const saving = ref(false)
const emptyForm = (): AppSavePayload => ({
  code: '', name: '', baseUrl: '', redirectUri: '', sort: 0, remark: ''
})
const form = reactive<AppSavePayload>(emptyForm())

function openCreate() {
  editing.value = false
  Object.assign(form, emptyForm())
  editOpen.value = true
}

function openEdit(record: AppInfo) {
  editing.value = true
  Object.assign(form, {
    code: record.code,
    name: record.name,
    baseUrl: record.baseUrl || '',
    redirectUri: record.redirectUri || '',
    sort: record.sort ?? 0,
    remark: record.remark || ''
  })
  editOpen.value = true
}

async function submitEdit() {
  if (!form.code || !form.code.trim()) {
    message.warning('请输入应用编码')
    return
  }
  if (!form.name || !form.name.trim()) {
    message.warning('请输入应用名称')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateApp({ ...form })
      message.success('已保存')
    } else {
      const created = await createApp({ ...form })
      message.success('对接应用已创建')
      showSecret(created.appSecret)
    }
    editOpen.value = false
    await load()
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function toggle(record: AppInfo) {
  const next = record.status === 1 ? 2 : 1
  Modal.confirm({
    title: `确认${next === 1 ? '启用' : '停用'}应用「${record.name}」？`,
    content: next === 2 ? '停用后该应用将无法换取令牌，已签发令牌到期后失效。' : undefined,
    centered: true,
    onOk: async () => {
      try {
        await toggleApp(record.code, next)
        message.success(next === 1 ? '已启用' : '已停用')
        await load()
      } catch (e: any) {
        message.error(e?.message || '操作失败')
      }
    }
  })
}

function resetSecret(record: AppInfo) {
  Modal.confirm({
    title: `确认重置「${record.name}」的密钥？`,
    content: '旧密钥将立即失效，请通知对方同步更新配置。',
    centered: true,
    onOk: async () => {
      try {
        const secret = await resetAppSecret(record.code)
        showSecret(secret)
        message.success('密钥已重置')
      } catch (e: any) {
        message.error(e?.message || '重置失败')
      }
    }
  })
}

function remove(record: AppInfo) {
  Modal.confirm({
    title: `确认删除对接应用「${record.name}」？`,
    content: '将同时清理该应用的接口授权与权限码，操作不可恢复。',
    okType: 'danger',
    centered: true,
    onOk: async () => {
      try {
        await deleteApp(record.code)
        message.success('已删除')
        await load()
      } catch (e: any) {
        message.error(e?.message || '删除失败')
      }
    }
  })
}

const secretOpen = ref(false)
const newSecret = ref('')
const secretMode = ref<'reset' | 'view'>('reset')
function showSecret(secret?: string, mode: 'reset' | 'view' = 'reset') {
  newSecret.value = secret || ''
  secretMode.value = mode
  secretOpen.value = !!secret
}
async function viewSecret(record: AppInfo) {
  try {
    const plain = await getAppSecret(record.code)
    if (!plain) {
      Modal.warning({
        title: '该应用未记录明文密钥',
        content: '历史密钥只存摘要无法反推，请点「重置密钥」生成新密钥，之后即可随时查看。',
        centered: true
      })
      return
    }
    showSecret(plain, 'view')
  } catch (e: any) {
    message.error(e?.message || '查看密钥失败')
  }
}
async function copySecret() {
  try {
    await navigator.clipboard.writeText(newSecret.value)
    message.success('已复制')
  } catch {
    message.warning('复制失败，请手动选择复制')
  }
}

async function load() {
  loading.value = true
  try {
    rows.value = await listApps()
    try {
      const authz = await listAuthz()
      authorizedAppCodes.value = new Set(authz.map((r) => r.appCode))
    } catch {
      // 授权列表加载失败不影响应用列表；后端删除接口仍会兜底拦截
      authorizedAppCodes.value = new Set()
    }
  } catch (e: any) {
    message.error(e?.message || '加载应用列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.app-page {
  --ft-min-width: 1000px;
}
/* 5 个操作按钮必须在同一行，窄屏靠表格横向滚动，不在单元格内换行 */
:deep(.ant-table-tbody > tr > td:last-child) {
  white-space: nowrap;
}
.app-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
}
.app-tip {
  flex: 1;
}
.app-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: max(320px, calc(100vh - 260px));
  padding: 24px 0;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
.field-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--muted);
}
</style>

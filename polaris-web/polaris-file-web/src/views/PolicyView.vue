<template>
  <div class="ft-page">
    <div class="toolbar ft-head">
      <span class="hint">按业务类型配置文件大小、扩展名与可见性；EHR 附件强制私有。</span>
      <a-space>
        <a-button v-if="store.can('file:policy:edit')" type="primary" @click="openCreate">
          <template #icon><plus-outlined /></template>新增业务类型
        </a-button>
        <a-button @click="load">刷新</a-button>
      </a-space>
    </div>
    <a-alert v-if="errorMsg" :message="errorMsg" type="warning" show-icon style="margin-bottom: 12px" />

    <section class="ft-scroll ft-bottom">
      <a-table :columns="columns" :data-source="records" :loading="loading" row-key="id" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'maxSizeMb'">
            {{ record.maxSizeMb }} MB
          </template>
          <template v-else-if="column.key === 'defaultVisibility'">
            <a-tag :color="record.defaultVisibility === 'PUBLIC' ? 'green' : 'blue'">
              {{ record.defaultVisibility === 'PUBLIC' ? '公开' : '私有' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'forcePrivate'">
            <a-tag v-if="record.forcePrivate" color="red">强制私有</a-tag>
            <span v-else class="muted">否</span>
          </template>
          <template v-else-if="column.key === 'quotaGb'">
            {{ record.quotaGb && record.quotaGb > 0 ? record.quotaGb + ' GB' : '不限' }}
          </template>
          <template v-else-if="column.key === 'ttlDays'">
            {{ record.ttlDays && record.ttlDays > 0 ? record.ttlDays + ' 天' : '永久' }}
          </template>
          <template v-else-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '停用' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a v-if="store.can('file:policy:edit')" @click="openEdit(record)">编辑</a>
            <span v-else class="muted">-</span>
          </template>
          <template v-else-if="column.key === 'remark'">
            <a-tooltip v-if="record.remark" :title="record.remark"><span class="cell-ellipsis muted">{{ record.remark }}</span></a-tooltip>
            <span v-else class="muted">-</span>
          </template>
        </template>
      </a-table>
    </section>

    <a-modal v-model:open="editOpen" :title="isCreate ? '新增业务类型' : '编辑存储策略'" :confirm-loading="saving" @ok="doSave" :width="520">
      <a-alert
        v-if="isCreate"
        type="info"
        show-icon
        style="margin-bottom: 14px"
        message="新增后即可在「文件管理」上传时选择该类型，无需改代码或重启服务。"
      />
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 17 }">
        <a-form-item label="业务类型" required>
          <a-input v-model:value="form.bizType" :disabled="!isCreate" placeholder="如 archive_file" />
        </a-form-item>
        <a-form-item label="业务名称">
          <a-input v-model:value="form.bizName" />
        </a-form-item>
        <a-form-item label="大小上限(MB)">
          <a-input-number v-model:value="form.maxSizeMb" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="允许扩展名">
          <a-input v-model:value="form.allowedExt" placeholder="逗号分隔，空为不限" />
        </a-form-item>
        <a-form-item label="默认可见性">
          <a-select v-model:value="form.defaultVisibility" :options="visibilityOptions" />
        </a-form-item>
        <a-form-item label="强制私有">
          <a-switch v-model:checked="form.forcePrivate" />
        </a-form-item>
        <a-form-item label="总配额(GB)">
          <a-input-number v-model:value="form.quotaGb" :min="0" placeholder="0 不限" style="width: 100%" />
        </a-form-item>
        <a-form-item label="生命周期(天)">
          <a-input-number v-model:value="form.ttlDays" :min="0" placeholder="0 永久" style="width: 100%" />
        </a-form-item>
        <a-form-item label="启用">
          <a-switch v-model:checked="form.enabled" />
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="form.remark" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { listPolicies, savePolicy } from '@/api/file'
import type { StoragePolicy } from '@/api/file'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const columns = [
  { title: '业务类型', dataIndex: 'bizType', key: 'bizType', width: 160 },
  { title: '业务名称', dataIndex: 'bizName', key: 'bizName', width: 160 },
  { title: '大小上限', key: 'maxSizeMb', width: 110 },
  { title: '允许扩展名', dataIndex: 'allowedExt', key: 'allowedExt', width: 220 },
  { title: '默认可见性', key: 'defaultVisibility', width: 110 },
  { title: '强制私有', key: 'forcePrivate', width: 100 },
  { title: '总配额', key: 'quotaGb', width: 90 },
  { title: '生命周期', key: 'ttlDays', width: 100 },
  { title: '状态', key: 'enabled', width: 90 },
  { title: '备注', key: 'remark', width: 180 },
  { title: '操作', key: 'action', width: 80 }
]
const visibilityOptions = [
  { label: '公开', value: 'PUBLIC' },
  { label: '私有', value: 'PRIVATE' }
]

const records = ref<StoragePolicy[]>([])
const loading = ref(false)
const errorMsg = ref('')
const editOpen = ref(false)
const isCreate = ref(false)
const saving = ref(false)
const form = reactive<StoragePolicy>({
  bizType: '', bizName: '', maxSizeMb: 20, allowedExt: '',
  defaultVisibility: 'PRIVATE', forcePrivate: false, quotaGb: 0, ttlDays: 0, enabled: true, remark: ''
})

onMounted(load)

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    records.value = await listPolicies()
  } catch (e: any) {
    errorMsg.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: undefined, bizType: '', bizName: '', maxSizeMb: 20, allowedExt: '',
    defaultVisibility: 'PRIVATE', forcePrivate: false, quotaGb: 0, ttlDays: 0, enabled: true, remark: ''
  })
  isCreate.value = true
  editOpen.value = true
}

function openEdit(record: StoragePolicy) {
  Object.assign(form, record)
  isCreate.value = false
  editOpen.value = true
}

async function doSave() {
  if (!form.bizType || !/^[a-z][a-z0-9_]{1,63}$/.test(form.bizType)) {
    message.warning('业务类型须以小写字母开头，仅含小写字母/数字/下划线')
    return
  }
  if (!form.bizName) {
    message.warning('请填写业务名称')
    return
  }
  saving.value = true
  try {
    await savePolicy({ ...form })
    message.success(isCreate.value ? '新增成功' : '保存成功')
    editOpen.value = false
    load()
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.hint { color: var(--muted); }
.muted { color: var(--muted); }
</style>

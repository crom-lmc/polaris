<template>
  <div class="ft-page">
    <div class="toolbar ft-head">
      <a-input-search v-model:value="keyword" placeholder="文件名" style="width: 220px" allow-clear @search="handleSearch" />
      <a-select v-model:value="bizType" style="width: 180px" :options="bizOptions" allowClear @change="handleSearch" />
      <a-select v-model:value="visibility" style="width: 140px" :options="visibilityOptions" allowClear @change="handleSearch" />
      <a-button type="primary" @click="openUpload">
        <template #icon><upload-outlined /></template>上传文件
      </a-button>
      <a-button @click="load">刷新</a-button>
    </div>

    <a-alert v-if="errorMsg" :message="errorMsg" type="warning" show-icon style="margin-bottom: 12px" />

    <section class="ft-scroll ft-bottom">
      <a-table :columns="columns" :data-source="records" :loading="loading" row-key="id" :pagination="pagination" @change="handleTableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'visibility'">
            <a-tag :color="record.visibility === 'PUBLIC' ? 'green' : 'blue'">
              {{ record.visibility === 'PUBLIC' ? '公开' : '私有' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'size'">
            {{ formatSize(record.size) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a v-if="store.can('file:download')" @click="openFile(record)">查看</a>
              <a v-if="store.can('file:download')" @click="downloadFile(record)">下载</a>
              <a v-if="store.can('file:delete')" class="danger" @click="confirmDelete(record)">删除</a>
            </a-space>
          </template>
        </template>
      </a-table>
    </section>

    <a-modal v-model:open="uploadOpen" title="上传文件" :confirm-loading="uploading" @ok="doUpload" :width="520">
      <a-form :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="业务类型" required>
          <a-select v-model:value="uploadForm.bizType" :options="bizOptions" placeholder="选择业务类型" />
        </a-form-item>
        <a-form-item label="可见性">
          <a-select v-model:value="uploadForm.visibility" :options="visibilityOptions" placeholder="默认取策略配置" allowClear />
        </a-form-item>
        <a-form-item label="归属类型">
          <a-input v-model:value="uploadForm.ownerType" placeholder="如 EMPLOYEE，可留空" />
        </a-form-item>
        <a-form-item label="归属 ID">
          <a-input v-model:value="uploadForm.ownerId" placeholder="可留空" />
        </a-form-item>
        <a-form-item label="选择文件" required>
          <input type="file" @change="onFileChange" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { UploadOutlined } from '@ant-design/icons-vue'
import { bizTypeOptions, deleteFile, listFiles, signFile, uploadFile } from '@/api/file'
import type { FileMeta } from '@/api/file'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const columns = [
  { title: '文件名', dataIndex: 'originalName', key: 'originalName', width: 220 },
  { title: '业务类型', dataIndex: 'bizType', key: 'bizType', width: 140 },
  { title: '可见性', key: 'visibility', width: 90 },
  { title: '大小', key: 'size', width: 100 },
  { title: '归属', key: 'owner', width: 160 },
  { title: '上传时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 140 }
]

const records = ref<FileMeta[]>([])
const loading = ref(false)
const errorMsg = ref('')
const keyword = ref('')
const bizType = ref<string | undefined>(undefined)
const visibility = ref<string | undefined>(undefined)
const bizOptions = ref<{ label: string; value: string }[]>([])
const visibilityOptions = [
  { label: '公开', value: 'PUBLIC' },
  { label: '私有', value: 'PRIVATE' }
]
const total = ref(0)
const current = ref(1)
const pageSize = ref(20)

const pagination = computed(() => ({
  current: current.value, pageSize: pageSize.value, total: total.value,
  showSizeChanger: true, showTotal: (t: number) => `共 ${t} 条`
}))

const uploadOpen = ref(false)
const uploading = ref(false)
const uploadForm = reactive({ bizType: '', visibility: '', ownerType: '', ownerId: '' })
let selectedFile: File | null = null

onMounted(async () => {
  try { bizOptions.value = await bizTypeOptions() } catch { /* ignore */ }
  load()
})

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await listFiles({
      page: current.value, size: pageSize.value,
      bizType: bizType.value, visibility: visibility.value, keyword: keyword.value || undefined
    })
    records.value = res.records || []
    total.value = res.total || 0
  } catch (e: any) {
    errorMsg.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function handleSearch() { current.value = 1; load() }
function handleTableChange(pag: any) { current.value = pag.current; pageSize.value = pag.pageSize; load() }

function openUpload() {
  uploadForm.bizType = bizOptions.value[0]?.value || ''
  uploadForm.visibility = ''
  uploadForm.ownerType = ''
  uploadForm.ownerId = ''
  selectedFile = null
  uploadOpen.value = true
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  selectedFile = input.files?.[0] || null
}

async function doUpload() {
  if (!uploadForm.bizType || !selectedFile) {
    message.warning('请选择业务类型和文件')
    return
  }
  uploading.value = true
  try {
    const base64 = await fileToBase64(selectedFile)
    await uploadFile({
      bizType: uploadForm.bizType,
      visibility: uploadForm.visibility || undefined,
      originalName: selectedFile.name,
      contentType: selectedFile.type || 'application/octet-stream',
      content: base64,
      ownerType: uploadForm.ownerType || undefined,
      ownerId: uploadForm.ownerId || undefined
    })
    message.success('上传成功')
    uploadOpen.value = false
    load()
  } catch (e: any) {
    message.error(e?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

async function openFile(record: FileMeta) {
  try {
    const res = await signFile(record.id, 'VIEW')
    window.open(res.url, '_blank')
  } catch (e: any) {
    message.error(e?.message || '获取访问链接失败')
  }
}

async function downloadFile(record: FileMeta) {
  try {
    const res = await signFile(record.id, 'DOWNLOAD')
    window.open(res.url, '_blank')
  } catch (e: any) {
    message.error(e?.message || '获取下载链接失败')
  }
}

function confirmDelete(record: FileMeta) {
  Modal.confirm({
    title: '删除文件',
    content: `确定删除「${record.originalName}」吗？删除为软删，字节文件延后清理。`,
    okText: '确定删除', cancelText: '取消', centered: true,
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await deleteFile(record.id)
        message.success('已删除')
        load()
      } catch (e: any) {
        message.error(e?.message || '删除失败')
      }
    }
  })
}

function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result).split(',')[1] || '')
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

function formatSize(size: number) {
  if (!size) return '0 B'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1024 / 1024).toFixed(1) + ' MB'
}
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.danger { color: #ff4d4f; }
</style>

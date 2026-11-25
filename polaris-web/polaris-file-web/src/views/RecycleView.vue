<template>
  <div class="ft-page">
    <div class="toolbar ft-head">
      <a-input-search v-model:value="keyword" placeholder="文件名" style="width: 220px" allow-clear @search="handleSearch" />
      <a-select v-model:value="bizType" style="width: 180px" :options="bizOptions" allowClear @change="handleSearch" />
      <a-button @click="load">刷新</a-button>
      <span class="tip">软删除文件可按存储策略保留期恢复；彻底删除不可恢复</span>
    </div>

    <a-alert v-if="errorMsg" :message="errorMsg" type="warning" show-icon style="margin-bottom: 12px" />

    <section class="ft-scroll ft-bottom">
      <a-table :columns="columns" :data-source="records" :loading="loading" row-key="id" :pagination="pagination" @change="handleTableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <span class="name-cell">
              <img v-if="record.thumbUrl" :src="record.thumbUrl" class="thumb" @error="onThumbError" />
              <span>{{ record.originalName }}</span>
            </span>
          </template>
          <template v-else-if="column.key === 'size'">
            {{ formatSize(record.size) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a v-if="store.can('file:delete')" @click="confirmRestore(record)">恢复</a>
              <a v-if="store.can('file:delete')" class="danger" @click="confirmPurge(record)">彻底删除</a>
            </a-space>
          </template>
        </template>
      </a-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { bizTypeOptions, listRecycle, purgeFile, restoreFile } from '@/api/file'
import type { FileMeta } from '@/api/file'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const columns = [
  { title: '文件名', key: 'name', width: 280 },
  { title: '业务类型', dataIndex: 'bizType', key: 'bizType', width: 160 },
  { title: '大小', key: 'size', width: 110 },
  { title: '删除时间', dataIndex: 'deletedAt', key: 'deletedAt', width: 190 },
  { title: '操作', key: 'action', width: 160 }
]

const records = ref<FileMeta[]>([])
const loading = ref(false)
const errorMsg = ref('')
const keyword = ref('')
const bizType = ref<string | undefined>(undefined)
const bizOptions = ref<{ label: string; value: string }[]>([])
const total = ref(0)
const current = ref(1)
const pageSize = ref(20)

const pagination = computed(() => ({
  current: current.value, pageSize: pageSize.value, total: total.value,
  showSizeChanger: true, showTotal: (t: number) => `共 ${t} 条`
}))

onMounted(async () => {
  try { bizOptions.value = await bizTypeOptions() } catch { /* ignore */ }
  load()
})

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await listRecycle({
      page: current.value, size: pageSize.value,
      bizType: bizType.value, keyword: keyword.value || undefined
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

function confirmRestore(record: FileMeta) {
  Modal.confirm({
    title: '恢复文件',
    content: `确定恢复「${record.originalName}」吗？`,
    okText: '恢复', cancelText: '取消', centered: true,
    onOk: async () => {
      try {
        await restoreFile(record.id)
        message.success('已恢复')
        load()
      } catch (e: any) {
        message.error(e?.message || '恢复失败')
      }
    }
  })
}

function confirmPurge(record: FileMeta) {
  Modal.confirm({
    title: '彻底删除',
    content: `彻底删除「${record.originalName}」？物理文件将被删除且不可恢复（若被其它文件秒传复用则仅删元数据）。`,
    okText: '彻底删除', cancelText: '取消', centered: true,
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await purgeFile(record.id)
        message.success('已彻底删除')
        load()
      } catch (e: any) {
        message.error(e?.message || '彻底删除失败')
      }
    }
  })
}

function onThumbError(e: Event) {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
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
.tip { font-size: 12px; color: var(--muted); }
.danger { color: #ff4d4f; }
.name-cell { display: inline-flex; align-items: center; gap: 6px; }
.thumb { width: 28px; height: 28px; object-fit: cover; border-radius: 4px; flex: none; }
</style>

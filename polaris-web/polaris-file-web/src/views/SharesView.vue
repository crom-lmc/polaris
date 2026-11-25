<template>
  <div class="ft-page">
    <div class="toolbar ft-head">
      <a-alert
        type="info"
        show-icon
        class="head-alert"
        message="这里集中展示你创建的分享链接。提取码只在创建时显示一次，此处仅标记是否设置；撤销后链接立即失效。"
      />
      <a-button @click="load">刷新</a-button>
    </div>

    <a-alert v-if="errorMsg" :message="errorMsg" type="warning" show-icon style="margin-bottom: 12px" />

    <section class="ft-scroll ft-bottom">
      <a-table
        :columns="columns"
        :data-source="records"
        :loading="loading"
        row-key="id"
        :pagination="false"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'fileName'">
            <span class="file-name">{{ record.fileName || '（文件已删除）' }}</span>
          </template>
          <template v-else-if="column.key === 'link'">
            <a-tooltip :title="record.url">
              <a class="link" @click="copyUrl(record.url)">{{ record.shareCode }}</a>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'password'">
            <a-tag :color="record.passwordHash ? 'orange' : 'green'">
              {{ record.passwordHash ? '有提取码' : '无提取码' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'expiresAt'">
            {{ record.expiresAt ? formatTime(record.expiresAt) : '长期有效' }}
          </template>
          <template v-else-if="column.key === 'views'">
            {{ record.viewCount || 0 }}{{ record.maxViews ? ` / ${record.maxViews}` : ' / 不限' }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="isExpired(record) ? 'default' : 'processing'">
              {{ isExpired(record) ? '已过期' : '生效中' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a @click="copyUrl(record.url)">复制链接</a>
              <a @click="openAccess(record)">打开</a>
              <a v-if="store.can('file:download')" class="danger" @click="confirmRevoke(record)">撤销</a>
            </a-space>
          </template>
        </template>
      </a-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { listShares, revokeShare } from '@/api/file'
import type { FileShare } from '@/api/file'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const columns = [
  { title: '文件', key: 'fileName', width: 200, ellipsis: true },
  { title: '分享码', key: 'link', width: 150 },
  { title: '提取码', key: 'password', width: 100 },
  { title: '有效期', key: 'expiresAt', width: 170 },
  { title: '查看次数', key: 'views', width: 110 },
  { title: '状态', key: 'status', width: 90 },
  { title: '创建时间', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 190 }
]

const records = ref<FileShare[]>([])
const loading = ref(false)
const errorMsg = ref('')

onMounted(load)

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    records.value = await listShares()
  } catch (e: any) {
    errorMsg.value = e?.message || '加载失败'
    records.value = []
  } finally {
    loading.value = false
  }
}

function isExpired(record: FileShare) {
  return !!record.expiresAt && new Date(record.expiresAt).getTime() < Date.now()
}

function formatTime(value: string) {
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function copyUrl(url: string) {
  try {
    await navigator.clipboard.writeText(url)
    message.success('链接已复制')
  } catch {
    message.warning('复制失败，请从分享码提示中手动复制')
  }
}

function openAccess(record: FileShare) {
  window.open(record.url, '_blank')
}

function confirmRevoke(record: FileShare) {
  Modal.confirm({
    title: '撤销分享',
    content: `撤销「${record.fileName || record.shareCode}」的分享链接？撤销后该链接立即失效，无法恢复。`,
    okText: '撤销', cancelText: '取消', centered: true,
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await revokeShare(record.id)
        message.success('已撤销')
        load()
      } catch (e: any) {
        message.error(e?.message || '撤销失败')
      }
    }
  })
}
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.head-alert { flex: 1 1 520px; }
.file-name { display: inline-block; max-width: 190px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: middle; }
.link { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
.danger { color: #ff4d4f; }
</style>

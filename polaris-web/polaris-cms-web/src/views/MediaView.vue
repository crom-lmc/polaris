<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { createFileShare, deleteMedia, pageMedia, uploadMedia, type MediaItem } from '@/api/cms'
import { confirmAction } from '@/utils/confirm'

const loading = ref(false)
const uploading = ref(false)
const list = ref<MediaItem[]>([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 24 })
const fileInput = ref<HTMLInputElement>()

const columns = [
  { title: '预览', key: 'preview', width: 90 },
  { title: '文件名', dataIndex: 'originalName' },
  { title: '类型', dataIndex: 'contentType', width: 130 },
  { title: '大小', key: 'size', width: 90 },
  { title: '上传人', dataIndex: 'uploadedName', width: 100 },
  { title: '操作', key: 'action', width: 150 }
]

onMounted(load)

async function load() {
  loading.value = true
  try {
    const data = await pageMedia(query)
    list.value = data.list
    total.value = data.total
  } catch (e: any) {
    message.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function pick() {
  fileInput.value?.click()
}

async function onPicked(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files
  if (!files?.length) return
  uploading.value = true
  try {
    for (const f of Array.from(files)) {
      await uploadMedia(f)
    }
    message.success('上传完成')
    await load()
  } catch (e: any) {
    message.error(e?.message || '上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

function fmtSize(n: number) {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(2)} MB`
}

async function copyUrl(m: MediaItem) {
  const url = window.location.origin + m.url
  try {
    await navigator.clipboard.writeText(url)
    message.success('已复制访问地址')
  } catch {
    message.warning('复制失败，请手动复制：' + m.url)
  }
}

const askDelete = (m: MediaItem) =>
  confirmAction({
    title: '删除文件',
    content: `删除「${m.originalName}」？引用它的文章图片会失效。`,
    okText: '删除',
    okType: 'danger',
    onOk: async () => {
      try {
        await deleteMedia(m.id)
        message.success('已删除')
        await load()
      } catch (e: any) {
        message.error(e?.message || '删除失败')
      }
    }
  })

const isImage = (m: MediaItem) => (m.contentType || m.originalName).toLowerCase().match(/\.(jpg|jpeg|png|gif|webp|bmp|ico)$/) !== null

// 公共能力：分享（不挂管理端权限码，登录即可）
const shareOpen = ref(false)
const shareCreating = ref(false)
const shareFileName = ref('')
const createdShareUrl = ref('')
/** 随机提取码：6 位大写字母+数字，剔除 0/O/1/I 等易混淆字符 */
function randomShareCode(len = 6) {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  const bytes = new Uint32Array(len)
  if (globalThis.crypto?.getRandomValues) {
    globalThis.crypto.getRandomValues(bytes)
  } else {
    for (let i = 0; i < len; i++) bytes[i] = Math.floor(Math.random() * 0xffffffff)
  }
  return Array.from(bytes, n => alphabet[n % alphabet.length]).join('')
}

const shareForm = reactive<{ password: string; expiresHours: number | null; maxViews: number | null }>({ password: '', expiresHours: null, maxViews: null })
let shareFileId = ''

function openShare(m: MediaItem) {
  if (!m.fileId) {
    message.warning('该媒体尚未纳入统一文件服务，无法分享')
    return
  }
  shareFileId = String(m.fileId)
  shareFileName.value = m.originalName
  shareForm.password = randomShareCode()
  shareForm.expiresHours = null
  shareForm.maxViews = null
  createdShareUrl.value = ''
  shareOpen.value = true
}

async function doCreateShare() {
  // 留空表示长期有效/不限次数；填了则必须是大于 0 的整数
  if (shareForm.expiresHours != null && (!Number.isInteger(shareForm.expiresHours) || shareForm.expiresHours <= 0)) {
    message.warning('有效期需为大于 0 的整数，留空表示长期有效')
    return
  }
  if (shareForm.maxViews != null && (!Number.isInteger(shareForm.maxViews) || shareForm.maxViews <= 0)) {
    message.warning('查看次数上限需为大于 0 的整数，留空表示不限次数')
    return
  }
  shareCreating.value = true
  try {
    const res = await createFileShare({
      fileId: shareFileId,
      password: shareForm.password || undefined,
      expiresHours: shareForm.expiresHours ?? undefined,
      maxViews: shareForm.maxViews ?? undefined
    })
    createdShareUrl.value = res.url
    message.success('分享链接已创建')
  } catch (e: any) {
    message.error(e?.message || '创建分享失败')
  } finally {
    shareCreating.value = false
  }
}

function selectShareUrl(e: MouseEvent) {
  (e.target as HTMLInputElement).select()
}

async function copyShareUrl() {
  const text = shareForm.password
    ? `${createdShareUrl.value}\n提取码：${shareForm.password}`
    : createdShareUrl.value
  try {
    await navigator.clipboard.writeText(text)
    message.success(shareForm.password ? '已复制链接和提取码' : '已复制')
  } catch {
    message.warning('复制失败，请手动选择链接')
  }
}
</script>

<template>
  <div class="ft-page media-page">
    <input ref="fileInput" type="file" multiple style="display: none" @change="onPicked" />
    <div class="toolbar ft-head">
      <a-input-search
        v-model:value="query.keyword"
        placeholder="文件名"
        style="width: 220px"
        allow-clear
        @search="() => { query.page = 1; load() }"
      />
      <a-button :loading="uploading" type="primary" @click="pick">上传文件</a-button>
      <a-button @click="load">刷新</a-button>
    </div>

    <section class="ft-scroll ft-bottom">
    <a-table class="ft-table" :columns="columns" :data-source="list" :loading="loading" row-key="id"
      :pagination="{ current: query.page, pageSize: query.size, total, showSizeChanger: true,
        showTotal: (t: number) => `共 ${t} 条`,
        onChange: (p: number, s: number) => { query.page = p; query.size = s; load() } }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'preview'">
          <a-image v-if="isImage(record)" :src="record.url" :width="56" :height="40" style="object-fit: cover" />
          <span v-else class="muted">文件</span>
        </template>
        <template v-else-if="column.key === 'size'">{{ fmtSize(record.size) }}</template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="copyUrl(record)">复制地址</a-button>
            <a-button v-if="record.fileId" type="link" size="small" @click="openShare(record)">分享</a-button>
            <a-button type="link" size="small" danger @click="askDelete(record)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>
    </section>

    <a-modal v-model:open="shareOpen" title="创建分享链接" :confirm-loading="shareCreating" @ok="doCreateShare" :width="520">
      <a-alert type="info" show-icon style="margin-bottom: 14px"
        :message="`为「${shareFileName}」创建对外分享链接。`" />
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 17 }">
        <a-form-item label="提取码">
          <a-input v-model:value="shareForm.password" placeholder="留空则无需提取码" allow-clear>
            <template #addonAfter>
              <a @click="shareForm.password = randomShareCode()">换一个</a>
            </template>
          </a-input>
        </a-form-item>
        <a-form-item label="有效期(小时)">
          <a-input-number v-model:value="shareForm.expiresHours" :min="1" :precision="0" placeholder="留空长期有效" style="width: 100%" />
        </a-form-item>
        <a-form-item label="查看次数上限">
          <a-input-number v-model:value="shareForm.maxViews" :min="1" :precision="0" placeholder="留空不限次数" style="width: 100%" />
        </a-form-item>
      </a-form>
      <div v-if="createdShareUrl" class="share-result">
        <p>链接已创建（请复制保存）：</p>
        <p v-if="shareForm.password">提取码：<b style="letter-spacing: 2px; font-size: 15px">{{ shareForm.password }}</b>（请一并发送给对方）</p>
        <a-input :value="createdShareUrl" readonly @click="selectShareUrl">
          <template #suffix>
            <a @click="copyShareUrl">复制</a>
          </template>
        </a-input>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
/* 表格最小宽度：横向滚动只发生在 .ft-scroll，操作列由 .ft-table 固定 */
.ft-page {
  --ft-min-width: 900px;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.muted {
  opacity: 0.6;
  font-size: 12px;
}

/* 空状态也要铺满滚动区，分页固定在右下角：
   表格链撑满 .ft-scroll，空占位行吃满剩余高度，分页由 ft-bottom 的 sticky 贴底。 */
.media-page :deep(.ant-table-wrapper),
.media-page :deep(.ant-spin-nested-loading),
.media-page :deep(.ant-spin-container) {
  height: 100%;
}
.media-page :deep(.ant-table) {
  min-height: 100%;
}
.media-page :deep(.ant-table-placeholder .ant-table-cell) {
  height: 320px;
}
.share-result {
  margin-top: 8px; padding: 12px; background: #f6ffed;
  border: 1px solid #b7eb8f; border-radius: 6px;
}
.share-result p { margin: 0 0 8px; color: #389e0d; }
</style>

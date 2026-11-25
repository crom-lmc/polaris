<template>
  <div class="ft-page">
    <div class="toolbar ft-head">
      <a-input-search v-model:value="keyword" placeholder="文件名" style="width: 200px" allow-clear @search="handleSearch" />
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
          <template v-if="column.key === 'bizType'">
            <a-tooltip :title="record.bizType">
              <span>{{ bizLabel(record.bizType) }}</span>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'visibility'">
            <a-tag :color="record.visibility === 'PUBLIC' ? 'green' : 'blue'">
              {{ record.visibility === 'PUBLIC' ? '公开' : '私有' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'name'">
            <a-tooltip v-if="record.originalName" :title="record.originalName">
              <span class="name-cell">
                <img v-if="record.thumbUrl" :src="record.thumbUrl" class="thumb" @error="onThumbError" />
                <span class="cell-ellipsis">{{ record.originalName }}</span>
                <a-tag v-if="record.deduplicated" color="cyan" class="dupe-tag">秒传</a-tag>
              </span>
            </a-tooltip>
            <span v-else class="name-cell">-</span>
          </template>
          <template v-else-if="column.key === 'size'">
            {{ formatSize(record.size) }}
          </template>
          <template v-else-if="column.key === 'time'">
            {{ record.createdAt }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a v-if="store.can('file:download')" @click="openPreview(record)">预览</a>
              <a v-if="store.can('file:download')" @click="downloadFile(record)">下载</a>
              <a v-if="store.can('file:download')" @click="openShare(record)">分享</a>
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

    <a-drawer v-model:open="previewOpen" :title="previewInfo?.name || '文件预览'" width="72%" :footer="null" @close="closePreview">
      <div v-if="previewLoading" class="preview-placeholder">加载中…</div>
      <div v-else-if="!previewInfo" class="preview-placeholder">无预览信息</div>
      <template v-else>
        <img v-if="previewInfo.mode === 'IMAGE'" :src="previewInfo.url" class="preview-img" />
        <div v-else-if="previewInfo.mode === 'PDF'" class="pdf-wrap">
          <iframe :src="previewInfo.url" class="preview-frame" />
          <div v-if="previewInfo.watermark" class="pdf-wm-layer" :style="{ gap: (previewInfo.watermark.gap || 46) + 'px' }">
            <span v-for="n in 40" :key="n" class="pdf-wm-item" :style="pdfWatermarkStyle">{{ previewInfo.watermark.text }}</span>
          </div>
        </div>
        <pre v-else-if="previewInfo.mode === 'TEXT'" class="preview-text">{{ previewText }}</pre>
        <div v-else class="preview-placeholder">
          <p>该类型不支持预览，请直接下载！</p>
          <a-button type="primary" @click="downloadFile({ id: previewId } as FileMeta)">下载文件</a-button>
        </div>
      </template>
    </a-drawer>

    <a-modal v-model:open="shareOpen" title="创建分享链接" :confirm-loading="shareCreating" @ok="doCreateShare" :width="520">
      <a-alert type="info" show-icon style="margin-bottom: 14px"
        :message="`为「${shareFileName}」创建对外分享链接，可设置提取码、有效期与查看次数。`" />
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
        <p>分享链接已创建，可在「分享管理」中查看和撤销：</p>
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

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { UploadOutlined } from '@ant-design/icons-vue'
import {
  bizTypeOptions, completeMultipart, createShare, deleteFile, initMultipart, listFiles,
  previewFile, readFileText, signFile, uploadFile, uploadPart
} from '@/api/file'
import type { FileMeta, PreviewInfo } from '@/api/file'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const columns = [
  { title: '文件名', key: 'name', width: 240 },
  { title: '业务类型', dataIndex: 'bizType', key: 'bizType', width: 140 },
  { title: '可见性', key: 'visibility', width: 90 },
  { title: '大小', key: 'size', width: 100 },
  { title: '归属', key: 'owner', width: 160 },
  { title: '上传时间', key: 'time', width: 180 },
  { title: '操作', key: 'action', width: 200 }
]

const records = ref<FileMeta[]>([])
const loading = ref(false)
const errorMsg = ref('')
const keyword = ref('')
const bizType = ref<string | undefined>(undefined)
const visibility = ref<string | undefined>(undefined)
const bizOptions = ref<{ label: string; value: string }[]>([])
/** 存储策略未登记的 bizType 兜底中文名（原始编码仍可在悬停查看） */
const BIZ_TYPE_FALLBACK: Record<string, string> = {
  announcement_attachment: '内部通知附件',
  cms_media: 'CMS 媒体资源',
  ehr_attachment: 'EHR 员工附件',
  forum_post: '论坛帖子附件',
  notice_attachment: '站内消息附件',
  openapi_export: '开放平台导出文件'
}
function bizLabel(type: string) {
  return bizOptions.value.find(o => o.value === type)?.label
    || BIZ_TYPE_FALLBACK[type]
    || type
}
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

// 预览抽屉
const previewOpen = ref(false)
const previewLoading = ref(false)
const previewInfo = ref<PreviewInfo | null>(null)
const pdfWatermarkStyle = computed(() => {
  const w = previewInfo.value?.watermark
  if (!w) return {}
  let h = (w.color || '#8B5CF6').replace('#', '')
  if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2]
  const n = parseInt(h, 16)
  const rgba = `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${(w.opacity || 30) / 100})`
  return {
    color: rgba,
    fontSize: `${(w.fontSize || 26) * 0.6}px`,
    transform: `rotate(${w.rotation || -24}deg)`
  }
})
const previewText = ref('')
const previewId = ref('')
const CHUNK_THRESHOLD = 5 * 1024 * 1024

// 分享弹窗
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
const shareFileId = ref('')

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
      bizType: bizType.value, keyword: keyword.value || undefined,
      visibility: visibility.value
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
    const file = selectedFile
    const contentType = file.type || 'application/octet-stream'
    let res: FileMeta
    if (file.size > CHUNK_THRESHOLD) {
      // 大文件：分片上传
      const session = await initMultipart({
        bizType: uploadForm.bizType,
        visibility: uploadForm.visibility || undefined,
        originalName: file.name,
        contentType,
        totalSize: file.size,
        ownerType: uploadForm.ownerType || undefined,
        ownerId: uploadForm.ownerId || undefined
      })
      const chunkSize = session.chunkSize
      for (let i = 0; i < session.totalChunks; i++) {
        const start = i * chunkSize
        const chunk = file.slice(start, Math.min(start + chunkSize, file.size))
        const base64 = await blobToBase64(chunk)
        await uploadPart(session.uploadId, i, base64)
      }
      res = await completeMultipart(session.uploadId)
    } else {
      const base64 = await fileToBase64(file)
      res = await uploadFile({
        bizType: uploadForm.bizType,
        visibility: uploadForm.visibility || undefined,
        originalName: file.name,
        contentType,
        content: base64,
        ownerType: uploadForm.ownerType || undefined,
        ownerId: uploadForm.ownerId || undefined
      })
    }
    message.success(res?.deduplicated ? '上传成功（秒传：内容已存在，复用存储）' : '上传成功')
    uploadOpen.value = false
    load()
  } catch (e: any) {
    message.error(e?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

async function openPreview(record: FileMeta) {
  previewId.value = record.id
  previewInfo.value = null
  previewText.value = ''
  previewLoading.value = true
  previewOpen.value = false
  try {
    const info = await previewFile(record.id)
    // 只有图片/PDF/可读取文本才打开预览；其余类型直接提示下载，避免空白抽屉
    if (info.mode !== 'IMAGE' && info.mode !== 'PDF' && info.mode !== 'TEXT') {
      message.warning('该类型不支持预览，请直接下载！')
      return
    }
    let text = ''
    if (info.mode === 'TEXT') {
      try {
        text = (await readFileText(info.url)) || ''
      } catch {
        text = ''
      }
      if (!String(text).trim()) {
        message.warning('该类型不支持预览，请直接下载！')
        return
      }
    }
    previewInfo.value = info
    previewText.value = String(text)
    previewOpen.value = true
  } catch (e: any) {
    message.error(e?.message || '获取预览信息失败')
  } finally {
    previewLoading.value = false
  }
}

function closePreview() {
  previewInfo.value = null
  previewText.value = ''
}

function openShare(record: FileMeta) {
  shareFileId.value = record.id
  shareFileName.value = record.originalName
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
    const res = await createShare({
      fileId: shareFileId.value,
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

function onThumbError(e: Event) {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
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
    content: `确定删除「${record.originalName}」吗？删除后可在左侧「回收站」恢复。`,
    okText: '确定删除', cancelText: '取消', centered: true,
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await deleteFile(record.id)
        message.success('已移入回收站')
        load()
      } catch (e: any) {
        message.error(e?.message || '删除失败')
      }
    }
  })
}

function fileToBase64(file: File): Promise<string> {
  return readAsBase64(file)
}

function blobToBase64(blob: Blob): Promise<string> {
  return readAsBase64(blob)
}

function readAsBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result).split(',')[1] || '')
    reader.onerror = reject
    reader.readAsDataURL(blob)
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
.name-cell { display: inline-flex; align-items: center; gap: 6px; max-width: 100%; min-width: 0; }
.name-cell .cell-ellipsis { flex: 1 1 auto; min-width: 0; }
.dupe-tag { flex: none; margin-left: 0; }
.thumb { width: 28px; height: 28px; object-fit: cover; border-radius: 4px; flex: none; }
.preview-placeholder { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 300px; color: #999; gap: 12px; }
.preview-img { max-width: 100%; max-height: 70vh; object-fit: contain; display: block; margin: 0 auto; }
.preview-frame { width: 100%; height: 70vh; border: none; }
.pdf-wrap { position: relative; }
.pdf-wm-layer { position: absolute; inset: 0; pointer-events: none; overflow: hidden; display: flex; flex-wrap: wrap; align-content: center; justify-content: center; padding: 20px; }
.pdf-wm-item { white-space: pre; font-weight: 700; user-select: none; line-height: 1.3; text-align: center; }
.preview-text { white-space: pre-wrap; word-break: break-all; font-size: 13px; line-height: 1.6; max-height: 70vh; overflow: auto; background: #fafafa; padding: 12px; border-radius: 6px; }
.share-result { margin-top: 8px; padding: 12px; background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 6px; }
.share-result p { margin: 0 0 8px; color: #389e0d; }
</style>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { shareDownload, shareInfo, type ShareAccessInfo } from '@/api/file'

const route = useRoute()
const code = String(route.query.code || '')

const loading = ref(true)
const info = ref<ShareAccessInfo | null>(null)
const error = ref('')
const password = ref('')
const downloading = ref(false)
const fileUrl = ref('')
const openedFileName = ref('')
const openedContentType = ref('')

let objectUrl = ''

function fail(e: any) {
  const c = e?.code
  if (c === 8118) error.value = '分享链接不存在或已失效'
  else if (c === 8120) error.value = '分享链接已过期'
  else if (c === 8121) error.value = '分享次数已用尽'
  else error.value = e?.message || '访问失败，请稍后重试'
}

onMounted(async () => {
  if (!code) {
    error.value = '缺少分享码'
    loading.value = false
    return
  }
  try {
    info.value = await shareInfo(code)
  } catch (e: any) {
    fail(e)
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  if (objectUrl) URL.revokeObjectURL(objectUrl)
})

function formatDate(v?: string) {
  if (!v) return '长期有效'
  const d = new Date(v)
  return Number.isNaN(d.getTime()) ? v : d.toLocaleString('zh-CN', { hour12: false })
}

function isPreviewable(ct: string) {
  return ct.startsWith('image/') || ct === 'application/pdf' || ct.startsWith('text/plain')
}

async function download() {
  if (downloading.value) return
  downloading.value = true
  error.value = ''
  try {
    const { blob, fileName, contentType } = await shareDownload(code, password.value || undefined)
    if (objectUrl) URL.revokeObjectURL(objectUrl)
    objectUrl = URL.createObjectURL(blob)
    fileUrl.value = objectUrl
    openedFileName.value = fileName
    openedContentType.value = contentType
    if (!isPreviewable(contentType)) {
      // 非可预览类型：直接触发下载
      const a = document.createElement('a')
      a.href = objectUrl
      a.download = fileName
      a.click()
      message.success('文件已开始下载')
    }
  } catch (e: any) {
    if (e?.code === 8119) message.error('提取码错误，请重新输入')
    else fail(e)
  } finally {
    downloading.value = false
  }
}

function forceDownload() {
  if (!fileUrl.value) return
  const a = document.createElement('a')
  a.href = fileUrl.value
  a.download = openedFileName.value || 'download'
  a.click()
}

function openNewWindow() {
  if (fileUrl.value) window.open(fileUrl.value, '_blank')
}
</script>

<template>
  <div class="share-page">
    <div class="share-card">
      <a-spin v-if="loading" size="large" tip="正在读取分享信息…" style="margin: 60px auto; display: block" />

      <template v-else-if="error">
        <div class="share-icon error-icon">!</div>
        <h2>无法访问该分享</h2>
        <a-alert type="error" show-icon :message="error" />
        <p class="hint">请联系分享者确认链接是否正确、是否过期或被撤销。</p>
      </template>

      <template v-else-if="info">
        <div class="share-icon">↓</div>
        <h2>文件分享</h2>
        <p class="file-name">{{ info.fileName }}</p>
        <a-descriptions :column="1" size="small" bordered class="meta">
          <a-descriptions-item label="有效期">{{ formatDate(info.expiresAt) }}</a-descriptions-item>
          <a-descriptions-item label="查看次数">
            {{ info.viewCount }}<template v-if="info.maxViews > 0"> / {{ info.maxViews }}</template>
          </a-descriptions-item>
        </a-descriptions>

        <template v-if="!fileUrl">
          <div v-if="info.needPassword" class="pwd-box">
            <a-input-password
              v-model:value="password"
              placeholder="请输入提取码"
              size="large"
              maxlength="32"
              @press-enter="download"
            />
          </div>
          <p v-else class="hint">该分享无需提取码，点击下方按钮查看文件。</p>
          <a-button type="primary" size="large" block :loading="downloading" @click="download">
            {{ info.needPassword ? '验证并查看文件' : '查看文件' }}
          </a-button>
        </template>

        <template v-else>
          <a-alert type="success" show-icon message="已通过验证" style="margin-bottom: 12px" />
          <div v-if="isPreviewable(openedContentType)" class="preview-box">
            <img v-if="openedContentType.startsWith('image/')" :src="fileUrl" :alt="openedFileName" />
            <iframe v-else :src="fileUrl" :title="openedFileName" />
          </div>
          <a-space style="width: 100%">
            <a-button type="primary" block @click="forceDownload">下载文件</a-button>
            <a-button block @click="openNewWindow">新窗口打开</a-button>
          </a-space>
        </template>
      </template>
    </div>
  </div>
</template>

<style scoped>
.share-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-page);
  padding: 24px;
}
.share-card {
  width: 100%;
  max-width: 560px;
  background: var(--color-background-primary);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 32px;
  text-align: center;
}
.share-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 14px;
  border-radius: 50%;
  background: #e6f4ff;
  color: #1677ff;
  font-size: 28px;
  line-height: 56px;
}
.error-icon { background: #fff1f0; color: #ff4d4f; }
.share-card h2 { margin: 0 0 8px; color: var(--text-base); font-size: 20px; }
.file-name { margin: 0 0 18px; font-weight: 600; color: var(--text-base); word-break: break-all; }
.meta { margin-bottom: 18px; text-align: left; }
.hint { color: var(--color-text-secondary, #8a9099); font-size: 13px; margin: 12px 0; }
.pwd-box { margin-bottom: 16px; }
.preview-box {
  margin-bottom: 16px;
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  overflow: hidden;
  background: var(--bg-page);
}
.preview-box img { max-width: 100%; max-height: 60vh; display: block; margin: auto; }
.preview-box iframe { width: 100%; height: 60vh; border: 0; display: block; }
</style>

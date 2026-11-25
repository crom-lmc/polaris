<template>
  <div class="attachment-panel">
    <div class="sub-toolbar">
      <span class="muted">档案附件直接上传/删除，无需审批；删除仅做逻辑删除并保留落盘文件</span>
      <a-upload
        v-if="canManage"
        :show-upload-list="false"
        :before-upload="beforeUpload"
        accept=".pdf,.jpg,.jpeg,.png,.doc,.docx"
      >
        <a-button size="small" type="primary">上传附件</a-button>
      </a-upload>
    </div>

    <a-table :columns="columns" :data-source="rows" :loading="loading" row-key="id" size="small" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <a @click="download(record)">{{ record.fileName }}</a>
        </template>
        <template v-else-if="column.key === 'size'">{{ formatSize(record.size) }}</template>
        <template v-else-if="column.key === 'createdAt'">{{ record.createdAt || '—' }}</template>
        <template v-else-if="column.key === 'action'">
          <a-space size="small">
            <a @click="download(record)">下载</a>
            <a v-if="record.fileId" @click="openShare(record)">分享</a>
            <a-popconfirm v-if="canManage" title="确定删除该附件？" ok-text="删除" cancel-text="取消" @confirm="remove(record)">
              <a class="danger">删除</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
      <template #emptyText>
        <a-empty description="暂无档案附件" />
      </template>
    </a-table>

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

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  createFileShare,
  deleteEhrAttachment,
  downloadEhrAttachment,
  listEhrAttachments,
  uploadEhrAttachment,
  type EhrAttachment
} from '@/api/ehr'

const props = defineProps<{
  employeeId?: string
  canManage?: boolean
}>()

/** 把条数抛给外层，供页签显示角标 */
const emit = defineEmits<{ (e: 'count', n: number): void }>()

const rows = ref<EhrAttachment[]>([])
const loading = ref(false)

const columns = [
  { title: '文件名', key: 'name' },
  { title: '大小', key: 'size', width: 100 },
  { title: '上传时间', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 120 }
]

async function load() {
  if (!props.employeeId) return
  loading.value = true
  try {
    rows.value = await listEhrAttachments('EMPLOYEE', props.employeeId)
    emit('count', rows.value.length)
  } catch (e: any) {
    message.error(e?.message || '加载附件失败')
  } finally {
    loading.value = false
  }
}

watch(() => props.employeeId, () => load(), { immediate: true })

async function beforeUpload(file: File) {
  if (!props.employeeId) return false
  try {
    await uploadEhrAttachment(file, 'EMPLOYEE', props.employeeId)
    message.success('附件已上传')
    await load()
  } catch (e: any) {
    message.error(e?.message || '附件上传失败')
  }
  return false
}

async function remove(record: EhrAttachment) {
  try {
    await deleteEhrAttachment(record.id)
    message.success('附件已删除')
    await load()
  } catch (e: any) {
    message.error(e?.message || '删除失败')
  }
}

async function download(record: EhrAttachment) {
  try {
    const res: any = await downloadEhrAttachment(record.id)
    const blob = new Blob([res.data], { type: record.mime || 'application/octet-stream' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = record.fileName
    a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    message.error(e?.message || '下载失败')
  }
}

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

function openShare(record: EhrAttachment) {
  if (!record.fileId) {
    message.warning('该附件尚未纳入统一文件服务，无法分享')
    return
  }
  shareFileId = record.fileId
  shareFileName.value = record.fileName
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

function formatSize(size: number) {
  if (!size) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

defineExpose({ reload: load })
</script>

<style scoped>
.attachment-panel {
  min-height: 120px;
}
.sub-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.muted {
  color: var(--color-text-tertiary, #8c8c8c);
  font-size: 12px;
}
.danger {
  color: var(--color-error, #ff4d4f);
}
.share-result {
  margin-top: 8px; padding: 12px; background: #f6ffed;
  border: 1px solid #b7eb8f; border-radius: 6px;
}
.share-result p { margin: 0 0 8px; color: #389e0d; }
</style>

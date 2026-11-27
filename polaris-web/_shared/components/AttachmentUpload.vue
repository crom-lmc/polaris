<script lang="ts">
/** 统一附件上传组件契约：组件不绑定任何应用 API，调用方注入上传函数与 file_id 列表。 */
export interface AttachmentUploadResult {
  fileId: string
  url: string
}
</script>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  modelValue?: string[]
  /** 由调用方注入：把 File 上传到所属业务的服务代理接口，返回 fileId 与预览 URL */
  upload: (file: File) => Promise<AttachmentUploadResult>
  label?: string
  hint?: string
  buttonText?: string
  accept?: string
  maxCount?: number
  maxSizeMb?: number
  required?: boolean
  /** 详情态只读展示；url 由调用方从后端签发的 attachmentUrls 传入 */
  readonly?: boolean
  initialUrls?: string[]
}>(), {
  modelValue: () => [],
  label: '附件',
  hint: '支持 jpg/png/pdf/ofd/doc/xls/zip，附件由统一文件服务托管。',
  buttonText: '上传附件',
  accept: '.jpg,.jpeg,.png,.pdf,.ofd,.doc,.docx,.xls,.xlsx,.zip',
  maxCount: 9,
  maxSizeMb: 20,
  required: false,
  readonly: false,
  initialUrls: () => []
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void
  /** 上传失败交给调用方提示，共享组件不依赖具体应用的 UI 库 */
  (e: 'error', message: string): void
}>()

interface Item { uid: string; fileId: string; name: string; url: string }
const items = ref<Item[]>([])
const fileList = ref<any[]>([])

function reset(ids: string[], urls: string[]) {
  items.value = ids.map((id, idx) => ({
    uid: `init-${id}-${idx}`,
    fileId: id,
    name: `附件 ${idx + 1}`,
    url: urls[idx] || ''
  }))
  fileList.value = items.value.map((i) => ({
    uid: i.uid, name: i.name, status: 'done', url: i.url || undefined
  }))
}

watch(() => (props.modelValue || []).join('|'), (key) => {
  const ids = props.modelValue || []
  if (key === items.value.map((i) => i.fileId).join('|')) return
  reset(ids, props.initialUrls || [])
}, { immediate: true })

async function onUpload(option: any) {
  const file = option.file as File
  try {
    if (file.size > props.maxSizeMb * 1024 * 1024) {
      throw new Error(`单个文件不能超过 ${props.maxSizeMb}MB`)
    }
    if (items.value.length >= props.maxCount) {
      throw new Error(`最多上传 ${props.maxCount} 个附件`)
    }
    const res = await props.upload(file)
    const uid = String((file as any).uid || `${Date.now()}-${Math.random()}`)
    items.value.push({ uid, fileId: res.fileId, url: res.url || '', name: file.name })
    fileList.value.push({ uid, name: file.name, status: 'done', url: res.url || undefined })
    emit('update:modelValue', items.value.map((i) => i.fileId))
    option.onSuccess?.(res as never)
  } catch (e: any) {
    option.onError?.(e)
    emit('error', e?.message || '附件上传失败')
  }
}

function onRemove(file: any) {
  const uid = String(file.uid)
  items.value = items.value.filter((i) => i.uid !== uid)
  fileList.value = fileList.value.filter((f) => String(f.uid) !== uid)
  emit('update:modelValue', items.value.map((i) => i.fileId))
  return true
}
</script>

<template>
  <a-form-item :label="label" :required="required">
    <a-upload v-if="!readonly" v-model:file-list="fileList" :custom-request="onUpload" :on-remove="onRemove"
              :accept="accept" :multiple="true">
      <a-button :disabled="items.length >= maxCount">{{ buttonText }}</a-button>
    </a-upload>
    <div v-else-if="items.length" class="attachment-links">
      <a v-for="item in items" :key="item.fileId" :href="item.url || undefined" target="_blank">{{ item.name }}</a>
    </div>
    <span v-else class="muted">无附件</span>
    <template v-if="!readonly" #extra>{{ hint }}（最多 {{ maxCount }} 个，单文件不超过 {{ maxSizeMb }}MB）</template>
  </a-form-item>
</template>

<style scoped>
.attachment-links { display: flex; flex-wrap: wrap; gap: 12px; }
</style>

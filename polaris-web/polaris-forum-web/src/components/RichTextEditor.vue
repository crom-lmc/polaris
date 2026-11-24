<template>
  <div class="rich-text-editor">
    <div class="editor-toolbar rich-text-toolbar">
      <Toolbar class="rich-text-toolbar-buttons" :editor="editorRef" :default-config="toolbarConfig" mode="default" />
      <button type="button" class="rich-text-clear" :disabled="disabled" @click="clear">清空</button>
    </div>
    <Editor
      v-model="editorHtml"
      class="rich-text-editor-content"
      :default-config="editorConfig"
      mode="default"
      @onCreated="handleCreated"
      @onChange="handleChange"
    />
    <p v-if="uploadError" class="form-message error" role="alert">{{ uploadError }}</p>
  </div>
</template>

<script setup lang="ts">
import '@wangeditor/editor/dist/css/style.css'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import type { IDomEditor, IEditorConfig, IToolbarConfig } from '@wangeditor/editor'
import { onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { uploadAttachment } from '@/api/forum'
import { sanitizeRichText, toEditorHtml } from '@/utils/html'

const props = withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
  disabled?: boolean
  /** 评论/回复场景：精简工具栏，仅保留常用格式与图片上传 */
  compact?: boolean
}>(), { placeholder: '', disabled: false, compact: false })
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const editorRef = shallowRef<IDomEditor>()
const editorHtml = ref(toEditorHtml(props.modelValue))
const uploadError = ref('')
const toolbarConfig: Partial<IToolbarConfig> = {
  toolbarKeys: props.compact
    ? ['bold', 'italic', 'underline', 'through', 'uploadImage', 'undo', 'redo']
    : ['header2', 'header3', 'bold', 'italic', 'underline', 'through', 'bulletedList', 'numberedList', 'blockquote', 'uploadImage', 'undo', 'redo', 'clearStyle']
}
const editorConfig: Partial<IEditorConfig> = {
  placeholder: props.placeholder,
  readOnly: props.disabled,
  // 正文插图走统一文件服务：后端返回稳定别名路径，编辑期与发布后都能匿名加载
  MENU_CONF: {
    uploadImage: {
      async customUpload(file: File, insertFn: (url: string, alt?: string, href?: string) => void) {
        uploadError.value = ''
        try {
          const uploaded = await uploadAttachment(file, 'INLINE')
          insertFn(uploaded.url, uploaded.originalName, '')
        } catch (cause) {
          uploadError.value = cause instanceof Error ? cause.message : '图片上传失败'
        }
      }
    }
  }
}

watch(() => props.modelValue, value => {
  if (value !== editorHtml.value) editorHtml.value = toEditorHtml(value)
})
watch(() => props.disabled, disabled => {
  const editor = editorRef.value
  if (!editor) return
  if (disabled) editor.disable()
  else editor.enable()
})

function handleCreated(editor: IDomEditor) {
  editorRef.value = editor
  if (props.disabled) editor.disable()
}

function handleChange(editor: IDomEditor) {
  emit('update:modelValue', sanitizeRichText(editor.getHtml()))
}

function clear() {
  editorHtml.value = ''
  editorRef.value?.setHtml('')
  emit('update:modelValue', '')
}

onBeforeUnmount(() => {
  editorRef.value?.destroy()
  editorRef.value = undefined
})
</script>

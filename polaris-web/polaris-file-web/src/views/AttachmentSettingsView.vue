<template>
  <div class="ft-page attachment-settings-page">
    <div class="toolbar ft-head">
      <span class="hint">按业务类型配置附件水印，以及预览/缩略图/秒传等附件行为。改动保存后即时生效。</span>
      <a-space>
        <a-button v-if="canEdit" @click="loadAll">刷新</a-button>
        <a-button v-if="canEdit" type="primary" :loading="saving" @click="doSave">
          <template #icon><save-outlined /></template>保存设置
        </a-button>
      </a-space>
    </div>
    <a-alert v-if="errorMsg" :message="errorMsg" type="warning" show-icon style="margin-bottom:12px" />

    <section class="ft-scroll settings-scroll">
      <!-- 水印设置 -->
      <a-card class="panel">
        <template #title>
          <span class="card-title">水印设置</span>
          <a-tooltip trigger="hover" placement="top">
            <template #title>
              <div class="hint-tip">
                <p>按业务类型（bizType）为附件叠加水印，防止截图外传与泄露追溯。</p>
                <p>图片与 SVG 服务端实时合成；PDF 用 PDFBox 逐页烧录；Word/Excel/PPT 服务端写入水印。均不修改原文件，失败自动回退。</p>
                <p>水印文本支持变量：{username} 预览人、{date} 日期、{userId} 工号、{ip} IP、{filename} 文件名、{app} 应用名，运行时替换为真实值。</p>
              </div>
            </template>
            <info-circle-outlined class="title-info" />
          </a-tooltip>
        </template>

        <div class="wm-layout">
          <!-- 左：水印配置 -->
          <div class="wm-form">
            <a-form :label-col="{ span: 7 }" :wrapper-col="{ span: 16 }" size="small">
              <a-form-item>
                <template #label>业务类型&nbsp;<FieldHint text="复用存储策略里的 bizType，每个业务一条配置；已配置过的业务不再重复出现。编辑时固定为当前业务。" /></template>
                <a-select v-model:value="form.bizType" :options="availableBizOptions" placeholder="请选择业务类型"
                          :disabled="editing" style="width:100%" show-search option-filter-prop="label" />
              </a-form-item>
              <a-form-item>
                <template #label>启用水印&nbsp;<FieldHint text="关闭后该业务所有附件都不叠加水印，配置保留。" /></template>
                <a-switch v-model:checked="form.enabled" />
              </a-form-item>
              <a-form-item>
                <template #label>水印内容&nbsp;<FieldHint :text="'支持静态文本与变量占位符：{username} 预览人、{date} 日期、{userId} 工号、{ip} IP、{filename} 文件名、{app} 应用名。用换行可显示多行。'" /></template>
                <a-textarea v-model:value="form.content" :rows="2" placeholder="例如：仅供内部使用" />
                <div class="var-chips">
                  <a-tag v-for="v in variables" :key="v.key" class="var-chip" @click="insertVar(v.token)">{{ v.label }}</a-tag>
                </div>
              </a-form-item>
              <a-form-item>
                <template #label>字号(px)&nbsp;<FieldHint text="水印文字字号，范围 12–72px。过大可能影响正文阅读。" /></template>
                <a-slider v-model:value="form.fontSize" :min="12" :max="72" :step="1" />
              </a-form-item>
              <a-form-item>
                <template #label>颜色&nbsp;<FieldHint text="水印文字颜色，建议与文档形成适度对比。" /></template>
                <a-input v-model:value="form.color" placeholder="#8B5CF6" />
              </a-form-item>
              <a-form-item>
                <template #label>透明度(%)&nbsp;<FieldHint text="越小越淡。建议 20–40%，既能看清又不遮挡正文。" /></template>
                <a-slider v-model:value="form.opacity" :min="5" :max="100" :step="1" />
              </a-form-item>
              <a-form-item>
                <template #label>旋转角度&nbsp;<FieldHint text="文字倾斜角度，负值逆时针。常用 -24°。" /></template>
                <a-slider v-model:value="form.rotation" :min="-90" :max="0" :step="1" />
              </a-form-item>
              <a-form-item>
                <template #label>排布方式&nbsp;<FieldHint text="整屏平铺：全页重复铺满；居中大水印：仅正中一条大字。" /></template>
                <a-segmented v-model:value="form.layout" :options="layoutOptions" />
              </a-form-item>
              <a-form-item v-if="form.layout === 'TILE'">
                <template #label>平铺间距(px)&nbsp;<FieldHint text="平铺水印之间的间距，越小越密集。" /></template>
                <a-slider v-model:value="form.gap" :min="18" :max="120" :step="1" />
              </a-form-item>
              <a-form-item>
                <template #label>作用文件类型&nbsp;<FieldHint text="图片/SVG 服务端合成，PDF/Word/Excel/PPT 服务端烧录；Excel 水印在打印页眉生效；老版 .doc/.xls/.ppt 不支持。" /></template>
                <a-checkbox-group v-model:value="fileTypes" :options="fileTypeOptions" />
              </a-form-item>
              <a-form-item>
                <template #label>生效环节&nbsp;<FieldHint text="在线预览、分享访问页、下载。下载水印需服务端烧录，默认只开预览/分享。" /></template>
                <a-checkbox-group v-model:value="scenes" :options="sceneOptions" />
              </a-form-item>
            </a-form>

            <div class="wm-form-actions">
              <a-button type="primary" :loading="saving" @click="saveWatermarkConfig">{{ editing ? '保存修改' : '添加水印配置' }}</a-button>
              <a-button v-if="editing" @click="resetForm">取消编辑</a-button>
            </div>
          </div>

          <!-- 右：实时预览 -->
          <div class="wm-preview">
            <div class="preview-title">
              <span>实时预览</span>
              <a-tooltip trigger="hover" placement="top">
                <template #title>
                  <div class="hint-tip">
                    <p>按当前参数模拟文档预览；变量已替换为示例值。</p>
                    <p>图片与 SVG 由服务端合成，PDF/Word/Excel/PPT 由服务端烧录，效果与此一致。</p>
                  </div>
                </template>
                <info-circle-outlined class="title-info" />
              </a-tooltip>
            </div>
            <div class="preview-stage">
              <div class="doc">
                <div class="doc-head"><i style="background:#ff5f57" /><i style="background:#febc2e" /><i style="background:#28c840" /><span class="name">劳动合同_张伟丽.pdf · 预览</span></div>
                <div class="doc-body">
                  <div class="title">劳 动 合 同</div>
                  <div class="ln m" /><div class="ln l" /><div class="ln s" />
                  <div class="ln l" /><div class="ln m" /><div class="ln l" /><div class="ln s" />
                  <div class="ln m" /><div class="ln l" />
                  <div class="sig"><span>甲方（盖章）：__________</span><span>乙方：张伟丽</span></div>
                </div>
                <div class="wm-layer" :class="{ center: form.layout === 'CENTER' }" :style="wmLayerStyle">
                  <template v-if="form.enabled">
                    <span v-for="n in tileCount" :key="n" class="wm-item" :style="wmItemStyle">{{ previewText }}</span>
                  </template>
                </div>
              </div>
            </div>
            <div class="preview-note">预览按当前参数渲染，变量为示例值。实际水印由服务端/预览层注入登录人真实信息。</div>
          </div>
        </div>

        <!-- 已配置列表 -->
        <div class="wm-list">
          <a-divider orientation="left" style="margin:18px 0 12px">已配置水印（{{ watermarks.length }}）</a-divider>
          <a-table :columns="wmColumns" :data-source="watermarks" row-key="id" :pagination="false" size="small">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'bizType'">
                <a-tooltip :title="record.bizType">
                  <span>{{ bizLabel(record.bizType) }}</span>
                </a-tooltip>
              </template>
              <template v-else-if="column.key === 'fileTypes'">
                <span>{{ fileTypesLabel(record.fileTypes) }}</span>
              </template>
              <template v-else-if="column.key === 'scenes'">
                <span>{{ scenesLabel(record.scenes) }}</span>
              </template>
              <template v-else-if="column.key === 'enabled'">
                <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '停用' }}</a-tag>
              </template>
              <template v-else-if="column.key === 'layout'">
                {{ record.layout === 'CENTER' ? '居中大字' : '整屏平铺' }}
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space v-if="canEdit">
                  <a @click="editWatermark(record)">编辑</a>
                  <a-popconfirm title="删除该水印配置？" @confirm="removeWatermark(record)">
                    <a class="danger">删除</a>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </a-table>
        </div>
      </a-card>

      <!-- 其他附件设置 -->
      <a-card class="panel" style="margin-top:16px">
        <template #title><span class="card-title">其他附件设置</span></template>
        <div class="setting-grid">
          <div class="setting-tile" v-for="s in settingDefs" :key="s.key">
            <div class="st-head">
              <span class="st-title">{{ s.title }}</span>
              <a-tooltip trigger="hover" placement="top">
                <template #title><div class="hint-tip"><p v-for="(p,i) in s.tips" :key="i">{{ p }}</p></div></template>
                <info-circle-outlined class="title-info" />
              </a-tooltip>
            </div>
            <div class="st-desc">{{ s.desc }}</div>
            <div class="st-ctrl">
              <a-switch v-if="s.type === 'bool'" v-model:checked="settingValues[s.key]" :disabled="!canEdit" />
              <a-input-number v-else v-model:value="settingValues[s.key]" :min="s.min" :max="s.max" :step="1" :disabled="!canEdit" style="width:100%" />
              <span v-if="s.unit" class="st-unit">{{ s.unit }}</span>
            </div>
          </div>
        </div>
      </a-card>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { message, Tooltip } from 'ant-design-vue'
import { InfoCircleOutlined, SaveOutlined } from '@ant-design/icons-vue'
import {
  listWatermarks, saveWatermark, deleteWatermark,
  getAttachmentSettings, saveAttachmentSettings, bizTypeOptions,
  type WatermarkConfig, type AttachmentSettings
} from '@/api/file'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const canEdit = computed(() => store.can('file:watermark:edit'))

// ---- 字段 hint 小组件（渲染为标题旁的 ⓘ，hover 出气泡）----
const FieldHint = (props: { text: string }) =>
  h(Tooltip, { trigger: 'hover', placement: 'top' }, {
    title: () => h('div', { class: 'hint-tip' }, [h('p', props.text)]),
    default: () => h(InfoCircleOutlined, { class: 'title-info' })
  })
FieldHint.props = ['text']

const variables = [
  { key: 'date', label: '{date} 日期', token: '{date}' },
  { key: 'username', label: '{username} 预览人', token: '{username}' },
  { key: 'userId', label: '{userId} 工号', token: '{userId}' },
  { key: 'ip', label: '{ip} IP', token: '{ip}' },
  { key: 'filename', label: '{filename} 文件名', token: '{filename}' },
  { key: 'app', label: '{app} 应用名', token: '{app}' }
]
const layoutOptions = [
  { label: '整屏平铺', value: 'TILE' },
  { label: '居中大字', value: 'CENTER' }
]
const fileTypeOptions = [
  { label: '图片', value: 'IMAGE' },
  { label: 'SVG', value: 'SVG' },
  { label: 'PDF', value: 'PDF' },
  { label: 'Word（docx）', value: 'DOCX' },
  { label: 'Excel（xlsx）', value: 'XLSX' },
  { label: 'PPT（pptx）', value: 'PPTX' }
]
const sceneOptions = [
  { label: '在线预览', value: 'VIEW' },
  { label: '分享访问', value: 'SHARE' },
  { label: '下载', value: 'DOWNLOAD' }
]

/** 列表里把 CSV 编码翻译成中文；未知编码原样保留 */
function csvLabel(csv: string | undefined, options: { label: string; value: string }[]) {
  if (!csv) return '—'
  return csv.split(',').filter(Boolean)
    .map(code => options.find(o => o.value === code)?.label || code)
    .join('、')
}
function fileTypesLabel(csv: string | undefined) { return csvLabel(csv, fileTypeOptions) }
function scenesLabel(csv: string | undefined) { return csvLabel(csv, sceneOptions) }

const wmColumns = [
  { title: '业务类型', dataIndex: 'bizType', key: 'bizType', width: 180 },
  { title: '水印内容', dataIndex: 'content', key: 'content', ellipsis: true },
  { title: '排布', key: 'layout', width: 100 },
  { title: '文件类型', dataIndex: 'fileTypes', key: 'fileTypes', width: 210 },
  { title: '生效环节', dataIndex: 'scenes', key: 'scenes', width: 230 },
  { title: '状态', key: 'enabled', width: 90 },
  { title: '操作', key: 'action', width: 120 }
]

const bizOptions = ref<Array<{ label: string; value: string }>>([])
/** 存储策略未登记的 bizType 兜底中文名（原始编码仍可在列表悬停查看） */
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
const watermarks = ref<WatermarkConfig[]>([])
const errorMsg = ref('')
const saving = ref(false)
const editing = ref(false)

const defaultForm = (): WatermarkConfig => ({
  bizType: '', enabled: true, content: '仅供内部使用\n{username} · {date}',
  fontSize: 26, color: '#8B5CF6', opacity: 30, rotation: -24,
  layout: 'TILE', gap: 46, fileTypes: 'IMAGE,PDF', scenes: 'VIEW,SHARE'
})
const form = reactive<WatermarkConfig>(defaultForm())
const fileTypes = ref<string[]>(['IMAGE', 'PDF'])
const scenes = ref<string[]>(['VIEW', 'SHARE'])

// 下拉选项排除已配置业务（编辑时保留当前业务，避免无法回显）
const availableBizOptions = computed(() => {
  const configured = new Set(watermarks.value.map(w => w.bizType))
  if (editing.value && form.bizType) configured.delete(form.bizType)
  return bizOptions.value.filter(o => !configured.has(o.value))
})

// ---- 实时预览 ----
const previewVar: Record<string, string> = {
  '{date}': new Date().toISOString().slice(0, 10), '{username}': '张伟丽', '{userId}': 'E10086',
  '{ip}': '10.20.3.41', '{filename}': '劳动合同_张伟丽.pdf', '{app}': '北极星云'
}
const previewText = computed(() => {
  let t = (form.content || '').replace(/\\n/g, '\n')
  Object.entries(previewVar).forEach(([k, v]) => { t = t.split(k).join(v) })
  return t
})
const wmLayerStyle = computed(() => ({ '--wmgap': (form.layout === 'TILE' ? form.gap : 0) + 'px' } as any))
const wmItemStyle = computed(() => {
  const base: Record<string, string> = {
    color: hexToRgba(form.color, (form.opacity || 30) / 100),
    fontSize: (form.layout === 'CENTER' ? (form.fontSize || 26) * 1.8 : (form.fontSize || 26)) + 'px',
    fontWeight: '700'
  }
  if (form.layout !== 'CENTER') base.transform = `rotate(${form.rotation || 0}deg)`
  else base.transform = `rotate(${form.rotation || 0}deg)`
  return base
})
const tileCount = computed(() => form.layout === 'CENTER' ? 1 : 30)

function hexToRgba(hex: string, a: number) {
  let h = (hex || '#8B5CF6').replace('#', '')
  if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2]
  const n = parseInt(h, 16)
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`
}
function insertVar(token: string) {
  form.content = (form.content || '') + token
}

// ---- 其他附件设置 ----
interface SettingDef {
  key: string; title: string; desc: string; type: 'bool' | 'number'; unit?: string; min?: number; max?: number; tips: string[]
}
const settingDefs: SettingDef[] = [
  {
    key: 'preview.enabled', title: '在线预览', desc: '是否允许在线预览附件', type: 'bool',
    tips: ['关闭后所有附件的在线预览入口都不可用，仅保留下载。', '适合对内容外泄敏感、只允许下载本地查看的场景。']
  },
  {
    key: 'preview.maxSizeMb', title: '预览大小上限', desc: '超过该大小的附件不提供在线预览', type: 'number', unit: 'MB', min: 1, max: 2000,
    tips: ['大文件在线预览会占用较多内存与带宽。', '超过上限的文件在预览时提示“超过在线预览大小上限”，仍可下载。']
  },
  {
    key: 'thumbnail.maxEdge', title: '缩略图最大边', desc: '列表缩略图的最长边像素', type: 'number', unit: 'px', min: 32, max: 1200,
    tips: ['图片附件按该值等比生成缩略图，不影响原图。', '调小可节省带宽与存储，调大列表更清晰。']
  },
  {
    key: 'dedup.enabled', title: '秒传去重', desc: '相同内容复用已存在文件', type: 'bool',
    tips: ['按 SHA256 摘要 + 大小判重，命中时复用物理字节、只新增元数据。', '关闭后每次上传都独立落盘，适合需要每份文件独立留存审计的场景。']
  },
  {
    key: 'sign.ttlSeconds', title: '私有签名有效期', desc: '私有文件访问签名有效时长', type: 'number', unit: '秒', min: 60, max: 86400,
    tips: ['私有文件通过短时效签名 URL 访问，过期须重新签发。', '调短更安全，调长便于长时间打开的页面不失效。上限 86400 秒。']
  },
  {
    key: 'audit.download', title: '下载审计', desc: '下载动作留痕（业务侧声明）', type: 'bool',
    tips: ['网关层已对全部请求做统一审计兜底。', '此开关作为业务侧下载留痕的声明，供后续细粒度审计接入使用。']
  },
  {
    key: 'recycle.keepDays', title: '回收站保留天数', desc: '软删文件保留多久后物理清理', type: 'number', unit: '天', min: 0, max: 3650,
    tips: ['回收站中的文件超过该天数后由每日定时任务物理删除。', '设为 0 表示永久保留，不自动清理。秒传共享字节在被引用时不会被物理删除。']
  }
]
const settingValues = reactive<Record<string, any>>({})

async function loadAll() {
  errorMsg.value = ''
  try {
    const [wms, settings, biz] = await Promise.all([
      listWatermarks(), getAttachmentSettings(), bizTypeOptions()
    ])
    watermarks.value = wms
    bizOptions.value = biz
    settingDefs.forEach(s => {
      const raw = settings[s.key]
      settingValues[s.key] = s.type === 'bool'
        ? (raw === undefined ? true : raw === 'true')
        : (raw === undefined ? (s.key === 'recycle.keepDays' ? 30 : s.key === 'sign.ttlSeconds' ? 300 : 20) : Number(raw))
    })
  } catch (e: any) {
    errorMsg.value = e?.message || '加载失败'
  }
}

function resetForm() {
  Object.assign(form, defaultForm())
  fileTypes.value = ['IMAGE', 'PDF']
  scenes.value = ['VIEW', 'SHARE']
  editing.value = false
}

function editWatermark(record: WatermarkConfig) {
  Object.assign(form, record)
  fileTypes.value = (record.fileTypes || '').split(',').filter(Boolean)
  scenes.value = (record.scenes || '').split(',').filter(Boolean)
  editing.value = true
  // 滚动回内容区顶部（滚动容器是布局层的 .content，不是本页 .settings-scroll）
  document.querySelector('.content')?.scrollTo({ top: 0, behavior: 'smooth' })
}

async function saveWatermarkConfig() {
  if (!form.bizType) { message.warning('请选择业务类型'); return }
  if (!form.content || !form.content.trim()) { message.warning('请填写水印内容'); return }
  if (!fileTypes.value.length) { message.warning('请至少选择一种作用文件类型'); return }
  saving.value = true
  try {
    await saveWatermark({
      ...form,
      fileTypes: fileTypes.value.join(','),
      scenes: scenes.value.join(',')
    })
    message.success('水印配置已保存')
    resetForm()
    await loadAll()
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeWatermark(record: WatermarkConfig) {
  if (!record.id) return
  try {
    await deleteWatermark(record.id)
    message.success('已删除')
    await loadAll()
  } catch (e: any) {
    message.error(e?.message || '删除失败')
  }
}

async function doSave() {
  saving.value = true
  try {
    const payload: AttachmentSettings = {}
    settingDefs.forEach(s => {
      payload[s.key] = settingValues[s.key] === undefined || settingValues[s.key] === null
        ? '' : String(settingValues[s.key])
    })
    await saveAttachmentSettings(payload)
    message.success('附件设置已保存')
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.hint { color: var(--muted); }
.card-title { font-weight: 600; }
.title-info { margin-left: 6px; color: var(--muted); font-size: 13px; cursor: help; }
.hint-tip { max-width: 320px; line-height: 1.7; font-size: 12px; }
.hint-tip p { margin: 0 0 6px; }
.hint-tip p:last-child { margin-bottom: 0; }
.settings-scroll { display: flex; flex-direction: column; }

.wm-layout { display: grid; grid-template-columns: minmax(0, 1.05fr) minmax(0, .95fr); gap: 20px; }
@media (max-width: 1180px) { .wm-layout { grid-template-columns: 1fr; } }
.var-chips { margin-top: 6px; }
.var-chip { cursor: pointer; margin: 0 4px 4px 0; user-select: none; }
.wm-form-actions { display: flex; gap: 8px; justify-content: flex-end; padding-top: 4px; border-top: 1px solid var(--panel-border); }

.preview-title { display: flex; align-items: center; font-weight: 600; margin-bottom: 10px; }
.preview-stage { border-radius: 10px; overflow: hidden; border: 1px solid var(--panel-border); }
.doc { position: relative; width: 100%; aspect-ratio: 4 / 3; background: linear-gradient(160deg, #fbfcfe, #eef1f6); overflow: hidden; }
.doc-head { position: absolute; top: 0; left: 0; right: 0; height: 34px; background: #fff; border-bottom: 1px solid #e6e9ef; display: flex; align-items: center; gap: 6px; padding: 0 12px; }
.doc-head i { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.doc-head .name { margin-left: 8px; font-size: 10px; color: #9aa3b2; }
.doc-body { position: absolute; top: 46px; left: 24px; right: 24px; bottom: 20px; color: #4a5262; }
.doc-body .title { font-size: 15px; font-weight: 700; color: #2b3140; text-align: center; margin-bottom: 14px; letter-spacing: 2px; }
.doc-body .ln { height: 7px; border-radius: 3px; background: #d9dee8; margin: 9px 0; }
.doc-body .ln.s { width: 60%; } .doc-body .ln.m { width: 85%; } .doc-body .ln.l { width: 100%; }
.doc-body .sig { margin-top: 18px; display: flex; justify-content: space-between; font-size: 10px; color: #8b93a3; }
.wm-layer { position: absolute; inset: 34px 0 0 0; pointer-events: none; overflow: hidden; display: flex; flex-wrap: wrap; align-content: center; justify-content: center; gap: var(--wmgap, 46px); padding: 20px; }
.wm-item { white-space: pre; user-select: none; line-height: 1.25; }
.preview-note { font-size: 11px; color: var(--muted); margin-top: 10px; }
.danger { color: var(--danger); }

.setting-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 14px; }
.setting-tile { border: 1px solid var(--panel-border); border-radius: 10px; padding: 14px 16px; }
.st-head { display: flex; align-items: center; }
.st-title { font-weight: 600; font-size: 13px; }
.st-desc { font-size: 11px; color: var(--muted); margin: 4px 0 12px; min-height: 28px; }
.st-ctrl { display: flex; align-items: center; gap: 8px; }
.st-unit { font-size: 12px; color: var(--muted); }

/* 本页内容较长（水印表单 + 实时预览 + 其他设置），不锁死视口高度：
   交回 FileLayout 的 .content（overflow-y:auto）统一纵向滚动，避免底部卡片与预览下半部分被裁掉。 */
.attachment-settings-page.ft-page {
  height: auto;
  max-height: none;
  min-height: 100%;
  overflow: visible;
  flex: 0 0 auto;
}
.attachment-settings-page .settings-scroll.ft-scroll {
  overflow: visible;
  flex: 0 0 auto;
}
</style>

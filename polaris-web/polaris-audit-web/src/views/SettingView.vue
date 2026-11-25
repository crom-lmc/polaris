<template>
  <div class="audit-settings">
    <div class="page-head">
      <div>
        <h2 class="title">审计全局设置</h2>
        <p class="desc">统一配置采集、忽略、留存与脱敏策略 · 保存后由审计服务即时生效</p>
      </div>
      <a-space>
        <a-button :disabled="!dirty" @click="doReset">放弃修改</a-button>
        <a-button :disabled="!canEdit" @click="restoreDefaults">
          <template #icon><reload-outlined /></template>恢复默认
        </a-button>
        <a-button v-if="canEdit" type="primary" :loading="saving" :disabled="!dirty" @click="doSave">
          <template #icon><save-outlined /></template>保存设置
        </a-button>
      </a-space>
    </div>

    <a-alert v-if="errorMsg" :message="errorMsg" type="warning" show-icon style="margin-bottom:12px" />
    <div v-if="dirty" class="dirty-tip"><span class="dot"></span>有未保存改动</div>

    <a-spin :spinning="loading">
      <a-empty v-if="visibleTabs.length === 0" description="当前账号没有可见的设置分区，请联系管理员授权" style="padding:60px 0" />
      <a-tabs v-else v-model:activeKey="activeTab" class="settings-tabs">
        <!-- 采集策略 -->
        <a-tab-pane v-if="canCollect" key="collect" tab="采集策略">
          <a-card size="small" class="panel" title="采集总开关">
            <template #extra><span class="panel-sub">四类数据源独立控制</span></template>
            <SettingRow label="网关访问审计" desc="所有经网关请求均记录（含 GET）；关闭后仅保留业务层写操作审计">
              <template #control><a-switch v-model:checked="form.collectGatewayAccess" :disabled="!canEdit" /><span class="sw-tip">{{ form.collectGatewayAccess ? '开启' : '关闭' }}</span></template>
            </SettingRow>
            <SettingRow label="业务操作审计" desc="POST / PUT / PATCH / DELETE 写操作留痕">
              <template #control><a-switch v-model:checked="form.collectBusinessOp" :disabled="!canEdit" /><span class="sw-tip">{{ form.collectBusinessOp ? '开启' : '关闭' }}</span></template>
            </SettingRow>
            <SettingRow label="登录事件审计" desc="登录成功与失败均记录；合规必留，强制开启不可关闭">
              <template #control><a-switch :checked="true" disabled /><span class="sw-tip">强制</span></template>
            </SettingRow>
            <SettingRow label="系统日志采集" desc="各应用 Logback Appender 采集 WARN / ERROR 写入 system_log；审计服务自身强制关闭防递归">
              <template #control><a-switch v-model:checked="form.collectSystemLog" :disabled="!canEdit" /><span class="sw-tip">{{ form.collectSystemLog ? '开启' : '关闭' }}</span></template>
            </SettingRow>
          </a-card>

          <a-card size="small" class="panel" title="采集参数">
            <template #extra><span class="panel-sub">控制上报粒度与体积</span></template>
            <SettingRow label="系统日志最低级别" desc="低于该级别的日志不上报；INFO 会产生大量记录，仅排障期临时调低">
              <template #control><a-select v-model:value="form.collectLogLevel" :disabled="!canEdit" :options="logLevelOptions" style="width:150px" /></template>
            </SettingRow>
            <SettingRow label="采集 Query 参数" desc="GET 与表单查询串，上报前按脱敏关键词打码">
              <template #control><a-switch v-model:checked="form.collectQueryParams" :disabled="!canEdit" /></template>
            </SettingRow>
            <SettingRow label="采集 JSON Body" desc="写操作请求体；开启后敏感业务建议保持关闭">
              <template #control><a-switch v-model:checked="form.collectJsonBody" :disabled="!canEdit" /><span class="sw-tip">{{ form.collectJsonBody ? '开启' : '关闭' }}</span></template>
            </SettingRow>
            <SettingRow label="单字段截断长度" desc="请求参数与响应摘要的最大字符数，超出自动截断">
              <template #control><a-input-number v-model:value="form.collectTruncateLength" :disabled="!canEdit" :min="100" :max="1800" /><span class="unit">字符</span></template>
            </SettingRow>
            <SettingRow label="系统日志队列容量" desc="有界队列，满时丢弃并计数告警">
              <template #control><a-input-number v-model:value="form.collectQueueCapacity" :disabled="!canEdit" :min="200" :max="100000" :step="100" /><span class="unit">条</span></template>
            </SettingRow>
          </a-card>
          <a-alert class="tip-alert" type="info" show-icon message="采集源头开关（网关/各服务侧）的完全关闭需通过 Nacos 下发；此处开关在审计服务接收端即时生效，可先按设置收敛落库。" />
        </a-tab-pane>

        <!-- 忽略规则 -->
        <a-tab-pane v-if="canIgnore" key="ignore" tab="忽略规则">
          <a-card size="small" class="panel" title="忽略路径前缀">
            <template #extra><span class="panel-sub">命中前缀的请求不再入库，按最长前缀匹配</span></template>
            <a-table :data-source="pathPrefixRows" :columns="pathColumns" :pagination="false" size="small" row-key="path">
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'path'"><code>{{ record.path }}</code></template>
                <template v-else-if="column.key === 'scope'">{{ record.scope }}</template>
                <template v-else-if="column.key === 'action'">
                  <a v-if="canEdit" class="link-danger" @click="removePathPrefix(record.path)">删除</a>
                  <span v-else class="muted">—</span>
                </template>
              </template>
            </a-table>
            <div class="add-row" v-if="canEdit">
              <a-input v-model:value="newPathPrefix" placeholder="如 /actuator/**" @pressEnter="addPathPrefix" />
              <a-button @click="addPathPrefix">＋ 新增前缀</a-button>
            </div>
            <a-alert class="tip-alert" type="info" show-icon message="匿名 /public/** 本就不在审计范围；此处仅用于降噪，不能替代权限控制。" />
          </a-card>

          <a-card size="small" class="panel" title="按应用与类别忽略">
            <template #extra><span class="panel-sub">被忽略的应用或类别将不写入事件表</span></template>
            <SettingRow label="忽略应用" desc="留空表示采集全部应用">
              <template #control>
                <a-select v-model:value="form.ignoreApps" mode="multiple" :disabled="!canEdit" :options="appOptions" placeholder="全选（不忽略）" style="width:280px" allowClear />
              </template>
            </SettingRow>
            <SettingRow label="忽略事件类别" desc="登录事件与敏感操作合规必留，不可忽略">
              <template #control>
                <a-checkbox-group v-model:value="form.ignoreCategories" :disabled="!canEdit">
                  <a-checkbox value="ACCESS">ACCESS 访问</a-checkbox>
                  <a-checkbox value="BUSINESS">BUSINESS 业务</a-checkbox>
                </a-checkbox-group>
              </template>
            </SettingRow>
            <a-alert class="tip-alert" type="warning" show-icon message="登录与敏感操作属于合规必留项，界面禁用忽略；后端同样强制保留，二者双重保障。" />
          </a-card>
        </a-tab-pane>

        <!-- 留存与清理 -->
        <a-tab-pane v-if="canRetention" key="retention" tab="留存与清理">
          <a-card size="small" class="panel" title="留存周期">
            <template #extra><span class="panel-sub">超过保留期按天批量清理，清理任务由审计服务定时执行</span></template>
            <SettingRow label="审计事件保留天数" desc="超过天数的审计事件将被清理">
              <template #control><a-input-number v-model:value="form.retentionEventDays" :disabled="!canEdit" :min="1" :max="3650" /><span class="unit">天</span></template>
            </SettingRow>
            <SettingRow label="系统日志保留天数" desc="system_log 体量更大，建议短于审计事件">
              <template #control><a-input-number v-model:value="form.retentionSyslogDays" :disabled="!canEdit" :min="1" :max="3650" /><span class="unit">天</span></template>
            </SettingRow>
            <SettingRow label="每日清理时间" desc="低峰期执行，单次批量删除避免长事务">
              <template #control><a-input v-model:value="form.retentionCleanTime" :disabled="!canEdit" placeholder="03:00" style="width:100px" /></template>
            </SettingRow>
            <SettingRow label="清理前归档" desc="预留开关：真实归档需对接文件存储位置，当前仅记录告警后直接清理">
              <template #control><a-switch v-model:checked="form.retentionArchiveBefore" :disabled="!canEdit" /><span class="sw-tip">{{ form.retentionArchiveBefore ? '开启' : '关闭' }}</span></template>
            </SettingRow>
            <a-alert class="tip-alert" type="warning" show-icon message="清理不可逆。首次启用建议先核对保留天数，并在小批量上验证；清理动作本身写本地日志留痕。" />
          </a-card>
        </a-tab-pane>

        <!-- 脱敏与隐私 -->
        <a-tab-pane v-if="canPrivacy" key="privacy" tab="脱敏与隐私">
          <a-card size="small" class="panel" title="脱敏规则">
            <template #extra><span class="panel-sub">匹配到的敏感键值统一替换为 ***</span></template>
            <SettingRow label="敏感字段关键词" desc="不区分大小写，支持子串匹配；接收端二次打码，服务端不存原文">
              <template #control>
                <div class="kw-wrap">
                  <a-tag v-for="kw in sensitiveKeywords" :key="kw" closable :disabled="!canEdit" @close.prevent="removeKeyword(kw)">{{ kw }}</a-tag>
                  <a-input v-if="canEdit" v-model:value="newKeyword" size="small" placeholder="新增关键词回车" style="width:150px" @pressEnter="addKeyword" />
                </div>
              </template>
            </SettingRow>
            <SettingRow label="记录客户端 IP" desc="取 X-Forwarded-For / X-Real-IP，用于溯源；登录事件强制记录">
              <template #control><a-switch v-model:checked="form.privacyRecordIp" :disabled="!canEdit" /><span class="sw-tip">{{ form.privacyRecordIp ? '开启' : '关闭' }}</span></template>
            </SettingRow>
            <SettingRow label="记录 User-Agent" desc="用于识别终端类型与异常客户端；登录事件强制记录">
              <template #control><a-switch v-model:checked="form.privacyRecordUa" :disabled="!canEdit" /><span class="sw-tip">{{ form.privacyRecordUa ? '开启' : '关闭' }}</span></template>
            </SettingRow>
            <SettingRow label="记录操作人账号 ID" desc="与用户名同时写入，支持按账号精确检索；关闭后只能按用户名查">
              <template #control><a-switch v-model:checked="form.privacyRecordOperatorId" :disabled="!canEdit" /><span class="sw-tip">{{ form.privacyRecordOperatorId ? '开启' : '关闭' }}</span></template>
            </SettingRow>
            <SettingRow label="日志正文截断长度" desc="system_log 的 message 与 stack_trace 分别截断">
              <template #control><a-input-number v-model:value="form.privacyLogTruncateLength" :disabled="!canEdit" :min="200" :max="3900" :step="100" /><span class="unit">字符</span></template>
            </SettingRow>
          </a-card>
        </a-tab-pane>

        <!-- 导出与合规 -->
        <a-tab-pane v-if="canExport" key="export" tab="导出与合规">
          <a-card size="small" class="panel" title="审计数据导出">
            <template #extra><span class="panel-sub">审计事件导出为预留能力，配置先行</span></template>
            <SettingRow label="开放审计事件导出" desc="预留：导出功能尚未实现，此处为策略预置">
              <template #control><a-switch v-model:checked="form.exportEnabled" :disabled="!canEdit" /><span class="sw-tip">{{ form.exportEnabled ? '开启' : '关闭' }}</span></template>
            </SettingRow>
            <SettingRow label="单次导出上限" desc="超过上限时提示缩小时间范围，防止拖库式导出">
              <template #control><a-input-number v-model:value="form.exportMaxRows" :disabled="!canEdit" :min="100" :max="1000000" :step="1000" /><span class="unit">条</span></template>
            </SettingRow>
            <SettingRow label="导出二次确认" desc="弹出确认框要求填写用途，用途随导出审计记录保存">
              <template #control><a-switch v-model:checked="form.exportConfirm" :disabled="!canEdit" /><span class="sw-tip">{{ form.exportConfirm ? '开启' : '关闭' }}</span></template>
            </SettingRow>
            <SettingRow label="导出文件水印文案" desc="预留：与附件水印一致的身份变量，烧录到导出首页">
              <template #control><a-input v-model:value="form.exportWatermark" :disabled="!canEdit" style="width:320px" /></template>
            </SettingRow>
            <SettingRow label="记录导出行为" desc="每次导出写入审计事件，包含操作人、条件与条数；合规必留，强制开启">
              <template #control><a-switch :checked="true" disabled /><span class="sw-tip">强制</span></template>
            </SettingRow>
          </a-card>
        </a-tab-pane>
      </a-tabs>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, h, watch } from 'vue'
import { message, Tooltip } from 'ant-design-vue'
import { SaveOutlined, ReloadOutlined, QuestionCircleOutlined } from '@ant-design/icons-vue'
import { getAuditSettings, saveAuditSettings, resetAuditSettings, type AuditSettings } from '@/api/audit'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const canEdit = computed(() => store.can('audit:setting:edit'))
/** 分区可见性：各分区有独立权限码；超管 store.can 恒为 true。 */
const canCollect = computed(() => store.can('audit:setting:collect'))
const canIgnore = computed(() => store.can('audit:setting:ignore'))
const canRetention = computed(() => store.can('audit:setting:retention'))
const canPrivacy = computed(() => store.can('audit:setting:privacy'))
const canExport = computed(() => store.can('audit:setting:export'))
const visibleTabs = computed(() => [
  { key: 'collect', ok: canCollect.value },
  { key: 'ignore', ok: canIgnore.value },
  { key: 'retention', ok: canRetention.value },
  { key: 'privacy', ok: canPrivacy.value },
  { key: 'export', ok: canExport.value }
].filter(t => t.ok).map(t => t.key))

/** 通用设置行：标题与控件同一行，说明收进问号提示，避免每行说明把页面撑高 */
const SettingRow = (props: { label: string; desc?: string }, { slots }: any) =>
  h('div', { class: 'setting-row' }, [
    h('div', { class: 'row-info' }, [
      h('span', { class: 'row-label' }, props.label),
      props.desc
        ? h(Tooltip, { title: props.desc, placement: 'top' }, {
            default: () => h(QuestionCircleOutlined, { class: 'hint-icon' })
          })
        : null
    ]),
    h('div', { class: 'row-control' }, slots.control?.())
  ])
SettingRow.props = ['label', 'desc']

interface FormState {
  collectGatewayAccess: boolean
  collectBusinessOp: boolean
  collectSystemLog: boolean
  collectLogLevel: string
  collectQueryParams: boolean
  collectJsonBody: boolean
  collectTruncateLength: number
  collectQueueCapacity: number
  ignoreApps: string[]
  ignoreCategories: string[]
  retentionEventDays: number
  retentionSyslogDays: number
  retentionCleanTime: string
  retentionArchiveBefore: boolean
  privacyRecordIp: boolean
  privacyRecordUa: boolean
  privacyRecordOperatorId: boolean
  privacyLogTruncateLength: number
  exportEnabled: boolean
  exportMaxRows: number
  exportConfirm: boolean
  exportWatermark: string
}

const form = reactive<FormState>({
  collectGatewayAccess: true, collectBusinessOp: true, collectSystemLog: true,
  collectLogLevel: 'WARN', collectQueryParams: true, collectJsonBody: false,
  collectTruncateLength: 1800, collectQueueCapacity: 2000,
  ignoreApps: [], ignoreCategories: [],
  retentionEventDays: 180, retentionSyslogDays: 90, retentionCleanTime: '03:00', retentionArchiveBefore: false,
  privacyRecordIp: true, privacyRecordUa: true, privacyRecordOperatorId: true, privacyLogTruncateLength: 4000,
  exportEnabled: false, exportMaxRows: 10000, exportConfirm: true, exportWatermark: '仅供内部使用 {username} {date}'
})

type FormKey = keyof FormState
const KEY_MAP: Record<FormKey, string> = {
  collectGatewayAccess: 'collect.gatewayAccess',
  collectBusinessOp: 'collect.businessOp',
  collectSystemLog: 'collect.systemLog',
  collectLogLevel: 'collect.logLevel',
  collectQueryParams: 'collect.queryParams',
  collectJsonBody: 'collect.jsonBody',
  collectTruncateLength: 'collect.truncateLength',
  collectQueueCapacity: 'collect.queueCapacity',
  ignoreApps: 'ignore.apps',
  ignoreCategories: 'ignore.categories',
  retentionEventDays: 'retention.eventDays',
  retentionSyslogDays: 'retention.systemLogDays',
  retentionCleanTime: 'retention.cleanTime',
  retentionArchiveBefore: 'retention.archiveBeforeClean',
  privacyRecordIp: 'privacy.recordIp',
  privacyRecordUa: 'privacy.recordUserAgent',
  privacyRecordOperatorId: 'privacy.recordOperatorId',
  privacyLogTruncateLength: 'privacy.logTruncateLength',
  exportEnabled: 'export.enabled',
  exportMaxRows: 'export.maxRows',
  exportConfirm: 'export.confirmRequired',
  exportWatermark: 'export.watermarkText'
}
const BOOL_KEYS = new Set<FormKey>([
  'collectGatewayAccess', 'collectBusinessOp', 'collectSystemLog', 'collectQueryParams', 'collectJsonBody',
  'retentionArchiveBefore', 'privacyRecordIp', 'privacyRecordUa', 'privacyRecordOperatorId',
  'exportEnabled', 'exportConfirm'
])
const LIST_KEYS = new Set<FormKey>(['ignoreApps', 'ignoreCategories'])
const NUM_KEYS = new Set<FormKey>([
  'collectTruncateLength', 'collectQueueCapacity', 'retentionEventDays', 'retentionSyslogDays',
  'privacyLogTruncateLength', 'exportMaxRows'
])

const logLevelOptions = [
  { label: 'WARN（推荐）', value: 'WARN' },
  { label: 'ERROR', value: 'ERROR' },
  { label: 'INFO（仅排障）', value: 'INFO' }
]
const appOptions = ['sso', 'audit', 'file', 'cms', 'portal', 'ehr', 'settings', 'job', 'forum', 'openapi']
  .map(a => ({ label: a, value: a }))

const pathColumns = [
  { title: '路径前缀', key: 'path', width: '60%' },
  { title: '作用范围', key: 'scope', width: '25%' },
  { title: '操作', key: 'action', width: '15%' }
]
const SCOPE_LABELS: Record<string, string> = {
  '/actuator/**': '健康检查', '/internal/**': '服务间调用',
  '/api/file/public/**': '匿名访问', '/favicon.ico': '静态资源'
}
const pathPrefixRows = computed(() => pathPrefixes.value.map(p => ({ path: p, scope: SCOPE_LABELS[p] || '自定义' })))
const pathPrefixes = ref<string[]>([])
const newPathPrefix = ref('')
const sensitiveKeywords = ref<string[]>([])
const newKeyword = ref('')

const activeTab = ref(visibleTabs.value[0] || 'collect')
const loading = ref(false)
const saving = ref(false)
const errorMsg = ref('')
const dirty = ref(false)
const baseline = ref('')

function serialize(): AuditSettings {
  const out: AuditSettings = {}
  ;(Object.keys(KEY_MAP) as FormKey[]).forEach(k => {
    const v = form[k]
    if (BOOL_KEYS.has(k)) out[KEY_MAP[k]] = v ? 'true' : 'false'
    else if (LIST_KEYS.has(k)) out[KEY_MAP[k]] = (v as string[]).join(',')
    else if (NUM_KEYS.has(k)) out[KEY_MAP[k]] = String(v)
    else out[KEY_MAP[k]] = String(v)
  })
  out['ignore.pathPrefixes'] = pathPrefixes.value.join(',')
  out['privacy.sensitiveKeywords'] = sensitiveKeywords.value.join(',')
  return out
}

function snapshot() {
  return JSON.stringify(serialize())
}

function applySettings(data: AuditSettings) {
  ;(Object.keys(KEY_MAP) as FormKey[]).forEach(k => {
    const raw = data[KEY_MAP[k]]
    if (raw === undefined) return
    if (BOOL_KEYS.has(k)) (form[k] as boolean) = raw === 'true'
    else if (LIST_KEYS.has(k)) (form[k] as string[]) = raw ? raw.split(',').map(s => s.trim()).filter(Boolean) : []
    else if (NUM_KEYS.has(k)) (form[k] as number) = Number(raw)
    else (form[k] as string) = raw
  })
  pathPrefixes.value = (data['ignore.pathPrefixes'] || '').split(',').map(s => s.trim()).filter(Boolean)
  sensitiveKeywords.value = (data['privacy.sensitiveKeywords'] || '').split(',').map(s => s.trim()).filter(Boolean)
}

async function loadAll() {
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await getAuditSettings()
    applySettings(data || {})
    baseline.value = snapshot()
    dirty.value = false
  } catch (e: any) {
    errorMsg.value = e?.message || '加载设置失败'
  } finally {
    loading.value = false
  }
}

async function doSave() {
  saving.value = true
  errorMsg.value = ''
  try {
    await saveAuditSettings(serialize())
    baseline.value = snapshot()
    dirty.value = false
    message.success('设置已保存，即时生效')
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function doReset() {
  void loadAll()
}

async function restoreDefaults() {
  try {
    await resetAuditSettings()
    message.success('已恢复默认设置')
    void loadAll()
  } catch (e: any) {
    message.error(e?.message || '恢复默认失败')
  }
}

function addPathPrefix() {
  const v = newPathPrefix.value.trim()
  if (v && !pathPrefixes.value.includes(v)) pathPrefixes.value.push(v)
  newPathPrefix.value = ''
}
function removePathPrefix(p: string) { pathPrefixes.value = pathPrefixes.value.filter(x => x !== p) }
function addKeyword() {
  const v = newKeyword.value.trim()
  if (v && !sensitiveKeywords.value.includes(v)) sensitiveKeywords.value.push(v)
  newKeyword.value = ''
}
function removeKeyword(k: string) { sensitiveKeywords.value = sensitiveKeywords.value.filter(x => x !== k) }

watch(serialize, () => { dirty.value = baseline.value !== '' && snapshot() !== baseline.value }, { deep: true })

onMounted(() => { void loadAll() })
</script>

<style scoped>
.audit-settings { padding: 0 2px; }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.title { margin: 0 0 4px; font-size: 18px; font-weight: 600; }
.desc { margin: 0; color: var(--muted); font-size: 12px; }
.dirty-tip { color: var(--warning, #e7b45a); font-size: 12px; margin-bottom: 10px; display: flex; align-items: center; gap: 6px; }
.dirty-tip .dot { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
.settings-tabs :deep(.ant-tabs-nav) { margin-bottom: 14px; }
.panel { margin-bottom: 14px; border-radius: 8px; }
.panel-sub { color: var(--muted); font-size: 12px; }
.sw-tip { margin-left: 8px; color: var(--muted); font-size: 12px; }
.unit { margin-left: 8px; color: var(--muted); font-size: 12px; }
.tip-alert { margin-bottom: 14px; }
.title-info { color: var(--muted); margin-left: 4px; cursor: help; }
.link-danger { color: #e46a76; }
.muted { color: var(--muted); }
code { background: var(--code-bg, rgba(127,127,127,.12)); padding: 1px 6px; border-radius: 4px; font-size: 12px; }
.add-row { display: flex; gap: 8px; margin-top: 12px; }
.add-row .ant-input { max-width: 320px; }
.manual-clean { margin-top: 14px; }
.kw-wrap { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.setting-row { display: flex; align-items: center; gap: 18px; padding: 10px 0; border-bottom: 1px solid var(--border-subtle, rgba(127,127,127,.15)); min-height: 44px; }
.setting-row:last-child { border-bottom: none; }
.setting-row .row-info { flex: 1; min-width: 0; display: flex; align-items: center; gap: 6px; }
.setting-row .row-label { font-size: 13px; }
.setting-row .hint-icon { color: var(--muted); font-size: 12px; cursor: help; }
.setting-row .row-control { flex: 0 0 auto; display: flex; align-items: center; }
</style>

<template>
  <div class="ft-page setting-page">
    <div class="ft-head setting-head">
      <div>
        <div class="setting-title">平台设置</div>
        <div class="setting-desc">网关级全局参数；保存后立即对所有调用生效</div>
      </div>
      <a-button size="small" :loading="loading" @click="load">刷新</a-button>
    </div>

    <div class="ft-scroll setting-scroll">
      <a-spin :spinning="loading">
        <a-alert
          v-if="!canEdit"
          type="warning"
          show-icon
          style="margin-bottom: 12px"
          message="你没有「openapi:setting:edit」权限，当前页面为只读"
        />

        <!-- 来源 IP 白名单：按第三方应用分别维护 -->
        <a-card size="small" class="setting-card">
          <template #title>
            <span class="card-title">来源 IP 白名单</span>
            <span class="card-subtitle">按第三方应用分别限制来源，未配置的回退全局设置</span>
          </template>
          <div class="whitelist-app-row">
            <span class="field-label">对接应用</span>
            <a-select
              v-model:value="selectedApp"
              style="width: 300px"
              :options="appOptions"
              option-filter-prop="label"
              show-search
              placeholder="选择第三方应用"
            />
            <a-tag :color="effectiveColor" class="effective-tag">{{ effectiveText }}</a-tag>
            <a-button
              v-if="canEdit"
              size="small"
              type="primary"
              :loading="savingWhitelist"
              @click="saveWhitelist"
            >
              保存
            </a-button>
          </div>
          <IpWhitelistEditor
            ref="whitelistRef"
            v-model="ipWhitelistValue"
            :disabled="!canEdit || !selectedApp"
          />
          <div class="whitelist-tip">
            * 全局白名单（<code>ip.whitelist</code>）作为所有未单独配置应用的回退；选择「全局白名单（回退）」可维护它。留空表示不限制来源 IP。
          </div>
        </a-card>

        <!-- 网关全局参数：两列 label / 输入框 -->
        <a-card size="small" class="setting-card">
          <template #title>
            <span class="card-title">网关全局参数</span>
            <span class="card-subtitle">对所有第三方调用生效</span>
          </template>
          <div class="field-grid">
            <div v-for="key in globalKeys" :key="key" class="field-item">
              <div class="field-line">
                <span class="field-label">{{ metaOf(key).label }}</span>
                <a-input-number
                  v-if="metaOf(key).kind === 'number'"
                  v-model:value="formValues[key]"
                  :min="0"
                  :precision="0"
                  style="width: 160px"
                  :disabled="!canEdit"
                />
                <a-input
                  v-else
                  v-model:value="formValues[key]"
                  style="flex: 1; min-width: 0"
                  :placeholder="metaOf(key).hint"
                  :disabled="!canEdit"
                />
                <a-button
                  v-if="canEdit"
                  size="small"
                  type="primary"
                  :loading="savingKey === key"
                  @click="save(key)"
                >
                  保存
                </a-button>
              </div>
              <div class="field-hint">{{ metaOf(key).hint }}</div>
            </div>
          </div>
        </a-card>
      </a-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  listSettings,
  saveSetting,
  listAppIpWhitelists,
  saveAppIpWhitelist,
  type Setting,
  type AppIpWhitelist
} from '@/api/openapi'
import { useUserStore } from '@/stores/user'
import IpWhitelistEditor from '@/components/IpWhitelistEditor.vue'

const GLOBAL_APP = '__GLOBAL__'
const GLOBAL_WHITELIST_KEY = 'ip.whitelist'

const store = useUserStore()
const canEdit = computed(() => store.can('openapi:setting:edit'))
const whitelistRef = ref<InstanceType<typeof IpWhitelistEditor> | null>(null)

interface SettingMeta {
  label: string
  hint: string
  kind: 'text' | 'number'
}

/** 全局参数展示方式；IP 白名单已独立到上方按应用维护，不在此列 */
const META: Record<string, SettingMeta> = {
  'global.rate.limit': {
    label: '全局限流（次/分钟）',
    hint: '每个应用每分钟的调用上限，留空不限制；与接口级限流取更严格者',
    kind: 'number'
  },
  'nonce.ttl.seconds': {
    label: '防重放窗口（秒）',
    hint: 'nonce 去重窗口，留空则使用签名容忍窗口',
    kind: 'number'
  },
  'mask.rules': {
    label: '日志脱敏规则',
    hint: 'JSON 路径到脱敏策略的映射，留空使用默认规则',
    kind: 'text'
  }
}

function metaOf(key: string): SettingMeta {
  return META[key] || { label: key, hint: '自定义设置项', kind: 'text' }
}

const loading = ref(false)
const savingKey = ref('')
const savingWhitelist = ref(false)
const settings = ref<Setting[]>([])
const appWhitelists = ref<AppIpWhitelist[]>([])
const formValues = reactive<Record<string, string>>({})
const selectedApp = ref<string>('')
const ipWhitelistValue = ref('')

/** 全局参数键：已知键固定展示，后端多出的非白名单键追加在后面 */
const globalKeys = computed(() => {
  const list = Object.keys(META)
  settings.value.forEach((s) => {
    if (s.settingKey !== GLOBAL_WHITELIST_KEY && !s.settingKey.startsWith('ip.whitelist.')
        && !list.includes(s.settingKey)) {
      list.push(s.settingKey)
    }
  })
  return list
})

/** 应用下拉：全局回退项 + 全部第三方应用 */
const appOptions = computed(() => {
  const options = [{ label: '全局白名单（回退）', value: GLOBAL_APP }]
  appWhitelists.value.forEach((a) => {
    options.push({ label: a.appName ? `${a.appName}（${a.appCode}）` : a.appCode, value: a.appCode })
  })
  return options
})

const currentWhitelist = computed(() =>
  selectedApp.value ? appWhitelists.value.find((a) => a.appCode === selectedApp.value) : undefined
)

const effectiveText = computed(() => {
  if (selectedApp.value === GLOBAL_APP) return '全局回退'
  const map: Record<string, string> = { APP: '应用级生效', GLOBAL: '回退全局', NONE: '不限制' }
  return map[currentWhitelist.value?.effectiveFrom || ''] || '—'
})

const effectiveColor = computed(() => {
  if (selectedApp.value === GLOBAL_APP) return 'blue'
  return currentWhitelist.value?.effectiveFrom === 'APP' ? 'green' : 'default'
})

/** 切换应用时载入对应白名单；全局项读取 setting 里的 ip.whitelist */
watch(selectedApp, (code) => {
  if (code === GLOBAL_APP) {
    ipWhitelistValue.value = formValues[GLOBAL_WHITELIST_KEY] || ''
  } else if (code) {
    ipWhitelistValue.value = currentWhitelist.value?.whitelist || ''
  } else {
    ipWhitelistValue.value = ''
  }
})

async function load() {
  loading.value = true
  try {
    const [rows, apps] = await Promise.all([listSettings(), listAppIpWhitelists()])
    settings.value = rows
    rows.forEach((s) => {
      formValues[s.settingKey] = s.settingValue ?? ''
    })
    globalKeys.value.forEach((k) => {
      if (!(k in formValues)) formValues[k] = ''
    })
    appWhitelists.value = apps
    if (!selectedApp.value) {
      selectedApp.value = apps.length ? apps[0].appCode : GLOBAL_APP
    } else {
      // 触发 watch 重新载入当前选中项
      const code = selectedApp.value
      selectedApp.value = ''
      void Promise.resolve().then(() => { selectedApp.value = code })
    }
  } catch (e: any) {
    message.error(e?.message || '加载平台设置失败')
  } finally {
    loading.value = false
  }
}

async function save(key: string) {
  if (!canEdit.value) return
  savingKey.value = key
  try {
    await saveSetting(key, String(formValues[key] ?? ''))
    message.success('设置已保存')
  } catch (e: any) {
    message.error(e?.message || '保存设置失败')
  } finally {
    savingKey.value = ''
  }
}

async function saveWhitelist() {
  if (!canEdit.value || !selectedApp.value) return
  if (whitelistRef.value && !whitelistRef.value.validate()) return
  savingWhitelist.value = true
  try {
    if (selectedApp.value === GLOBAL_APP) {
      const value = ipWhitelistValue.value
      await saveSetting(GLOBAL_WHITELIST_KEY, value)
      formValues[GLOBAL_WHITELIST_KEY] = value
    } else {
      await saveAppIpWhitelist(selectedApp.value, ipWhitelistValue.value)
      const target = appWhitelists.value.find((a) => a.appCode === selectedApp.value)
      if (target) {
        target.whitelist = ipWhitelistValue.value
        target.effectiveFrom = ipWhitelistValue.value.trim() ? 'APP'
          : (formValues[GLOBAL_WHITELIST_KEY]?.trim() ? 'GLOBAL' : 'NONE')
      }
    }
    message.success('白名单已保存')
  } catch (e: any) {
    message.error(e?.message || '保存白名单失败')
  } finally {
    savingWhitelist.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.setting-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
}
.setting-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-base);
}
.setting-desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--muted);
}
.setting-scroll {
  padding-right: 4px;
}
.setting-card {
  margin-bottom: 12px;
}
.setting-card :deep(.ant-card-head) {
  min-height: 42px;
  padding: 0 16px;
}
.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-base);
}
.card-subtitle {
  margin-left: 12px;
  font-size: 12px;
  font-weight: 400;
  color: var(--muted);
}
.whitelist-app-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.field-label {
  flex: none;
  font-size: 13px;
  color: var(--text-base);
}
.effective-tag {
  margin: 0;
}
.whitelist-tip {
  margin-top: 12px;
  font-size: 12px;
  color: var(--muted);
}
.whitelist-tip code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 32px;
}
.field-line {
  display: flex;
  align-items: center;
  gap: 12px;
}
.field-line .field-label {
  width: 150px;
}
.field-hint {
  margin-top: 6px;
  padding-left: 162px;
  font-size: 12px;
  color: var(--muted);
}
</style>

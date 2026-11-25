<template>
  <div class="ac-root" @click.stop>
    <!-- 宫格入口：截图右上角那个圆角方块九宫格图标 -->
    <button
      class="ac-trigger"
      :class="{ 'is-open': open }"
      type="button"
      :title="`应用中心（共 ${apps.length} 个应用）`"
      @click="toggle"
    >
      <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden="true">
        <rect x="3" y="3" width="7" height="7" rx="1.6" />
        <rect x="14" y="3" width="7" height="7" rx="1.6" />
        <rect x="3" y="14" width="7" height="7" rx="1.6" />
        <rect x="14" y="14" width="7" height="7" rx="1.6" />
      </svg>
    </button>

    <transition name="ac-fade">
      <div v-if="open" class="ac-panel" @click.stop>
        <div class="ac-head">
          <h3 class="ac-head-title">应用中心 <span class="ac-count">共 {{ filteredGroups.reduce((n, g) => n + g.apps.length, 0) }} 个应用</span></h3>
          <button class="ac-close" type="button" title="关闭" @click="open = false">×</button>
        </div>

        <div class="ac-search-wrap">
          <svg class="ac-search-icon" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <circle cx="11" cy="11" r="7" /><path d="M20 20l-3.5-3.5" stroke-linecap="round" />
          </svg>
          <input
            ref="searchRef"
            v-model="keyword"
            class="ac-search"
            type="text"
            placeholder="搜索应用名称、编码或功能..."
            @keydown.esc="open = false"
          />
          <span class="ac-kbd">/</span>
        </div>

        <div ref="bodyRef" class="ac-body">
          <section v-for="group in filteredGroups" :key="group.type" class="ac-group">
            <div class="ac-group-head">
              <h3>{{ group.label }}</h3>
              <span>{{ group.apps.length }} 个应用</span>
            </div>
            <div class="ac-grid">
              <button
                v-for="app in group.apps"
                :key="app.code"
                type="button"
                class="ac-app"
                :class="{ 'is-current': app.code === currentCode, 'is-busy': busy === app.code }"
                :disabled="app.code === currentCode || !!busy"
                :title="app.desc"
                @click="enter(app)"
              >
                <img
                  v-if="!iconErrors[app.code]"
                  class="ac-app-logo"
                  :src="iconUrl(app)"
                  :alt="app.name"
                  @error="iconErrors[app.code] = true"
                />
                <span v-else class="ac-app-logo ac-app-logo-fallback" :style="{ background: app.bg, color: app.fg }">{{ app.mark }}</span>
                <span class="ac-app-text">
                  <span class="ac-app-name">
                    {{ app.name }}
                    <span v-if="app.code === currentCode" class="ac-current-tag">当前</span>
                  </span>
                  <span class="ac-app-code">{{ app.code }}</span>
                  <span class="ac-app-remark">{{ app.desc }}</span>
                </span>
              </button>
            </div>
          </section>
          <div v-if="!filteredGroups.length" class="ac-empty">没有匹配的应用</div>
        </div>

        <div v-if="error" class="ac-error">{{ error }}</div>
        <div class="ac-foot">
          <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <rect x="5" y="10" width="14" height="11" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3" />
          </svg>
          北极星钥 统一认证 · 无需重复登录
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

/** 当前应用编码由各应用传入，用于标记「当前」并禁止点击 */
const props = defineProps<{ currentCode: string }>()

type AppItem = {
  code: string
  name: string
  desc: string
  icon: string
  mark: string
  bg: string
  fg: string
  url: string
  /** 注册在 sso_application.redirect_uri 的回跳地址，授权码必须送到这里才能换令牌 */
  callback: string
}
type Group = { type: string; label: string; apps: AppItem[] }

/**
 * 11 个应用的权威清单：名称/图标/分类与 sso_application + 我的应用保持一致。
 * callback 与生产 redirect_uri 完全一致；不送首页，否则门户/论坛拿不到授权码。
 */
const ALL_APPS: AppItem[] = [
  { code: 'sso-center', name: '北极星钥', desc: '统一身份认证', icon: 'sso-center', mark: '钥', bg: '#eef0ff', fg: '#5b63e6', url: 'https://polaris-sso.laimengchao.site/', callback: '' },
  { code: 'settings', name: '北极星典', desc: '字典与审批配置', icon: 'settings', mark: '典', bg: '#eef1f5', fg: '#5a6b82', url: 'https://polaris-settings.laimengchao.site/', callback: 'https://polaris-settings.laimengchao.site/callback' },
  { code: 'job', name: '北极星轨', desc: '定时任务调度', icon: 'job', mark: '轨', bg: '#e9f4ff', fg: '#2b7fc4', url: 'https://polaris-job.laimengchao.site/', callback: 'https://polaris-job.laimengchao.site/callback' },
  { code: 'openapi', name: '北极星桥', desc: '接口开放平台', icon: 'openapi', mark: '桥', bg: '#eaf6f7', fg: '#1f9096', url: 'https://polaris-openapi.laimengchao.site/', callback: 'https://polaris-openapi.laimengchao.site/callback' },
  { code: 'file', name: '北极星云', desc: '统一文件存储', icon: 'file', mark: '云', bg: '#eaf1ff', fg: '#3f6fd8', url: 'https://polaris-file.laimengchao.site/', callback: 'https://polaris-file.laimengchao.site/callback' },
  { code: 'audit', name: '北极星眼', desc: '统一审计与日志', icon: 'audit', mark: '眼', bg: '#f6efe6', fg: '#b07a3e', url: 'https://polaris-audit.laimengchao.site/', callback: 'https://polaris-audit.laimengchao.site/callback' },
  { code: 'ehr', name: '北极星宿', desc: '人力中心', icon: 'ehr', mark: '宿', bg: '#e9f2ff', fg: '#2f6fd0', url: 'https://polaris-ehr.laimengchao.site/', callback: 'https://polaris-ehr.laimengchao.site/callback' },
  { code: 'cms', name: '北极星源', desc: '内容中心', icon: 'cms', mark: '源', bg: '#fff3e8', fg: '#e08434', url: 'https://polaris-cms.laimengchao.site/', callback: 'https://polaris-cms.laimengchao.site/callback' },
  { code: 'forum-admin', name: '北极星核', desc: '论坛管理后台', icon: 'forum-admin', mark: '核', bg: '#fceef4', fg: '#d04a86', url: 'https://polaris-forum.laimengchao.site/admin/', callback: 'https://polaris-forum.laimengchao.site/admin/callback' },
  { code: 'portal', name: '北极星门', desc: '内容展示门户', icon: 'portal', mark: '门', bg: '#e8f7f1', fg: '#1a9d72', url: 'https://polaris-cms.laimengchao.site/portal/', callback: 'https://polaris-cms.laimengchao.site/portal/callback' },
  { code: 'forum', name: '北极星空', desc: '社区论坛', icon: 'forum', mark: '空', bg: '#f0edff', fg: '#7c5cff', url: 'https://polaris-forum.laimengchao.site/', callback: 'https://polaris-forum.laimengchao.site/auth/callback' }
]

/** 与「我的应用」app_type 字典同口径：SYSTEM / MANAGEMENT / PORTAL；标题在线跟随字典，离线时用生产口径兜底 */
const GROUP_DEFS = [
  { type: 'SYSTEM', label: '太空基地', codes: ['sso-center', 'settings', 'job', 'openapi', 'file', 'audit'] },
  { type: 'MANAGEMENT', label: '指挥调度', codes: ['ehr', 'cms', 'forum-admin'] },
  { type: 'PORTAL', label: '星辰生活区', codes: ['portal', 'forum'] }
]

const apps = ALL_APPS
const open = ref(false)
const keyword = ref('')
const groupLabels = ref<Record<string, string>>(
  Object.fromEntries(GROUP_DEFS.map(g => [g.type, g.label]))
)
const busy = ref('')
const error = ref('')
const searchRef = ref<HTMLInputElement | null>(null)
const bodyRef = ref<HTMLElement | null>(null)
const iconErrors = ref<Record<string, boolean>>({})

/** 图标来自账号中心静态目录，跨应用统一使用 SSO 域名绝对地址 */
const SSO_ORIGIN = ((import.meta.env.VITE_SSO_ORIGIN as string) ||
  (import.meta.env.DEV ? 'http://localhost:5173' : 'https://polaris-sso.laimengchao.site')).replace(/\/$/, '')
function iconUrl(app: AppItem) {
  return `${SSO_ORIGIN}/icons/${app.icon}.svg`
}

const filteredGroups = computed<Group[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  const match = (a: AppItem) => !kw || `${a.name} ${a.code} ${a.desc} ${a.mark}`.toLowerCase().includes(kw)
  return GROUP_DEFS.map(g => ({
    type: g.type,
    label: groupLabels.value[g.type] || g.label,
    apps: g.codes.map(c => ALL_APPS.find(a => a.code === c)).filter((a): a is AppItem => !!a && match(a))
  })).filter(g => g.apps.length > 0)
})

/** 分组标题与「我的应用」同源，字典改了标题自动同步，失败则保留兜底文案 */
async function loadGroupLabels() {
  try {
    const res = await fetch('/api/settings/public/dicts/sso.application_type')
    const body = await res.json()
    const items = body?.data
    if (!res.ok || body?.code !== 0 || !Array.isArray(items)) return
    const labels: Record<string, string> = {}
    for (const item of items) {
      if (item && typeof item.value === 'string' && typeof item.label === 'string' && item.label.trim()) {
        labels[item.value] = item.label.trim()
      }
    }
    groupLabels.value = { ...groupLabels.value, ...labels }
  } catch {
    // 字典不可用时保留兜底，不影响应用跳转
  }
}

function toggle() {
  open.value = !open.value
  if (open.value) {
    keyword.value = ''
    error.value = ''
    // 避免上次滚动位置残留，导致再次打开只看到最后一个分组
    void nextTick(() => {
      if (bodyRef.value) bodyRef.value.scrollTop = 0
      searchRef.value?.focus()
    })
  }
}

/** 兼容各应用不同的 token 存储键（xxx_access_token / accessToken 等） */
function findAccessToken(): string {
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (!key || !/access.?token$/i.test(key)) continue
    const val = localStorage.getItem(key)
    if (val && val.trim()) return val.trim()
  }
  return ''
}

function randomState(): string {
  const buf = new Uint8Array(12)
  crypto.getRandomValues(buf)
  return Array.from(buf).map(b => b.toString(16).padStart(2, '0')).join('')
}

function openTarget(target: string, tab: Window | null) {
  if (tab) {
    tab.location.href = target
    try { tab.opener = null } catch { /* 跨源后不可写，忽略 */ }
  } else {
    const fallback = window.open(target, '_blank')
    if (!fallback) window.location.href = target
  }
  open.value = false
}

/**
 * 跳目标应用：必须走各应用注册过的 callback 回跳，授权码在那里换令牌。
 * 首页回跳会让门户/论坛拿不到 code，表现为"点了没登录"。
 */
async function enter(app: AppItem) {
  if (app.code === props.currentCode || busy.value) return
  busy.value = app.code
  error.value = ''
  const tab = window.open('about:blank', '_blank') // 必须在 await 前同步开窗，防弹窗拦截
  try {
    if (app.code === 'sso-center') {
      openTarget(app.url, tab)
      return
    }
    const token = findAccessToken()
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (token) headers.Authorization = `Bearer ${token}`
    const state = randomState()
    sessionStorage.setItem('polaris_appcenter_oauth_state', state)
    const res = await fetch('/api/auth/oauth/authorize', {
      method: 'POST',
      headers,
      credentials: 'include',
      body: JSON.stringify({
        responseType: 'code',
        clientId: app.code,
        redirectUri: app.callback || app.url,
        state
      })
    })
    const body = await res.json()
    if (!res.ok || body.code !== 0 || !body.data?.redirectTo) {
      throw new Error(body?.message || '统一认证签码失败')
    }
    openTarget(body.data.redirectTo as string, tab)
  } catch {
    error.value = '统一认证签码失败，已直接打开目标应用'
    openTarget(app.url, tab)
  } finally {
    busy.value = ''
  }
}

function onDocClick() {
  if (open.value) open.value = false
}
function onKey(e: KeyboardEvent) {
  const tag = (e.target as HTMLElement | null)?.tagName
  const typing = tag === 'INPUT' || tag === 'TEXTAREA'
  if (e.key === '/' && !typing && !open.value) {
    e.preventDefault()
    toggle()
  } else if (e.key === 'Escape' && open.value) {
    open.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKey)
  void loadGroupLabels()
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKey)
})
</script>

<style scoped>
/* 两套主题变量都兼容：业务应用用 --bg-page/--text-base，论坛用 --surface/--text */
.ac-root {
  --ac-bg: var(--bg-page, var(--surface, #ffffff));
  --ac-elevated: var(--bg-elevated, var(--soft, var(--code-bg, #f6f7fb)));
  --ac-text: var(--text-base, var(--text, #1f2430));
  --ac-header-text: var(--header-text, var(--text, #64748b));
  --ac-muted: var(--muted, #8b93a7);
  --ac-border: var(--border, #dfe3ec);
  --ac-border-subtle: var(--border-subtle, var(--border, #eef0f5));
  --ac-primary: var(--primary, #2f54eb);
  --ac-hover: var(--header-hover, rgba(47, 84, 235, 0.12));
  position: relative;
  display: inline-flex;
}
:global(html[data-theme='dark']) .ac-root {
  --ac-bg: #191e29;
  --ac-elevated: #232a38;
  --ac-text: rgba(255, 255, 255, 0.88);
  --ac-header-text: rgba(255, 255, 255, 0.82);
  --ac-muted: #9ba4b8;
  --ac-border: rgba(255, 255, 255, 0.16);
  --ac-border-subtle: rgba(255, 255, 255, 0.1);
  --ac-primary: #8791ff;
  --ac-hover: rgba(255, 255, 255, 0.1);
}
.ac-trigger {
  display: inline-flex; align-items: center; justify-content: center;
  width: 32px; height: 32px; padding: 0; border: 0; border-radius: 8px;
  cursor: pointer; color: var(--ac-header-text); background: transparent;
  transition: background 0.18s, color 0.18s;
}
.ac-trigger:hover, .ac-trigger.is-open { background: var(--ac-hover); color: var(--ac-primary); }
.ac-panel {
  position: absolute; top: calc(100% + 10px); right: 0; z-index: 1000;
  width: 720px; max-width: calc(100vw - 32px);
  border: 1px solid var(--ac-border-subtle); border-radius: 10px;
  background: var(--ac-bg); color: var(--ac-text);
  box-shadow: 0 12px 36px rgba(15, 23, 42, 0.18);
  overflow: hidden;
}
.ac-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 18px 10px;
}
.ac-head-title {
  margin: 0; color: var(--ac-text);
  font-size: 20px; font-weight: 600; line-height: 1.4;
}
.ac-count { margin-left: 8px; font-size: 12px; font-weight: 400; color: var(--ac-muted); }
.ac-close { border: 0; background: transparent; cursor: pointer; font-size: 20px; line-height: 1; color: var(--ac-muted); padding: 0 2px; }
.ac-close:hover { color: var(--ac-text); }
.ac-search-wrap { position: relative; margin: 0 18px 12px; }
.ac-search-icon { position: absolute; left: 11px; top: 50%; transform: translateY(-50%); color: var(--ac-muted); }
.ac-search {
  width: 100%; height: 36px; padding: 0 34px; box-sizing: border-box;
  border: 1px solid var(--ac-border); border-radius: 8px;
  background: var(--ac-elevated); color: var(--ac-text);
  font-size: 13px; outline: none; transition: border-color 0.18s, box-shadow 0.18s;
}
.ac-search::placeholder { color: var(--ac-muted); }
.ac-search:focus { border-color: var(--ac-primary); box-shadow: 0 0 0 3px rgba(47, 84, 235, 0.12); }
.ac-kbd {
  position: absolute; right: 10px; top: 50%; transform: translateY(-50%);
  font-size: 11px; color: var(--ac-muted); border: 1px solid var(--ac-border);
  border-radius: 4px; padding: 0 5px; line-height: 17px;
}
.ac-body { max-height: 420px; overflow-y: auto; padding: 0 18px 8px; }
.ac-group + .ac-group { margin-top: 24px; }
.ac-group-head {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  margin-bottom: 12px;
}
.ac-group-head h3 {
  margin: 0; color: var(--ac-text);
  font-size: 16px; font-weight: 600;
}
.ac-group-head span { color: var(--ac-muted); font-size: 12px; }
.ac-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.ac-app {
  display: flex; flex-direction: column; gap: 10px; text-align: left;
  min-width: 0; height: 100%; padding: 12px 14px;
  border: 1px solid var(--ac-border-subtle); border-radius: 10px;
  background: transparent; color: var(--ac-text); cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.ac-app:hover:not(:disabled), .ac-app:focus-visible:not(:disabled) {
  border-color: var(--ac-primary);
  box-shadow: 0 4px 16px rgba(47, 84, 235, 0.16);
  outline: none;
}
.ac-app.is-current {
  cursor: default; border-color: var(--ac-primary);
  box-shadow: 0 4px 16px rgba(47, 84, 235, 0.12);
}
.ac-app.is-busy { opacity: 0.6; }
.ac-app-logo {
  width: 42px; height: 42px; flex: 0 0 auto;
  border-radius: 10px; object-fit: contain;
}
.ac-app-logo-fallback {
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 16px; font-weight: 600;
}
.ac-app-text { min-width: 0; display: flex; flex-direction: column; }
.ac-app-name {
  overflow: hidden; color: var(--ac-text);
  font-size: 14px; font-weight: 600;
  text-overflow: ellipsis; white-space: nowrap;
}
.ac-app-code {
  margin-top: 4px; overflow: hidden; color: var(--ac-muted);
  font-size: 12px; text-overflow: ellipsis; white-space: nowrap;
}
.ac-app-remark {
  margin-top: 10px; overflow: hidden; color: var(--ac-muted);
  font-size: 12px; text-overflow: ellipsis; white-space: nowrap;
}
.ac-current-tag { margin-left: 5px; font-size: 10px; font-weight: 500; color: var(--ac-primary); }
.ac-empty { padding: 30px 0; text-align: center; color: var(--ac-muted); font-size: 13px; }
.ac-error { margin: 0 18px 8px; padding: 7px 10px; border-radius: 7px; background: rgba(220, 53, 69, 0.1); color: #e56574; font-size: 11px; }
.ac-foot {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 11px; border-top: 1px solid var(--ac-border-subtle);
  font-size: 11px; color: var(--ac-muted); background: var(--ac-elevated);
}
.ac-fade-enter-active, .ac-fade-leave-active { transition: opacity 0.14s, transform 0.14s; }
.ac-fade-enter-from, .ac-fade-leave-to { opacity: 0; transform: translateY(-4px); }
@media (max-width: 900px) {
  .ac-panel { width: calc(100vw - 32px); right: -8px; }
  .ac-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 560px) {
  .ac-grid { grid-template-columns: 1fr; }
  .ac-body { max-height: min(62vh, 460px); }
}
</style>

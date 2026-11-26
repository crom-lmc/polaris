<template>
  <div class="page-container embed-page">
    <div class="page-root embed-layout">
      <!-- 左侧树：埋点分组 → 应用 → 模块，点击过滤右侧列表（与消息配置同构） -->
      <aside class="panel module-tree">
        <div class="panel-body tree-body">
          <a-tree
            v-if="rows.length"
            class="approval-nav-tree"
            :tree-data="treeData"
            :selected-keys="selectedKey ? [selectedKey] : []"
            :expanded-keys="expandedKeys"
            block-node
            @select="onSelect"
            @expand="onExpand"
          >
            <template #title="node">
              <span class="approval-nav-node">
                <span :class="['approval-nav-name', navTitleClass(String(node.key))]">
                  {{ node.title }}<template v-if="String(node.key).startsWith('app:')">（{{ navCount(node) }}）</template>
                </span>
              </span>
            </template>
          </a-tree>
          <a-empty v-else :image-style="{ height: '40px' }" description="暂无埋点" />
        </div>
      </aside>

      <section class="panel embed-main">
        <div class="panel-head">
          <span>埋点配置</span>
          <a-space :size="8">
            <a-input
              v-model:value="keyword"
              placeholder="搜 key / 描述 / 模块"
              allow-clear
              style="width: 220px"
              @press-enter="load"
            />
            <a-button @click="load">查询</a-button>
          </a-space>
        </div>

        <div class="panel-body table-body">
          <a-table
            :columns="columns"
            :data-source="filteredRows"
            :loading="loading"
            row-key="id"
            size="small"
            :pagination="false"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'code'">
                <span class="mono">{{ record.code }}</span>
              </template>
              <template v-else-if="column.key === 'appCode'">
                {{ record.appCode ? appNameOf(record.appCode) : '通用' }}
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space :size="12">
                  <a v-if="canManage" @click="openEdit(record)">编辑</a>
                  <a @click="openView(record)">查看</a>
                  <a @click="openHistory(record)">历史记录</a>
                </a-space>
              </template>
            </template>
          </a-table>
        </div>
      </section>
    </div>

    <!-- 查看 / 编辑：埋点与页面绑定，不支持在配置中心新增 -->
    <a-drawer
      v-model:open="drawerOpen"
      :title="drawerMode === 'history' ? '查看历史记录' : drawerMode === 'edit' ? '编辑埋点' : '查看埋点'"
      width="640"
      destroy-on-close
    >
      <template v-if="current">
        <a-descriptions :column="1" size="small" bordered class="meta">
          <a-descriptions-item label="Key"><span class="mono">{{ current.code }}</span></a-descriptions-item>
          <a-descriptions-item label="所属应用">{{ current.appCode ? appNameOf(current.appCode) : '通用' }}</a-descriptions-item>
          <a-descriptions-item label="模块">{{ current.module || '-' }}</a-descriptions-item>
          <a-descriptions-item label="描述">
            <a-input
              v-model:value="form.name"
              :disabled="drawerMode !== 'edit'"
              placeholder="埋点用途说明"
            />
          </a-descriptions-item>
        </a-descriptions>

        <!-- 试用期提醒收件人：部门多选 + 人员多选 + 直属主管复选 -->
        <template v-if="editor === 'receiver'">
          <a-divider orientation="left">收件人维护</a-divider>
          <a-form layout="vertical">
            <a-form-item label="按部门（含所有下级）">
              <a-tree-select
                v-model:value="orgIds"
                :tree-data="orgTreeData"
                tree-checkable
                allow-clear
                show-search
                tree-node-filter-prop="title"
                :loading="receiverLoading"
                :disabled="isReadonly"
                placeholder="可多选部门，保存后展开为部门内已开户在职员工"
                max-tag-count="responsive"
                @change="syncReceiverJson"
              />
            </a-form-item>
            <a-form-item label="按人（账号）">
              <a-select
                v-model:value="accountIds"
                mode="multiple"
                show-search
                allow-clear
                option-filter-prop="label"
                :options="employeeOptions"
                :loading="receiverLoading"
                :disabled="isReadonly"
                placeholder="可搜姓名 / 工号，可多选"
                @change="syncReceiverJson"
              />
            </a-form-item>
            <a-form-item>
              <a-checkbox v-model:checked="directSupervisor" :disabled="isReadonly" @change="syncReceiverJson">
                同时发送给员工的直属主管
              </a-checkbox>
            </a-form-item>
          </a-form>
        </template>

        <!-- 新开账户默认授权角色：角色编码多选 -->
        <template v-if="editor === 'roles'">
          <a-divider orientation="left">默认授权角色</a-divider>
          <a-form layout="vertical">
            <a-form-item label="角色编码（可输入后回车新增）">
              <a-select
                v-model:value="roleCodes"
                mode="tags"
                allow-clear
                :options="roleOptions"
                :disabled="isReadonly"
                placeholder="如 EMPLOYEE_SELF"
                @change="syncRolesJson"
              />
            </a-form-item>
          </a-form>
        </template>

        <!-- 新开账户默认开通应用：应用多选 -->
        <template v-if="editor === 'apps'">
          <a-divider orientation="left">默认开通应用</a-divider>
          <a-form layout="vertical">
            <a-form-item label="应用（可多选，新开账户自动授权）">
              <a-select
                v-model:value="defaultApps"
                mode="multiple"
                allow-clear
                option-filter-prop="label"
                show-search
                :options="appOptions"
                :disabled="isReadonly"
                placeholder="选择新开账户默认开通的应用"
                @change="syncAppsJson"
              />
            </a-form-item>
          </a-form>
        </template>

        <!-- 模拟考勤定时任务开关：布尔开关 -->
        <template v-if="editor === 'switch'">
          <a-divider orientation="left">开关配置</a-divider>
          <a-form layout="vertical">
            <a-form-item :label="current?.name || '开关'">
              <a-switch
                v-model:checked="mockEnabled"
                :disabled="isReadonly"
                checked-children="开启"
                un-checked-children="关闭"
                @change="syncSwitchJson"
              />
              <span class="muted switch-tip">开关状态保存后即时生效（配置中心 60 秒缓存）</span>
            </a-form-item>
          </a-form>
        </template>

        <!-- 社区等级数量上限：数字输入 -->
        <template v-if="editor === 'number'">
          <a-divider orientation="left">数值配置</a-divider>
          <a-form layout="vertical">
            <a-form-item :label="current?.name || '数值'">
              <a-input-number
                v-model:value="numberValue"
                :min="0"
                :max="numberMax(currentCode)"
                :disabled="isReadonly"
                placeholder="请输入数值"
                style="width: 220px"
                @change="syncNumberJson"
              />
              <span class="muted switch-tip">保存后最多 60 秒生效（配置中心缓存）；后端有内置兜底值
                <template v-if="currentCode === MEETING_MAX_ADVANCE_KEY">；0 表示仅可预约当天</template>
                <template v-else-if="currentCode === MEETING_NO_SHOW_GRACE_KEY">；0 表示会议开始即自动释放</template>
                <template v-else-if="currentCode === MEETING_MAX_DURATION_KEY">；0 表示单次时长不限制</template>
              </span>
            </a-form-item>
          </a-form>
        </template>

        <!-- 会议室爽约限制：统计天数 / 触发次数 / 禁约天数 -->
        <template v-if="editor === 'noShowRule'">
          <a-divider orientation="left">爽约限制规则</a-divider>
          <a-form layout="vertical">
            <a-form-item label="统计天数">
              <a-input-number v-model:value="noShowWindowDays" :min="1" :max="365" :disabled="isReadonly"
                              placeholder="近多少天内的爽约计入统计" style="width: 220px"
                              @change="syncNoShowRuleJson" />
            </a-form-item>
            <a-form-item label="触发次数">
              <a-input-number v-model:value="noShowThreshold" :min="0" :max="100" :disabled="isReadonly"
                              placeholder="未签到释放达到多少次" style="width: 220px"
                              @change="syncNoShowRuleJson" />
            </a-form-item>
            <a-form-item label="禁约天数">
              <a-input-number v-model:value="noShowBanDays" :min="0" :max="365" :disabled="isReadonly"
                              placeholder="达到阈值后禁止预约多少天" style="width: 220px"
                              @change="syncNoShowRuleJson" />
            </a-form-item>
            <span class="muted switch-tip">次数或禁约天数填 0 表示不启用爽约限制；按会议开始时间统计未签到释放，保存后最多 60 秒生效</span>
          </a-form>
        </template>

        <!-- 到期提醒节点：到期前 N 天提醒，可配置多个 -->
        <template v-if="editor === 'offsets'">
          <a-divider orientation="left">提醒节点</a-divider>
          <a-form layout="vertical">
            <a-form-item label="到期前提醒（天，输入后回车新增，可多个）">
              <a-select
                v-model:value="offsetTags"
                mode="tags"
                allow-clear
                :disabled="isReadonly"
                placeholder="如 30、7、1"
                @change="syncOffsetsJson"
              />
            </a-form-item>
          </a-form>
        </template>

        <!-- HR 部门：直接选组织节点（含全部下级），不再按名称模糊匹配 -->
        <template v-if="editor === 'orgs'">
          <a-divider orientation="left">HR 部门</a-divider>
          <a-form layout="vertical">
            <a-form-item label="组织节点（含所有下级部门）">
              <a-tree-select
                v-model:value="hrOrgIds"
                :tree-data="orgTreeData"
                tree-checkable
                allow-clear
                show-search
                tree-node-filter-prop="title"
                :loading="receiverLoading"
                :disabled="isReadonly"
                placeholder="选择 HR 部门，保存后按该部门及下级部门发待办"
                max-tag-count="responsive"
                @change="syncHrOrgJson"
              />
            </a-form-item>
          </a-form>
        </template>

        <!-- 城市假期政策：按城市分行维护婚假/产假/陪产假天数 -->
        <template v-if="editor === 'cityPolicy'">
          <a-divider orientation="left">城市假期政策</a-divider>
          <div class="city-policy-body">
            <div class="city-policy-tip muted hint-line">
              婚假的天数与计算方式（自然日 / 工作日）均可配置；产假和陪产假天数固定按自然日（含周末 / 法定节假日），后端强制自然日。
            </div>
            <a-table
              :columns="cityPolicyColumns"
              :data-source="cityPolicyRows"
              row-key="city"
              size="small"
              :pagination="false"
              :bordered="true"
              class="city-policy-table"
            >
              <template #bodyCell="{ column, record, index }">
                <template v-if="column.key === 'city'">
                  <a-select
                    v-if="!isReadonly"
                    v-model:value="record.city"
                    :options="cityOptions"
                    allow-clear
                    show-search
                    option-filter-prop="label"
                    placeholder="选择城市"
                    style="width: 100%"
                    @change="onCityRowChange"
                  />
                  <span v-else>{{ record.city || '—' }}</span>
                </template>
                <template v-else-if="column.key === 'marriageDays'">
                  <a-input-number
                    v-if="!isReadonly"
                    v-model:value="record.marriageDays"
                    :min="0"
                    :max="365"
                    style="width: 100%"
                    placeholder="婚假天数"
                    @change="onCityRowChange"
                  />
                  <span v-else>{{ record.marriageDays ?? '—' }}</span>
                </template>
                <template v-else-if="column.key === 'marriageMode'">
                  <a-select
                    v-if="!isReadonly"
                    v-model:value="record.marriageMode"
                    :options="MARRIAGE_MODE_OPTIONS"
                    style="width: 100%"
                    @change="onCityRowChange"
                  />
                  <span v-else>{{ record.marriageMode === 'WORKDAY' ? '工作日' : '自然日' }}</span>
                </template>
                <template v-else-if="column.key === 'maternityDays'">
                  <a-input-number
                    v-if="!isReadonly"
                    v-model:value="record.maternityDays"
                    :min="0"
                    :max="365"
                    style="width: 100%"
                    placeholder="产假天数"
                    @change="onCityRowChange"
                  />
                  <span v-else>{{ record.maternityDays ?? '—' }}</span>
                </template>
                <template v-else-if="column.key === 'paternityDays'">
                  <a-input-number
                    v-if="!isReadonly"
                    v-model:value="record.paternityDays"
                    :min="0"
                    :max="365"
                    style="width: 100%"
                    placeholder="陪产假天数"
                    @change="onCityRowChange"
                  />
                  <span v-else>{{ record.paternityDays ?? '—' }}</span>
                </template>
                <template v-else-if="column.key === 'op'">
                  <a
                    v-if="!isReadonly"
                    class="danger"
                    @click="removeCityRow(index)"
                  >删除</a>
                  <span v-else class="muted">—</span>
                </template>
              </template>
            </a-table>
            <div v-if="!isReadonly" class="city-policy-actions">
              <a-button type="dashed" block @click="addCityRow">+ 新增城市</a-button>
            </div>
          </div>
        </template>

        <!-- 财务 / 合同域埋点：裸值编辑（业务服务读整个 value，内置兜底；不包一层对象） -->
        <template v-if="editor === 'plain'">
          <a-divider orientation="left">业务规则配置</a-divider>
          <a-alert type="info" show-icon class="unknown-tip"
                   message="值为业务服务直接读取的规则，保存后由 settings 下发（缓存约 60 秒）"
                   description="每个埋点都有独立的结构化表单与内置默认值；埋点只控制行为，不承载主数据、不参与单据外键、不定义审批流。" />
          <a-form layout="vertical">
            <a-form-item v-if="plainKind === 'bool'" :label="current?.name || '开关'">
              <a-switch v-model:checked="plainBool" :disabled="isReadonly"
                        checked-children="开启" un-checked-children="关闭" @change="syncPlainValue" />
              <span class="muted switch-tip">{{ currentCode === CONTRACT_ALLOW_OVER_KEY ? '关闭=超预算拦截（默认），开启=放行并记录超预算留痕' : '默认关闭，按业务服务内置值兜底' }}</span>
            </a-form-item>
            <a-form-item v-else-if="plainKind === 'number'" :label="current?.name || '数值'">
              <a-input-number v-model:value="plainNumber" :min="PLAIN_NUMBER_RANGE[currentCode]?.min ?? 0"
                              :max="PLAIN_NUMBER_RANGE[currentCode]?.max ?? 1000000" :disabled="isReadonly"
                              style="width: 220px" @change="syncPlainValue" />
              <span class="muted switch-tip">取值范围由埋点定义，超限自动收敛；业务服务另有内置兜底值</span>
            </a-form-item>
            <a-form-item v-else-if="plainKind === 'enum'" :label="current?.name || '选项'">
              <a-select v-model:value="plainEnum" :disabled="isReadonly" style="width: 220px"
                        @change="syncPlainValue">
                <a-select-option v-for="opt in PLAIN_ENUM_OPTIONS[currentCode] || []" :key="opt" :value="opt">{{ opt }}</a-select-option>
              </a-select>
              <span class="muted switch-tip">枚举值直接作为埋点值保存，业务服务按此判定行为</span>
            </a-form-item>
            <a-form-item v-else :label="current?.name || 'JSON 配置'">
              <a-textarea v-model:value="plainJson" :rows="6" :disabled="isReadonly"
                          placeholder="请输入合法 JSON（数组或对象）" @blur="syncPlainValue" />
              <span class="muted switch-tip">该配置有固定字段结构，业务服务读取失败时按内置默认值兜底</span>
            </a-form-item>
          </a-form>
        </template>

        <!-- 未登记结构化编辑器的埋点：只读预览，不提供 JSON 编辑入口 -->
        <template v-if="editor === 'unknown'">
          <a-alert
            type="warning"
            show-icon
            class="unknown-tip"
            message="该埋点暂未提供结构化维护表单"
            description="请在 EmbedPointView.vue 的 EDITOR_BY_CODE 中登记编辑类型后再维护；页面不再支持直接编辑 JSON。"
          />
          <pre class="json-preview mono">{{ form.value }}</pre>
        </template>
      </template>

      <template v-if="drawerMode === 'edit'" #footer>
        <div class="drawer-actions">
          <a-button @click="drawerOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submit">保存</a-button>
        </div>
      </template>
    </a-drawer>

    <!-- 历史记录 -->
    <a-drawer v-model:open="historyOpen" title="历史记录" width="760" destroy-on-close>
      <div class="history-body">
        <div v-if="historyLoading || historyRows.length" class="history-table">
          <a-table
            :columns="historyColumns"
            :data-source="pagedHistoryRows"
            :loading="historyLoading"
            row-key="id"
            size="small"
            :pagination="false"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'createdAt'">{{ formatTime(record.createdAt) }}</template>
              <template v-else-if="column.key === 'action'">
                <a @click="openHistoryValue(record)">查看</a>
              </template>
            </template>
          </a-table>
        </div>
        <div v-else class="history-empty">
          <a-empty description="暂无数据" />
        </div>
        <div v-if="historyRows.length" class="history-pager">
          <a-pagination
            v-model:current="historyPage"
            size="small"
            :page-size="HISTORY_PAGE_SIZE"
            :total="historyRows.length"
            show-less-items
          />
        </div>
      </div>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { fetchApps, fetchRoles, type AppBrief, type RoleBrief } from '@/api/approval'
import { listItems } from '@/api/settings'
import {
  fetchEmbedPointHistories,
  fetchEmbedReceiverEmployees,
  fetchEmbedReceiverOrgs,
  listEmbedPoints,
  updateEmbedPoint,
  type EmbedPoint,
  type EmbedPointHistory,
  type EmbedReceiverEmployee,
  type EmbedReceiverOrg
} from '@/api/embed'

/** 试用期超期提醒收件人：结构化维护 */
const RECEIVER_KEY = 'ehr.probation.reminder.receivers'
/** 新开账户默认授权角色：结构化维护 */
const ROLE_KEY = 'ehr.account.default-roles'
/** 新开账户默认开通应用：结构化维护 */
const DEFAULT_APPS_KEY = 'ehr.account.default-apps'
/** 模拟考勤定时任务开关：结构化维护 */
const MOCK_ENABLED_KEY = 'ehr.attendance.mock.enabled'
/** 试用期到期提醒节点：结构化维护 */
const PROBATION_OFFSETS_KEY = 'ehr.reminder.probation-offsets'
/** 合同到期提醒节点：结构化维护 */
const CONTRACT_OFFSETS_KEY = 'ehr.reminder.contract-offsets'
/** HR 部门（组织节点）：结构化维护 */
const HR_ORG_NAME_KEY = 'ehr.notice.hr-org-name'
/** 城市假期政策（婚假 / 产假 / 陪产假按城市配置天数与计算方式）：结构化维护 */
const CITY_POLICY_KEY = 'ehr.leave.city-policy'
/** 社区等级数量上限：数字输入，值为 {"maxLevel": 20} */
const MAX_LEVELS_KEY = 'forum.level.max-levels'
/** 会议室可预约天数上限：数字输入，值为 {"maxDays": 7}，0=仅当天 */
const MEETING_MAX_ADVANCE_KEY = 'meeting.booking.max_advance_days'
/** 会议室未签到释放宽限期：数字输入，值为 {"graceMinutes": 15}，0=开始即释放 */
const MEETING_NO_SHOW_GRACE_KEY = 'meeting.booking.no_show_grace_minutes'
/** 会议室单次预约最长时长：数字输入，值为 {"maxMinutes": 0}，0=不限制 */
const MEETING_MAX_DURATION_KEY = 'meeting.booking.max_duration_minutes'
/** 会议室爽约限制：值为 {"windowDays":90,"threshold":3,"banDays":7} */
const MEETING_NO_SHOW_RULE_KEY = 'meeting.booking.no_show_restriction'
/** 论坛积分抽奖开关：布尔，值为 {"enabled": true/false} */
const FORUM_LOTTERY_KEY = 'forum.lottery.enabled'
/** 论坛礼品兑换开关：布尔，值为 {"enabled": true/false} */
const FORUM_EXCHANGE_KEY = 'forum.exchange.enabled'

/** 财务域埋点：值为裸 JSON（true/false、数字、"ENUM"、数组/对象），由业务服务内置兜底 */
const FINANCE_AUTO_POST_KEY = 'finance.voucher.auto-post'
const FINANCE_AGING_BUCKETS_KEY = 'finance.aging.buckets'
const FINANCE_ALERT_RATIO_KEY = 'finance.budget.alert-ratio'
const FINANCE_CONTROL_MODE_KEY = 'finance.budget.control-mode'
const FINANCE_APPROVAL_AMOUNT_KEY = 'finance.expense.approval-amount'
const FINANCE_CLOSE_DAY_KEY = 'finance.period.close-day'
const FINANCE_WRITEOFF_MODE_KEY = 'finance.receivable.writeoff-mode'
const FINANCE_PERIOD_CHECK_KEY = 'finance.voucher.period-check'
/** 合同域埋点：超预算开关默认 false（fail-closed 拦截）；其余阈值与财务同口径 */
const CONTRACT_ALLOW_OVER_KEY = 'contract.budget.allow-over'
const CONTRACT_PAYMENT_REMIND_KEY = 'contract.payment.remind-days'
const CONTRACT_ACCEPT_REQUIRED_KEY = 'contract.acceptance.required-before-pay'
const CONTRACT_PURCHASE_APPROVAL_KEY = 'contract.purchase.approval-amount'
const CONTRACT_SALES_CREDIT_KEY = 'contract.sales.credit-check'
const CONTRACT_CHANGE_MAX_KEY = 'contract.change.max-times'
const CONTRACT_MATERIAL_PATTERN_KEY = 'contract.material.code-pattern'

/** plain 编辑器：按 key 决定控件类型与取值范围，保存裸 JSON 值（不包一层对象） */
type PlainKind = 'bool' | 'number' | 'enum' | 'json'
const PLAIN_KEYS: Record<string, PlainKind> = {
  [FINANCE_AUTO_POST_KEY]: 'bool',
  [FINANCE_PERIOD_CHECK_KEY]: 'bool',
  [CONTRACT_ALLOW_OVER_KEY]: 'bool',
  [CONTRACT_ACCEPT_REQUIRED_KEY]: 'bool',
  [CONTRACT_SALES_CREDIT_KEY]: 'bool',
  [FINANCE_ALERT_RATIO_KEY]: 'number',
  [FINANCE_APPROVAL_AMOUNT_KEY]: 'number',
  [FINANCE_CLOSE_DAY_KEY]: 'number',
  [CONTRACT_PAYMENT_REMIND_KEY]: 'number',
  [CONTRACT_PURCHASE_APPROVAL_KEY]: 'number',
  [CONTRACT_CHANGE_MAX_KEY]: 'number',
  [FINANCE_CONTROL_MODE_KEY]: 'enum',
  [FINANCE_WRITEOFF_MODE_KEY]: 'enum',
  [FINANCE_AGING_BUCKETS_KEY]: 'json',
  [CONTRACT_MATERIAL_PATTERN_KEY]: 'json'
}
const PLAIN_ENUM_OPTIONS: Record<string, string[]> = {
  [FINANCE_CONTROL_MODE_KEY]: ['REMIND', 'STRICT'],
  [FINANCE_WRITEOFF_MODE_KEY]: ['FIFO', 'MANUAL']
}
const PLAIN_NUMBER_RANGE: Record<string, { min: number; max: number }> = {
  [FINANCE_ALERT_RATIO_KEY]: { min: 1, max: 1000 },
  [FINANCE_APPROVAL_AMOUNT_KEY]: { min: 0, max: 10000000 },
  [FINANCE_CLOSE_DAY_KEY]: { min: 1, max: 28 },
  [CONTRACT_PAYMENT_REMIND_KEY]: { min: 0, max: 365 },
  [CONTRACT_PURCHASE_APPROVAL_KEY]: { min: 0, max: 100000000 },
  [CONTRACT_CHANGE_MAX_KEY]: { min: 0, max: 100 }
}
const COMMON_APP = '__common__'

const store = useUserStore()
const canManage = computed(() => store.can('embed:manage'))

const columns = [
  { title: 'Key', key: 'code', width: 300, ellipsis: true },
  { title: '模块', dataIndex: 'module', key: 'module', width: 140, ellipsis: true },
  { title: '所属应用', key: 'appCode', width: 120, ellipsis: true },
  { title: '描述', dataIndex: 'name', key: 'name', width: 240, ellipsis: true },
  { title: '操作', key: 'action', width: 190 }
]

const historyColumns = [
  { title: '保存时间', key: 'createdAt', width: 170 },
  { title: '操作人', dataIndex: 'operatedName', key: 'operatedName', width: 120, ellipsis: true },
  { title: '操作', key: 'action', width: 100 }
]

/** 城市假期政策表格列 */
const cityPolicyColumns: any[] = [
  { title: '城市', key: 'city', width: 140, ellipsis: true },
  { title: '婚假天数', key: 'marriageDays', width: 110 },
  { title: '婚假计算方式', key: 'marriageMode', width: 170 },
  { title: '产假天数', key: 'maternityDays', width: 110 },
  { title: '陪产假天数', key: 'paternityDays', width: 120 },
  { title: '操作', key: 'op', width: 80, align: 'center' }
]

const rows = ref<EmbedPoint[]>([])
const apps = ref<AppBrief[]>([])
const loading = ref(false)
const keyword = ref('')

/* ---------------- 左侧树 ---------------- */

interface TreeNode {
  key: string
  title: string
  isLeaf?: boolean
  children?: TreeNode[]
  count?: number
}

const selectedApp = ref('')
const selectedModule = ref('')

const appNameMap = computed(() => {
  const map = new Map<string, string>()
  for (const a of apps.value) map.set(a.code, a.name)
  return map
})

function appNameOf(code: string) {
  return appNameMap.value.get(code) || code
}

const appKeyOf = (r: EmbedPoint) => r.appCode || COMMON_APP

const treeData = computed<TreeNode[]>(() => {
  // 与「消息配置」一致：业务模块 → 应用（含暂无埋点的应用）→ 模块，并固定保留「通用」
  const known = new Map<string, string>()
  for (const a of apps.value) known.set(a.code, a.name)
  known.set(COMMON_APP, '通用')

  const appNodes: TreeNode[] = []
  for (const [code, name] of known.entries()) {
    const list = rows.value.filter((r) => appKeyOf(r) === code)
    const modules = new Map<string, number>()
    for (const r of list) {
      const mod = r.module || '未分类'
      modules.set(mod, (modules.get(mod) || 0) + 1)
    }
    const children: TreeNode[] = [...modules.entries()].map(([mod, count]) => ({
      key: `module:${code}::${mod}`,
      title: mod,
      count,
      isLeaf: true
    }))
    children.sort((a, b) => a.title.localeCompare(b.title, 'zh'))
    appNodes.push({ key: `app:${code}`, title: name, count: list.length, children })
  }
  appNodes.sort((a, b) => a.title.localeCompare(b.title, 'zh'))

  return [{ key: 'root', title: '业务模块', children: appNodes }]
})

const navTitleClass = (key: string) =>
  key === 'root'
    ? 'approval-nav-title-root'
    : key.startsWith('module:')
      ? 'approval-nav-title-leaf'
      : 'approval-nav-title-app'

const navCount = (node: any) => (typeof node?.count === 'number' ? node.count : 0)

const expandedKeys = ref<string[]>([])
watch(
  treeData,
  (nodes) => {
    const parents: string[] = []
    const walk = (list: TreeNode[]) =>
      list.forEach((n) => {
        if (n.children?.length) {
          parents.push(n.key)
          walk(n.children)
        }
      })
    walk(nodes)
    const known = new Set(expandedKeys.value)
    const added = parents.filter((k) => !known.has(k))
    if (added.length) expandedKeys.value = [...expandedKeys.value, ...added]
  },
  { immediate: true }
)

function onExpand(keys: (string | number)[]) {
  expandedKeys.value = keys.map(String)
}

const selectedKey = computed(() => {
  if (selectedModule.value) return `module:${selectedApp.value}::${selectedModule.value}`
  if (selectedApp.value) return `app:${selectedApp.value}`
  return 'root'
})

function onSelect(keys: (string | number)[]) {
  const key = String(keys[0] || 'root')
  if (key === 'root' || !key) {
    selectedApp.value = ''
    selectedModule.value = ''
  } else if (key.startsWith('app:')) {
    selectedApp.value = key.slice(4)
    selectedModule.value = ''
  } else {
    const rest = key.slice(7)
    const sep = rest.indexOf('::')
    selectedApp.value = rest.slice(0, sep)
    selectedModule.value = rest.slice(sep + 2)
  }
}

const filteredRows = computed(() => {
  let list = rows.value
  if (selectedApp.value) list = list.filter((r) => appKeyOf(r) === selectedApp.value)
  if (selectedModule.value) list = list.filter((r) => (r.module || '未分类') === selectedModule.value)
  return list
})

/* ---------------- 新增 / 查看 / 编辑 ---------------- */

const drawerOpen = ref(false)
const drawerMode = ref<'view' | 'edit' | 'history'>('view')
const current = ref<EmbedPoint | null>(null)
const saving = ref(false)
const form = reactive({ name: '', value: '' })

/** 当前维护的 key */
const currentCode = computed(() => current.value?.code || '')
/** 查看/历史快照与编辑页同构，仅禁用全部控件 */
const isReadonly = computed(() => drawerMode.value !== 'edit')

/**
 * 每个埋点 key 对应的结构化编辑器。
 * 约定：所有埋点必须登记结构化表单，页面不再提供原始 JSON 编辑入口；
 * 新增埋点时先在此登记 editor（未登记的会提示"暂未提供结构化维护表单"）。
 */
type EmbedEditor =
  | 'receiver'
  | 'roles'
  | 'cityPolicy'
  | 'apps'
  | 'switch'
  | 'offsets'
  | 'orgs'
  | 'number'
  | 'noShowRule'
  | 'plain'
  | 'unknown'
const EDITOR_BY_CODE: Record<string, EmbedEditor> = {
  [RECEIVER_KEY]: 'receiver',
  [ROLE_KEY]: 'roles',
  [CITY_POLICY_KEY]: 'cityPolicy',
  [DEFAULT_APPS_KEY]: 'apps',
  [MOCK_ENABLED_KEY]: 'switch',
  [PROBATION_OFFSETS_KEY]: 'offsets',
  [CONTRACT_OFFSETS_KEY]: 'offsets',
  [HR_ORG_NAME_KEY]: 'orgs',
  [MAX_LEVELS_KEY]: 'number',
  [MEETING_MAX_ADVANCE_KEY]: 'number',
  [MEETING_NO_SHOW_GRACE_KEY]: 'number',
  [MEETING_MAX_DURATION_KEY]: 'number',
  [MEETING_NO_SHOW_RULE_KEY]: 'noShowRule',
  [FORUM_LOTTERY_KEY]: 'switch',
  [FORUM_EXCHANGE_KEY]: 'switch',
  ...Object.fromEntries(Object.keys(PLAIN_KEYS).map(k => [k, 'plain']))
}
const editor = computed<EmbedEditor>(() => EDITOR_BY_CODE[currentCode.value] || 'unknown')

/** 数值埋点按 key 区分 JSON 字段名、缺省值与范围，禁止复用其他埋点的字段名 */
function numberField(code: string) {
  if (code === MEETING_MAX_ADVANCE_KEY) return 'maxDays'
  if (code === MEETING_NO_SHOW_GRACE_KEY) return 'graceMinutes'
  if (code === MEETING_MAX_DURATION_KEY) return 'maxMinutes'
  return 'maxLevel'
}
function numberDefault(code: string) {
  if (code === MEETING_MAX_ADVANCE_KEY) return 7
  if (code === MEETING_NO_SHOW_GRACE_KEY) return 15
  if (code === MEETING_MAX_DURATION_KEY) return 0
  return 20
}
function numberMax(code: string) {
  if (code === MEETING_MAX_DURATION_KEY) return 1440
  if (code === MEETING_MAX_ADVANCE_KEY) return 365
  if (code === MEETING_NO_SHOW_GRACE_KEY) return 240
  return 100
}

/* 结构化编辑：部门 / 人员 / 直属主管，改动后回写 JSON 文本 */
const orgIds = ref<string[]>([])
const accountIds = ref<string[]>([])
const directSupervisor = ref(true)
const roleCodes = ref<string[]>([])
/** 新开账户默认开通应用（appCode 列表） */
const defaultApps = ref<string[]>([])
/** 模拟考勤定时任务开关 */
const mockEnabled = ref(false)
/** 到期提醒节点（天） */
const offsetTags = ref<string[]>([])
/** 数值埋点（如等级上限）：通用数字输入 */
const numberValue = ref<number>(1)
/** 财务/合同埋点：裸值编辑控件，按 key 决定用 bool / number / enum / json */
const plainKind = computed<PlainKind>(() => PLAIN_KEYS[currentCode.value] || 'json')
const plainBool = ref(false)
const plainNumber = ref<number>(0)
const plainEnum = ref('')
const plainJson = ref('')
/** 会议室爽约限制：统计天数 / 触发次数 / 禁约天数 */
const noShowWindowDays = ref<number>(90)
const noShowThreshold = ref<number>(3)
const noShowBanDays = ref<number>(7)
/** HR 部门组织节点（含全部下级） */
const hrOrgIds = ref<string[]>([])
/** 城市假期政策：每行一个城市 */
interface CityPolicyRow {
  city: string
  marriageDays?: number | null
  marriageMode?: 'NATURAL' | 'WORKDAY'
  maternityDays?: number | null
  paternityDays?: number | null
}
const cityPolicyRows = ref<CityPolicyRow[]>([])
const cityOptions = ref<{ value: string; label: string }[]>([])
const MARRIAGE_MODE_OPTIONS = [
  { value: 'NATURAL', label: '自然日（含周末 / 节假日）' },
  { value: 'WORKDAY', label: '工作日（跳过周末 / 节假日）' }
]
/** 全量角色，默认授权角色下拉可选所有角色（仍支持 tags 手动输入） */
const roles = ref<RoleBrief[]>([])
const receiverLoading = ref(false)
const employees = ref<EmbedReceiverEmployee[]>([])
const orgTreeData = ref<Array<{ title: string; value: string; children?: any[] }>>([])

const employeeOptions = computed(() =>
  employees.value.map((e) => ({ value: e.id, label: `${e.name}${e.empNo ? `（${e.empNo}）` : ''}` }))
)

const roleOptions = computed(() =>
  roles.value.map((r) => ({
    value: r.code,
    label: r.appCode ? `${r.name}（${r.code} · ${r.appCode}）` : `${r.name}（${r.code}）`
  }))
)

/** 默认开通应用的可选项：来自应用注册表 */
const appOptions = computed(() => apps.value.map((a) => ({ value: a.code, label: a.name })))

async function loadRoles() {
  try {
    roles.value = (await fetchRoles()) || []
  } catch (e) {
    message.error((e as Error).message || '角色列表加载失败')
  }
}

/** 扁平组织节点组树；叶子不带 children 键，避免显示展开箭头 */
function buildOrgTree(list: EmbedReceiverOrg[]) {
  const map = new Map<string, { title: string; value: string; children?: any[] }>()
  list.forEach((n) => map.set(n.id, { title: n.name, value: n.id, children: [] }))
  const roots: Array<{ title: string; value: string; children?: any[] }> = []
  for (const n of list) {
    const node = map.get(n.id)!
    const parent = n.parentId ? map.get(n.parentId) : undefined
    if (parent) parent.children!.push(node)
    else roots.push(node)
  }
  const walk = (ns: Array<{ children?: any[] }>) =>
    ns.forEach((x) => {
      if (x.children?.length) walk(x.children)
      else delete x.children
    })
  walk(roots)
  return roots
}

async function loadReceivers() {
  receiverLoading.value = true
  try {
    const [emps, orgs] = await Promise.all([fetchEmbedReceiverEmployees(), fetchEmbedReceiverOrgs()])
    employees.value = emps || []
    orgTreeData.value = buildOrgTree(orgs || [])
  } catch {
    // 选人数据加载失败不阻塞维护；保存时仍是合法 JSON
    employees.value = []
    orgTreeData.value = []
  } finally {
    receiverLoading.value = false
  }
}

function parseJson(raw?: string): any {
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

/** JSON → 结构化控件（编辑回显、手动改 JSON 后失焦同步） */
function applyStructured(code: string, raw: string) {
  const parsed = parseJson(raw)
  const obj = parsed || {}
  if (PLAIN_KEYS[code]) {
    const kind = PLAIN_KEYS[code]
    plainBool.value = parsed === true
    plainNumber.value = typeof parsed === 'number' && Number.isFinite(parsed) ? parsed : 0
    plainEnum.value = typeof parsed === 'string' ? parsed : (PLAIN_ENUM_OPTIONS[code]?.[0] || '')
    plainJson.value = raw || ''
    if (kind === 'number' && !plainNumber.value) {
      plainNumber.value = code === FINANCE_ALERT_RATIO_KEY ? 80
        : code === FINANCE_APPROVAL_AMOUNT_KEY ? 5000
        : code === FINANCE_CLOSE_DAY_KEY ? 5
        : code === CONTRACT_PAYMENT_REMIND_KEY ? 7
        : code === CONTRACT_PURCHASE_APPROVAL_KEY ? 100000 : 3
    }
    if (kind === 'enum' && !plainEnum.value) plainEnum.value = PLAIN_ENUM_OPTIONS[code]?.[0] || ''
  } else if (code === RECEIVER_KEY) {
    orgIds.value = Array.isArray(obj.orgIds) ? obj.orgIds.map(String) : []
    accountIds.value = Array.isArray(obj.accountIds) ? obj.accountIds.map(String) : []
    directSupervisor.value = obj.directSupervisor !== false
  } else if (code === ROLE_KEY) {
    roleCodes.value = Array.isArray(obj.roleCodes) ? obj.roleCodes.map(String) : []
  } else if (code === DEFAULT_APPS_KEY) {
    defaultApps.value = Array.isArray(obj.apps) ? obj.apps.map(String) : []
  } else if (code === MOCK_ENABLED_KEY || code === FORUM_LOTTERY_KEY || code === FORUM_EXCHANGE_KEY) {
    mockEnabled.value = obj.enabled === true
  } else if (code === MAX_LEVELS_KEY || code === MEETING_MAX_ADVANCE_KEY
      || code === MEETING_NO_SHOW_GRACE_KEY || code === MEETING_MAX_DURATION_KEY) {
    const n = Number(obj[numberField(code)])
    numberValue.value = Number.isFinite(n) && n >= 0 ? n : numberDefault(code)
  } else if (code === MEETING_NO_SHOW_RULE_KEY) {
    const windowDays = Number(obj.windowDays)
    const threshold = Number(obj.threshold)
    const banDays = Number(obj.banDays)
    noShowWindowDays.value = Number.isFinite(windowDays) && windowDays >= 1 ? windowDays : 90
    noShowThreshold.value = Number.isFinite(threshold) && threshold >= 0 ? threshold : 3
    noShowBanDays.value = Number.isFinite(banDays) && banDays >= 0 ? banDays : 7
  } else if (code === PROBATION_OFFSETS_KEY || code === CONTRACT_OFFSETS_KEY) {
    offsetTags.value = Array.isArray(obj.offsets) ? obj.offsets.map(String) : []
  } else if (code === HR_ORG_NAME_KEY) {
    hrOrgIds.value = Array.isArray(obj.orgIds) ? obj.orgIds.map(String) : []
  } else if (code === CITY_POLICY_KEY) {
    const rows: CityPolicyRow[] = []
    if (obj && typeof obj === 'object') {
      for (const [city, cfg] of Object.entries(obj)) {
        if (!cfg || typeof cfg !== 'object') continue
        const anyCfg = cfg as any
        rows.push({
          city,
          marriageDays: anyCfg.MARRIAGE?.days ?? null,
          marriageMode: anyCfg.MARRIAGE?.dayMode === 'WORKDAY' ? 'WORKDAY' : 'NATURAL',
          maternityDays: anyCfg.MATERNITY?.days ?? null,
          paternityDays: anyCfg.PATERNITY?.days ?? null
        })
      }
    }
    cityPolicyRows.value = rows
  }
}

function syncReceiverJson() {
  form.value = JSON.stringify(
    { orgIds: orgIds.value, accountIds: accountIds.value, directSupervisor: directSupervisor.value },
    null,
    2
  )
}

function syncRolesJson() {
  form.value = JSON.stringify({ roleCodes: roleCodes.value }, null, 2)
}

function syncAppsJson() {
  form.value = JSON.stringify({ apps: defaultApps.value }, null, 2)
}

function syncSwitchJson() {
  form.value = JSON.stringify({ enabled: mockEnabled.value }, null, 2)
}

/** 数值埋点：按 key 写回各自字段，字段名由 numberField 决定 */
function syncNumberJson() {
  const n = Number(numberValue.value)
  if (!Number.isFinite(n) || n < 0) {
    numberValue.value = 0
  }
  form.value = JSON.stringify({ [numberField(currentCode.value)]: Number(numberValue.value) }, null, 2)
}

/** 爽约规则：三个字段整体写回，0 表示不启用限制 */
/** plain 埋点：保存裸 JSON 值（业务服务读的是整个 value，不包一层对象） */
function syncPlainValue() {
  const code = currentCode.value
  const kind = PLAIN_KEYS[code]
  if (!kind) return
  if (kind === 'bool') {
    form.value = JSON.stringify(plainBool.value)
  } else if (kind === 'number') {
    const range = PLAIN_NUMBER_RANGE[code] || { min: 0, max: 1000000 }
    let n = Number(plainNumber.value)
    if (!Number.isFinite(n)) n = range.min
    n = Math.min(range.max, Math.max(range.min, n))
    plainNumber.value = n
    form.value = JSON.stringify(n)
  } else if (kind === 'enum') {
    if (!plainEnum.value) plainEnum.value = PLAIN_ENUM_OPTIONS[code]?.[0] || ''
    form.value = JSON.stringify(plainEnum.value)
  } else {
    form.value = plainJson.value || '{}'
  }
}

function syncNoShowRuleJson() {
  const windowDays = Number(noShowWindowDays.value)
  const threshold = Number(noShowThreshold.value)
  const banDays = Number(noShowBanDays.value)
  if (!Number.isFinite(windowDays) || windowDays < 1) noShowWindowDays.value = 90
  if (!Number.isFinite(threshold) || threshold < 0) noShowThreshold.value = 0
  if (!Number.isFinite(banDays) || banDays < 0) noShowBanDays.value = 0
  form.value = JSON.stringify({
    windowDays: Number(noShowWindowDays.value),
    threshold: Number(noShowThreshold.value),
    banDays: Number(noShowBanDays.value)
  }, null, 2)
}

/** 提醒节点：去重、降序、只保留正整数 */
function syncOffsetsJson() {
  const raw = offsetTags.value
  const nums = [...new Set(
    raw.map((v) => Number(String(v).trim())).filter((n) => Number.isFinite(n) && n > 0)
  )].sort((a, b) => b - a)
  offsetTags.value = nums.map(String)
  if (nums.length !== raw.length) {
    message.warning('提醒天数需为正整数，非法项已忽略')
  }
  form.value = JSON.stringify({ offsets: nums }, null, 2)
}

function syncHrOrgJson() {
  form.value = JSON.stringify({ orgIds: hrOrgIds.value }, null, 2)
}

/** 城市政策表格 → JSON；只保留有至少一项配置的行 */
function syncCityPolicyJson() {
  const out: Record<string, any> = {}
  for (const row of cityPolicyRows.value) {
    if (!row.city) continue
    const cfg: Record<string, any> = {}
    if (row.marriageDays != null && row.marriageDays > 0) {
      cfg.MARRIAGE = { days: row.marriageDays, dayMode: row.marriageMode || 'NATURAL' }
    }
    if (row.maternityDays != null && row.maternityDays > 0) {
      cfg.MATERNITY = { days: row.maternityDays }
    }
    if (row.paternityDays != null && row.paternityDays > 0) {
      cfg.PATERNITY = { days: row.paternityDays }
    }
    if (Object.keys(cfg).length) out[row.city] = cfg
  }
  form.value = JSON.stringify(out, null, 2)
}

function addCityRow() {
  cityPolicyRows.value = [
    ...cityPolicyRows.value,
    { city: '', marriageDays: null, marriageMode: 'NATURAL', maternityDays: null, paternityDays: null }
  ]
}

function removeCityRow(idx: number) {
  cityPolicyRows.value = cityPolicyRows.value.filter((_, i) => i !== idx)
  syncCityPolicyJson()
}

async function loadCityOptions() {
  try {
    const list = await listItems('ehr.job_city', true)
    cityOptions.value = (list || []).map((i: any) => ({ value: i.value, label: i.label || i.value }))
  } catch {
    cityOptions.value = []
  }
}

function onCityRowChange() {
  syncCityPolicyJson()
}

function openView(record: EmbedPoint) {
  current.value = record
  drawerMode.value = 'view'
  form.value = pretty(record.value)
  applyStructured(record.code, record.value)
  drawerOpen.value = true
}

function openEdit(record: EmbedPoint) {
  current.value = record
  drawerMode.value = 'edit'
  form.name = record.name
  form.value = pretty(record.value)
  applyStructured(record.code, record.value)
  drawerOpen.value = true
  if (record.code === CITY_POLICY_KEY) {
    loadCityOptions()
  }
}

async function submit() {
  if (!current.value) return
  const obj = parseJson(form.value)
  if (obj === null) {
    message.error('埋点值必须是合法 JSON')
    return
  }
  saving.value = true
  try {
    // 编辑只允许改描述与 value，key / 应用 / 模块保持只读
    await updateEmbedPoint(current.value.id, { name: form.name, value: form.value })
    message.success('已保存')
    drawerOpen.value = false
    await load()
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/* ---------------- 历史记录 ---------------- */

const historyOpen = ref(false)
const historyLoading = ref(false)
const historyRows = ref<EmbedPointHistory[]>([])
const HISTORY_PAGE_SIZE = 10
const historyPage = ref(1)
/** 分页放在抽屉底部固定展示，表格只渲染当前页 */
const pagedHistoryRows = computed(() =>
  historyRows.value.slice((historyPage.value - 1) * HISTORY_PAGE_SIZE, historyPage.value * HISTORY_PAGE_SIZE)
)

async function openHistory(record: EmbedPoint) {
  current.value = record
  historyPage.value = 1
  historyOpen.value = true
  historyLoading.value = true
  try {
    historyRows.value = await fetchEmbedPointHistories(record.id)
  } catch (e: any) {
    historyRows.value = []
    message.error(e?.message || '历史记录加载失败')
  } finally {
    historyLoading.value = false
  }
}

/* ---------------- 展示工具 ---------------- */

/** 历史快照：带出当时的配置值，按编辑页结构只读展示 */
function openHistoryValue(record: EmbedPointHistory) {
  form.value = pretty(record.value)
  applyStructured(current.value?.code || '', record.value)
  historyOpen.value = false
  drawerMode.value = 'history'
  drawerOpen.value = true
}

function pretty(raw?: string) {
  const obj = parseJson(raw)
  return obj === null ? raw || '' : JSON.stringify(obj, null, 2)
}

function formatTime(v?: string) {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 19)
}

/* ---------------- 加载 ---------------- */

onMounted(async () => {
  await Promise.all([load(), loadApps(), loadReceivers(), loadRoles()])
})

async function loadApps() {
  try {
    apps.value = await fetchApps()
  } catch {
    apps.value = []
  }
}

async function load() {
  loading.value = true
  try {
    rows.value = await listEmbedPoints(keyword.value || undefined)
  } catch (e: any) {
    message.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 左树 + 右表：与消息配置同一套外壳尺寸，保证两页视觉一致 */
.embed-page {
  height: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.embed-layout {
  display: flex;
  flex-direction: row;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.module-tree {
  flex: 0 0 260px;
}

.embed-main :deep(.panel-head) {
  height: 52px;
  padding-top: 0;
  padding-bottom: 0;
  box-sizing: border-box;
}

.tree-body {
  overflow-y: auto;
  overflow-x: hidden;
  /* a-tree 虚拟滚动把树节点渲染成 position:absolute，默认以 body 为包含块，
     节点宽度会被算成整个视口宽并从左侧偏移，撑出 document 级横向滚动条。
     这里建立定位上下文，把 absolute 节点约束回左树面板内。 */
  position: relative;
}

.embed-main {
  flex: 1;
  min-width: 0;
}

/* 历史抽屉：表格占满剩余高度，分页固定在最下方 */
.history-body {
  height: calc(100vh - 104px);
  display: flex;
  flex-direction: column;
}

.history-table {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

/* 无历史数据时让空状态撑满抽屉高度并居中 */
.history-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.history-pager {
  flex: none;
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}

.mono {
  font-family: 'JetBrains Mono', Consolas, Monaco, monospace;
}

.meta {
  margin-bottom: 16px;
}

.json-preview {
  margin: 12px 0 0;
  padding: 12px;
  border-radius: 4px;
  border: 1px solid #eee;
  background: #fafafa;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 360px;
  overflow: auto;
}

.unknown-tip {
  margin-top: 16px;
}

.switch-tip {
  margin-left: 8px;
  font-size: 12px;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* 城市假期政策表格 */
.city-policy-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.city-policy-tip {
  padding: 6px 10px;
  border-radius: 4px;
  background: rgba(128, 128, 128, 0.08);
  font-size: 12px;
  line-height: 1.5;
}

.city-policy-table :deep(td) {
  padding: 4px 8px !important;
  vertical-align: middle;
}

.city-policy-actions {
  margin-top: 4px;
}

.city-policy-table :deep(.ant-input-number),
.city-policy-table :deep(.ant-select) {
  width: 100%;
}
</style>

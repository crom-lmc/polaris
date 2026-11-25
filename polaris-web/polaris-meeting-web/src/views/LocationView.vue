<template>
  <div class="ft-page">
    <section class="ft-head toolbar">
      <a-select v-model:value="city" :options="cityOptions" placeholder="选择城市" style="width: 180px"
                @change="load" />
      <span class="stat-text" v-if="stats">共 {{ stats.buildings }} 栋大楼 · {{ stats.floors }} 个楼层 · {{ stats.rooms }} 间会议室</span>
      <span class="grow" />
      <a-tooltip v-if="!store.can('meeting:room:edit')" title="需要会议室维护权限">
        <a-button disabled>＋ 新增大楼</a-button>
      </a-tooltip>
      <a-button v-else type="primary" :disabled="!city" @click="openBuilding()">＋ 新增大楼</a-button>
      <a-button @click="load">刷新</a-button>
    </section>

    <section class="ft-scroll ft-bottom">
      <a-table class="ft-table location-table" row-key="id" children-column-name="children"
               :columns="columns" :data-source="buildings" :loading="loading"
               :class="{ 'tbl-empty-mode': !loading && !buildings.length }"
               :pagination="false" :scroll="buildings.length ? { x: 860 } : undefined" :expand-icon-column-index="0">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <span class="cell-ellipsis">{{ record.floorNo ? '└ ' + record.name : record.name }}</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === enabledValue ? 'green' : 'default'">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a v-if="!record.floorNo && store.can('meeting:room:edit')" @click="openFloor(record)">＋楼层</a>
              <a v-if="store.can('meeting:room:edit')" @click="openBuilding(record)">编辑</a>
              <template v-if="store.can('meeting:room:edit')">
                <a-tooltip v-if="record.roomCount > 0" title="已维护会议室，停用/删除已锁定">
                  <a class="disabled-link">状态</a>
                </a-tooltip>
                <a v-else @click="toggleStatus(record)">{{ record.status === enabledValue ? '停用' : '恢复' }}</a>
                <a-tooltip v-if="record.roomCount > 0 || (!record.floorNo && hasChildren(record))" title="已维护会议室或楼层，禁止删除">
                  <a class="disabled-link">删除</a>
                </a-tooltip>
                <a v-else class="danger" @click="remove(record)">删除</a>
              </template>
            </a-space>
          </template>
        </template>
        <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="暂无大楼数据" /></div></template>
      </a-table>
    </section>

    <a-modal v-model:open="formOpen" :title="editing ? '编辑' : '新增'" :confirm-loading="saving"
             :width="480" centered @ok="submit">
      <div class="crumb">{{ city }} › {{ form.floorId ? currentParentName : '新增项' }}</div>
      <a-form layout="vertical">
        <a-form-item v-if="form.buildingId" label="所属大楼">
          <a-input :value="currentParentName" readonly />
        </a-form-item>
        <a-form-item v-else label="城市">
          <a-input :value="city" readonly />
        </a-form-item>
        <a-form-item :label="form.buildingId ? '楼层号' : '大楼名称'" required>
          <a-input v-model:value="form.name" :placeholder="form.buildingId ? '如 12F' : '请输入大楼名称'" />
        </a-form-item>
        <a-form-item v-if="!form.buildingId" label="地址">
          <a-input v-model:value="form.address" />
        </a-form-item>
        <a-form-item label="排序"><a-input-number v-model:value="form.sort" :min="0" style="width: 100%" /></a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="form.status" :options="locationStatusOptions" />
        </a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  getDicts, listBuildings, getLocationStats, saveBuilding, updateBuilding, deleteBuilding,
  saveFloor, updateFloor, deleteFloor, type Building, type DictItem
} from '@/api/meeting'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const city = ref<string>()
const dicts = reactive<Record<string, DictItem[]>>({})
const buildings = ref<Building[]>([])
const stats = ref<{ buildings: number; floors: number; rooms: number }>()
const loading = ref(false)
const saving = ref(false)
const formOpen = ref(false)
const editing = ref(false)
const currentParentName = ref('')

const cityOptions = computed(() => (dicts['ehr.job_city'] || []).map(d => ({ label: d.label, value: d.value })))
const locationStatusOptions = computed(() =>
  (dicts['meeting.location_status'] || []).map(d => ({ label: d.label, value: d.value })))
const enabledValue = computed(() =>
  locationStatusOptions.value.find(o => o.label.includes('可用'))?.value || 'ENABLED')
const disabledValue = computed(() =>
  locationStatusOptions.value.find(o => o.label.includes('停用'))?.value || 'DISABLED')
function statusLabel(status: string) {
  return locationStatusOptions.value.find(o => o.value === status)?.label || status
}
const columns = [
  { title: '名称', key: 'name', dataIndex: 'name', width: 220 },
  { title: '城市/地址', key: 'address', dataIndex: 'address', ellipsis: true },
  { title: '状态', key: 'status', dataIndex: 'status', align: 'center', width: 90 },
  { title: '会议室数', key: 'roomCount', dataIndex: 'roomCount', align: 'right', width: 100 },
  { title: '操作', key: 'action', align: 'left', width: 260 }
]

const form = reactive({
  id: '', buildingId: '', floorId: '', name: '', address: '', sort: 0,
  status: 'ENABLED', remark: ''
})

async function load() {
  loading.value = true
  try {
    if (!city.value && cityOptions.value.length) city.value = cityOptions.value[0].value
    if (!city.value) { buildings.value = []; stats.value = undefined; return }
    const [list, s] = await Promise.all([listBuildings(city.value), getLocationStats(city.value)])
    buildings.value = list
    stats.value = s
    form.status = enabledValue.value
  } catch (e: any) { message.error(e?.message || '加载位置数据失败') }
  finally { loading.value = false }
}

function hasChildren(record: Building) {
  return !!record.children && record.children.length > 0
}
function openBuilding(record?: Building) {
  editing.value = !!record
  Object.assign(form, {
    id: record?.id || '', buildingId: '', floorId: '',
    name: record?.name || '', address: record?.address || '', sort: record?.sort || 0,
    status: record?.status || enabledValue.value, remark: record?.remark || ''
  })
  formOpen.value = true
}
function openFloor(building: Building) {
  editing.value = false
  currentParentName.value = building.name
  Object.assign(form, {
    id: '', buildingId: building.id, floorId: building.id, name: '', address: '',
    sort: 0, status: enabledValue.value, remark: ''
  })
  formOpen.value = true
}
async function submit() {
  if (!form.name.trim()) { message.warning('请填写名称'); return }
  saving.value = true
  try {
    if (form.buildingId) {
      const payload = { floorNo: form.name, sort: form.sort, status: form.status, remark: form.remark }
      if (editing.value) await updateFloor(form.id, payload)
      else await saveFloor(form.buildingId, payload)
    } else {
      const payload = { name: form.name, address: form.address, sort: form.sort, status: form.status, remark: form.remark }
      if (editing.value) await updateBuilding(form.id, payload)
      else await saveBuilding(city.value!, payload)
    }
    message.success('保存成功')
    formOpen.value = false
    await load()
  } catch (e: any) { message.error(e?.message || '保存失败') }
  finally { saving.value = false }
}
function toggleStatus(record: Building) {
  const next = record.status === enabledValue.value ? disabledValue.value : enabledValue.value
  Modal.confirm({
    title: `确认${next === disabledValue.value ? '停用' : '恢复'}`, centered: true,
    onOk: async () => {
      try {
        if (record.floorNo) await updateFloor(record.id, { status: next })
        else await updateBuilding(record.id, { status: next })
        message.success('操作成功'); await load()
      } catch (e: any) { message.error(e?.message || '操作失败'); throw e }
    }
  })
}
function remove(record: Building) {
  Modal.confirm({
    title: '确认删除', content: `删除后不可恢复：${record.name}`, centered: true,
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        if (record.floorNo) await deleteFloor(record.id)
        else await deleteBuilding(record.id)
        message.success('删除成功'); await load()
      } catch (e: any) { message.error(e?.message || '删除失败'); throw e }
    }
  })
}
onMounted(async () => {
  try { Object.assign(dicts, await getDicts()) } catch { /* 字典读取失败时城市下拉为空 */ }
  await load()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.grow { flex: 1 1 auto; }
.stat-text { font-size: 12px; color: var(--text-secondary, #8c8c8c); }
.crumb { font-size: 12px; color: var(--text-secondary, #8c8c8c); margin-bottom: 12px; }
.disabled-link, .danger { color: var(--text-secondary, #8c8c8c); cursor: not-allowed; }
.danger { color: #ff4d4f; cursor: pointer; }
.location-table { min-width: 860px; }
</style>

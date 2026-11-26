<template>
  <div class="ft-page">
    <section class="ft-head toolbar">
      <a-select v-model:value="city" :options="cityOptions" placeholder="城市" style="width: 120px" @change="onCityChange" />
      <a-select v-model:value="buildingId" :options="buildingOptions" placeholder="大楼" style="width: 140px"
                allow-clear @change="onBuildingChange" />
      <a-select v-model:value="floorId" :options="floorOptions" placeholder="楼层" style="width: 110px"
                allow-clear @change="load" />
      <a-select v-model:value="roomType" :options="dictOptions('meeting.room_type')" placeholder="类型" style="width: 140px"
                allow-clear @change="load" />
      <a-select v-model:value="deviceCode" :options="dictOptions('meeting.device')" placeholder="设备" style="width: 120px"
                allow-clear @change="load" />
      <a-select v-model:value="status" :options="dictOptions('meeting.room_status')" placeholder="状态" style="width: 110px"
                allow-clear @change="load" />
      <a-input-search v-model:value="keyword" placeholder="房间名/房间号" style="width: 180px" allow-clear @search="load" />
      <a-tooltip v-if="!store.can('meeting:room:edit')" title="需要会议室维护权限">
        <a-button disabled>＋ 新增会议室</a-button>
      </a-tooltip>
      <a-button v-else type="primary" @click="openForm()">＋ 新增会议室</a-button>
      <a-button @click="load">刷新</a-button>
    </section>

    <section class="ft-scroll ft-bottom">
      <a-table class="ft-table rooms-table" row-key="id" :columns="columns" :data-source="rooms"
               :class="{ 'tbl-empty-mode': !loading && !rooms.length }"
               :loading="loading" :pagination="false" :scroll="rooms.length ? { x: 1180 } : undefined">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <a-tooltip :title="record.name"><span class="cell-ellipsis">{{ record.name }}</span></a-tooltip>
          </template>
          <template v-else-if="column.key === 'location'">
            <a-tooltip :title="`${record.city || ''}·${record.buildingName || ''}·${record.floorNo || ''}`">
              <span class="cell-ellipsis">{{ record.city }}·{{ record.buildingName }}·{{ record.floorNo }}</span>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'capacity'">
            {{ record.maxCapacity }} 人<span class="muted">（舒适 {{ record.comfortCapacity }}）</span>
          </template>
          <template v-else-if="column.key === 'devices'">
            <span v-if="record.devices?.length" class="cell-ellipsis">
              {{ record.devices.map((d: Device) => `${deviceLabel(d.deviceCode)}×${d.qty}`).join('、') }}
            </span>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'type'">{{ dictLabel('meeting.room_type', record.roomType) }}</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="roomStatus(record.status) === '可用' ? 'green' : roomStatus(record.status) === '维护' ? 'orange' : 'default'">
              {{ dictLabel('meeting.room_status', roomStatus(record.status)) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a v-if="store.can('meeting:room:edit')" @click="openForm(record)">编辑</a>
              <template v-if="store.can('meeting:room:edit')">
                <a v-if="roomStatus(record.status) !== '可用'" @click="setStatus(record.id, '可用')">恢复可用</a>
                <a v-if="roomStatus(record.status) === '可用'" @click="setStatus(record.id, '维护')">维护</a>
                <a v-if="roomStatus(record.status) === '可用'" @click="setStatus(record.id, '停用')">停用</a>
                <a class="danger" @click="remove(record)">删除</a>
              </template>
            </a-space>
          </template>
        </template>
        <template #emptyText><div class="tbl-empty"><a-empty v-if="!loading" description="暂无会议室" /></div></template>
      </a-table>
    </section>

    <a-drawer v-model:open="formOpen" :title="editing ? '编辑会议室' : '新增会议室'" :width="720"
              placement="right" :body-style="{ paddingBottom: 80 }">
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12"><a-form-item label="城市"><a-input :value="city" readonly /></a-form-item></a-col>
          <a-col :span="12">
            <a-form-item label="所属楼层" required>
              <a-select v-model:value="form.floorId" :options="floorOptions" placeholder="选择楼层" />
            </a-form-item>
          </a-col>
          <a-col :span="12"><a-form-item label="会议室名称" required><a-input v-model:value="form.name" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="房间号"><a-input v-model:value="form.roomNo" /></a-form-item></a-col>
          <a-col :span="8">
            <a-form-item label="类型"><a-select v-model:value="form.roomType" :options="dictOptions('meeting.room_type')" allow-clear /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="容量档位"><a-select v-model:value="form.capacityTier" :options="dictOptions('meeting.room_capacity')" allow-clear /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="座位布局"><a-select v-model:value="form.layout" :options="dictOptions('meeting.seat_layout')" allow-clear /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="硬性上限" required><a-input-number v-model:value="form.maxCapacity" :min="1" style="width: 100%" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="舒适人数"><a-input-number v-model:value="form.comfortCapacity" :min="0" style="width: 100%" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态"><a-select v-model:value="form.status" :options="dictOptions('meeting.room_status')" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="审批预留">
              <a-switch v-model:checked="form.needApproval" disabled /><span class="muted"> 本期不启用</span>
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="设备清单">
          <div v-for="(d, i) in form.devices" :key="i" class="device-row">
            <a-select v-model:value="d.deviceCode" :options="dictOptions('meeting.device')" placeholder="设备类型" style="width: 180px" />
            <a-input-number v-model:value="d.qty" :min="1" placeholder="数量" style="width: 100px" />
            <a-input v-model:value="d.remark" placeholder="备注" style="flex: 1" />
            <a @click="form.devices.splice(i, 1)">移除</a>
          </div>
          <a-button size="small" @click="form.devices.push({ deviceCode: '', qty: 1, remark: '' })">＋ 添加设备</a-button>
        </a-form-item>
        <a-form-item label="房间图片">
          <a-upload :show-upload-list="false" :custom-request="onUpload" accept="image/*">
            <a-button :loading="uploading"><upload-outlined /> 上传图片</a-button>
          </a-upload>
          <div class="thumbs">
            <a-image v-for="(u, i) in form.imageUrls" :key="i" :src="u" :width="76" />
          </div>
        </a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button :disabled="saving" @click="formOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submit">确定</a-button>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { UploadOutlined } from '@ant-design/icons-vue'
import {
  getDicts, getLocationTree, listRooms, saveRoom, updateRoom, deleteRoom, changeRoomStatus,
  uploadRoomImage, type Building, type Device, type DictItem, type Room
} from '@/api/meeting'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const dicts = reactive<Record<string, DictItem[]>>({})
const tree = ref<Building[]>([])
const city = ref<string>()
const buildingId = ref<string>()
const floorId = ref<string>()
const roomType = ref<string>()
const deviceCode = ref<string>()
const status = ref<string>()
const keyword = ref('')
const rooms = ref<Room[]>([])
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const formOpen = ref(false)
const editing = ref(false)
const editingId = ref('')

const cityOptions = computed(() => (dicts['ehr.job_city'] || []).map(d => ({ label: d.label, value: d.value })))
const buildingOptions = computed(() => tree.value.map(b => ({ label: b.name, value: b.id })))
const floorOptions = computed(() => {
  const list = buildingId.value
    ? tree.value.filter(b => b.id === buildingId.value).flatMap(b => b.children || [])
    : tree.value.flatMap(b => b.children || [])
  return list.map(f => ({ label: f.floorNo || '', value: f.id }))
})
function dictOptions(type: string) { return (dicts[type] || []).map(d => ({ label: d.label, value: d.value })) }
function dictLabel(type: string, value: string) { return dictOptions(type).find(o => o.value === value)?.label || value }
function deviceLabel(code: string) { return dictLabel('meeting.device', code) }
/** 状态值按字典 value 使用中文；兼容历史上误写入的英文编码 */
const EN_STATUS: Record<string, string> = { AVAILABLE: '可用', MAINTAIN: '维护', DISABLED: '停用' }
function roomStatus(status: string) { return EN_STATUS[status] || status || '可用' }

const columns = [
  { title: '会议室', key: 'name', dataIndex: 'name', width: 180 },
  { title: '位置', key: 'location', width: 200 },
  { title: '类型', key: 'type', width: 120 },
  { title: '容量', key: 'capacity', width: 160 },
  { title: '设备', key: 'devices', width: 200 },
  { title: '状态', key: 'status', align: 'center', width: 90 },
  { title: '操作', key: 'action', align: 'left', width: 240 }
]

const form = reactive({
  floorId: '', name: '', roomNo: '', roomType: '', capacityTier: '', maxCapacity: 1,
  comfortCapacity: 0, layout: '', status: '可用', needApproval: false,
  remark: '', fileIds: [] as string[], imageUrls: [] as string[], devices: [] as Device[]
})
/** 后端 fileIds 是逗号分隔字符串，提交 DTO 却是 List<String>；统一归一为数组，避免把字符串原样提交导致 Jackson 解析失败 */
function fileIdsOf(value?: string[] | string): string[] {
  if (Array.isArray(value)) return value.filter(Boolean)
  if (!value) return []
  return String(value).split(',').map(s => s.trim()).filter(Boolean)
}

async function loadTree() {
  if (!city.value) { tree.value = []; return }
  tree.value = await getLocationTree(city.value)
}
function onCityChange() {
  buildingId.value = undefined
  floorId.value = undefined
  void loadTree().then(load)
}
function onBuildingChange() { floorId.value = undefined; void load() }

async function load() {
  loading.value = true
  try {
    rooms.value = await listRooms({
      city: city.value, buildingId: buildingId.value, floorId: floorId.value,
      roomType: roomType.value, deviceCode: deviceCode.value, status: status.value,
      keyword: keyword.value || undefined
    })
  } catch (e: any) { message.error(e?.message || '加载会议室失败') }
  finally { loading.value = false }
}

function openForm(record?: Room) {
  editing.value = !!record
  editingId.value = record?.id || ''
  Object.assign(form, {
    floorId: record?.floorId || '', name: record?.name || '', roomNo: record?.roomNo || '',
    roomType: record?.roomType || '', capacityTier: record?.capacityTier || '',
    maxCapacity: record?.maxCapacity || 1, comfortCapacity: record?.comfortCapacity || 0,
    layout: record?.layout || '', status: roomStatus(record?.status || '可用'),
    needApproval: !!record?.needApproval, remark: record?.remark || '',
    fileIds: fileIdsOf(record?.fileIds),
    imageUrls: record?.roomImages || [],
    devices: (record?.devices || []).map(d => ({ deviceCode: d.deviceCode, qty: d.qty, remark: d.remark || '' }))
  })
  formOpen.value = true
}

async function onUpload(option: any) {
  uploading.value = true
  try {
    const res = await uploadRoomImage(option.file as File)
    form.fileIds.push(res.fileId)
    if (res.url) form.imageUrls.push(res.url)
    option.onSuccess?.({} as never)
    message.success('图片已上传')
  } catch (e: any) {
    option.onError?.(e)
    message.error(e?.message || '上传失败')
  } finally { uploading.value = false }
}

async function submit() {
  if (!form.floorId || !form.name.trim()) { message.warning('请选择楼层并填写会议室名称'); return }
  saving.value = true
  try {
    const payload: Partial<Room> = {
      floorId: form.floorId, name: form.name, roomNo: form.roomNo, roomType: form.roomType,
      capacityTier: form.capacityTier, maxCapacity: form.maxCapacity, comfortCapacity: form.comfortCapacity,
      layout: form.layout, status: form.status, needApproval: form.needApproval, remark: form.remark,
      fileIds: fileIdsOf(form.fileIds), devices: form.devices.filter(d => !!d.deviceCode)
    }
    if (editing.value) await updateRoom(editingId.value, payload)
    else await saveRoom(payload)
    message.success('保存成功')
    formOpen.value = false
    await load()
  } catch (e: any) { message.error(e?.message || '保存失败') }
  finally { saving.value = false }
}
function setStatus(id: string, next: string) {
  changeRoomStatus(id, next).then(() => { message.success('状态已更新'); return load() })
    .catch((e: any) => message.error(e?.message || '操作失败'))
}
function remove(record: Room) {
  Modal.confirm({
    title: '确认删除会议室', content: record.name, centered: true, okButtonProps: { danger: true },
    onOk: async () => {
      try { await deleteRoom(record.id); message.success('删除成功'); await load() }
      catch (e: any) { message.error(e?.message || '删除失败'); throw e }
    }
  })
}
onMounted(async () => {
  try { Object.assign(dicts, await getDicts()) } catch { /* ignore */ }
  if (!city.value && cityOptions.value.length) city.value = cityOptions.value[0].value
  await loadTree()
  await load()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.rooms-table { min-width: 1180px; }
.muted { color: var(--text-secondary, #8c8c8c); font-size: 11px; }
.danger { color: #ff4d4f; }
.device-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.thumbs { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.drawer-footer { display: flex; justify-content: flex-end; gap: 8px; }
</style>

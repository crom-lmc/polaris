import request from './request'

/** 当前登录者可访问的应用详情（含 name），用于取本应用登记名 */
export interface MyAppInfo { code: string; name: string }
export const fetchMyApps = () => request.get<never, MyAppInfo[]>('/api/accounts/me/apps')

export const SSO_AUTHORIZE_URL = import.meta.env.VITE_SSO_ORIGIN ||
  (import.meta.env.DEV ? 'http://localhost:5173' : 'https://polaris-sso.laimengchao.site')

export function callbackUrl(): string {
  return window.location.origin + '/callback'
}

export function gotoAuthorize(redirect: string) {
  const state = Math.random().toString(36).slice(2) + Date.now().toString(36)
  try {
    sessionStorage.setItem('meeting_oauth_state', state)
    sessionStorage.setItem('meeting_oauth_redirect', redirect)
  } catch { /* ignore */ }
  const url = `${SSO_AUTHORIZE_URL}/authorize?response_type=code` +
    `&client_id=meeting&redirect_uri=${encodeURIComponent(callbackUrl())}` +
    `&state=${encodeURIComponent(state)}`
  window.location.replace(url)
}

export function consumeState(): string | null {
  try {
    const s = sessionStorage.getItem('meeting_oauth_state')
    sessionStorage.removeItem('meeting_oauth_state')
    return s
  } catch { return null }
}

export function takeRedirect(): string {
  try {
    const r = sessionStorage.getItem('meeting_oauth_redirect') || '/overview'
    sessionStorage.removeItem('meeting_oauth_redirect')
    return r
  } catch { return '/overview' }
}

/** OAuth2 授权码换 meeting 用户令牌（appSecret 在 meeting 后端） */
export function exchangeCode(code: string, redirectUri: string) {
  return request.post<never, { accessToken: string; refreshToken: string }>(
    '/api/meetingroom/sso/oauth/exchange', { code, redirectUri })
}

export function logoutSso(refreshToken?: string) {
  return request.post<never, void>('/api/auth/logout', { refreshToken })
}

export interface MeInfo {
  accountId: string
  username: string
  name: string
  city: string
  superAdmin: boolean
  appCode: string
  permissions: string[]
}
export const fetchMe = () => request.get<never, MeInfo>('/api/meetingroom/me')

export interface DictItem { value: string; label: string; sort: number }
export type DictMap = Record<string, DictItem[]>
export const getDicts = () => request.get<never, DictMap>('/api/meetingroom/dicts')
export interface BookingConfig {
  maxDays: number
  /** 未签到释放宽限期（分钟），0=开始即释放 */
  graceMinutes: number
  /** 单次预约最长时长（分钟），0=不限制 */
  maxMinutes: number
  /** 爽约规则：近 windowDays 天内 threshold 次未签到，禁约 banDays 天 */
  noShowWindowDays: number
  noShowThreshold: number
  noShowBanDays: number
  /** 当前账号在统计窗口内的爽约次数与禁约截止时间 */
  noShowCount: number
  bannedUntil?: string
}
export const getBookingConfig = () => request.get<never, BookingConfig>('/api/meetingroom/config/booking')
export const searchEmployees = (keyword: string) =>
  request.get<never, Employee[]>('/api/meetingroom/employees/search', { params: { keyword, limit: 50 } })
/** 按账号 ID 批量取姓名，回显会议参会人用 */
export const getEmployeeNames = (ids: string[]) =>
  request.get<never, Record<string, string>>('/api/meetingroom/employees/names', { params: { ids: ids.join(',') } })

export interface Overview {
  date: string
  /** 所选日期的全平台有效预约数 */
  meetingCount: number
  toCheckinCount: number
  inProgressCount: number
  availableRooms: number
  /** 日期过滤最大可往后选择的天数（埋点 meeting.booking.max_advance_days） */
  maxDays: number
  calendar: Record<string, number>
  agenda: Booking[]
}
export const getOverview = (date?: string, month?: string) =>
  request.get<never, Overview>('/api/meetingroom/overview', { params: { date, month } })

export interface Slot {
  start: string
  end: string
  state: string
  title?: string
  creatorName?: string
  bookingId?: string
}
export interface RoomSchedule {
  roomId: string; name: string; status: string; date: string
  maxCapacity: number; comfortCapacity: number; slots: Slot[]
  devices?: string[]
}
export interface Schedule { date: string; rooms: RoomSchedule[] }
export const getSchedule = (params: { date: string; city?: string; roomId?: string }) =>
  request.get<never, Schedule>('/api/meetingroom/schedule', { params })

export interface Building {
  id: string
  name: string
  city: string
  /** 楼层行才有：楼层号与所属大楼 */
  floorNo?: string
  buildingId?: string
  address?: string
  sort: number
  status: string
  remark?: string
  roomCount: number
  children?: Floor[]
}
export interface Floor {
  id: string
  buildingId: string
  floorNo: string
  name: string
  sort: number
  status: string
  remark?: string
  roomCount: number
}
export const listBuildings = (city?: string) =>
  request.get<never, Building[]>('/api/meetingroom/buildings', { params: { city } })
export const getLocationStats = (city?: string) =>
  request.get<never, { buildings: number; floors: number; rooms: number }>('/api/meetingroom/locations/stats', { params: { city } })
export const saveBuilding = (city: string, data: Partial<Building>) =>
  request.post<never, Building>('/api/meetingroom/buildings', data, { params: { city } })
export const updateBuilding = (id: string, data: Partial<Building>) =>
  request.put<never, void>(`/api/meetingroom/buildings/${id}`, data)
export const deleteBuilding = (id: string) => request.delete<never, void>(`/api/meetingroom/buildings/${id}`)
export const saveFloor = (buildingId: string, data: Partial<Floor>) =>
  request.post<never, Floor>('/api/meetingroom/floors', data, { params: { buildingId } })
export const updateFloor = (id: string, data: Partial<Floor>) =>
  request.put<never, void>(`/api/meetingroom/floors/${id}`, data)
export const deleteFloor = (id: string) => request.delete<never, void>(`/api/meetingroom/floors/${id}`)
export const getLocationTree = (city?: string) =>
  request.get<never, Building[]>('/api/meetingroom/location-tree', { params: { city } })

export interface Device { id?: string; deviceCode: string; qty: number; remark?: string }
export interface Room {
  id: string
  floorId: string
  name: string
  roomNo: string
  roomType: string
  capacityTier: string
  maxCapacity: number
  comfortCapacity: number
  layout: string
  fileIds?: string[]
  status: string
  needApproval?: boolean
  remark?: string
  city?: string
  buildingName?: string
  floorNo?: string
  devices?: Device[]
  roomImages?: string[]
}
export const listRooms = (params: {
  city?: string; buildingId?: string; floorId?: string; roomType?: string
  capacityTier?: string; status?: string; deviceCode?: string; keyword?: string
}) => request.get<never, Room[]>('/api/meetingroom/rooms', { params })
export const getRoom = (id: string) => request.get<never, Room>(`/api/meetingroom/rooms/${id}`)
export const saveRoom = (data: Partial<Room>) => request.post<never, Room>('/api/meetingroom/rooms', data)
export const updateRoom = (id: string, data: Partial<Room>) => request.put<never, void>(`/api/meetingroom/rooms/${id}`, data)
export const deleteRoom = (id: string) => request.delete<never, void>(`/api/meetingroom/rooms/${id}`)
export const changeRoomStatus = (id: string, status: string) =>
  request.post<never, void>(`/api/meetingroom/rooms/${id}/status`, { status })
export const uploadRoomImage = (file: File) => new Promise<{ fileId: string; url: string }>((resolve, reject) => {
  const reader = new FileReader()
  reader.onload = () => {
    const content = String(reader.result || '').split(',')[1] || ''
    request.post<never, { fileId: string; url: string }>('/api/meetingroom/files/upload', {
      originalName: file.name, contentType: file.type, content
    }).then(resolve).catch(reject)
  }
  reader.onerror = () => reject(new Error('读取文件失败'))
  reader.readAsDataURL(file)
})

export interface Booking {
  id: string
  bookingNo: string
  roomId: string
  roomName?: string
  location?: string
  title: string
  startTime: string
  endTime: string
  attendeeCount: number
  status: string
  checkinAt?: string
  releaseReason?: string
  city: string
  creatorId: string
  creatorName?: string
  /** 概览视角：CREATOR=本人创建，INVITEE=被邀请 */
  relation?: 'CREATOR' | 'INVITEE'
  attendeeIds?: string[]
  remark?: string
  createdAt: string
}
export interface BookingPage { list: Booking[]; total: number }
export const listBookings = (params: {
  tab?: string; city?: string; status?: string; keyword?: string
  from?: string; to?: string; page: number; size: number
}) => request.get<never, BookingPage>('/api/meetingroom/bookings', { params })
export const getBookingCounts = (params?: {
  city?: string; status?: string; keyword?: string; from?: string; to?: string
}) => request.get<never, Record<string, number>>('/api/meetingroom/bookings/counts', { params })
export const getBooking = (id: string) => request.get<never, Booking>(`/api/meetingroom/bookings/${id}`)
export interface BookingSave {
  roomId: string
  title: string
  startTime: string
  endTime: string
  attendeeCount: number
  attendeeIds: string[]
  remark?: string
}
export const createBooking = (data: BookingSave) => request.post<never, Booking>('/api/meetingroom/bookings', data)
export const updateBooking = (id: string, data: BookingSave) =>
  request.put<never, void>(`/api/meetingroom/bookings/${id}`, data)
export const checkinBooking = (id: string) => request.post<never, void>(`/api/meetingroom/bookings/${id}/checkin`)
export const extendBooking = (id: string, endTime: string) =>
  request.post<never, void>(`/api/meetingroom/bookings/${id}/extend`, { endTime })
export const cancelBooking = (id: string) => request.post<never, void>(`/api/meetingroom/bookings/${id}/cancel`)
export const releaseBooking = (id: string) => request.post<never, void>(`/api/meetingroom/bookings/${id}/release`)

export interface Employee { accountId: string; name: string; empNo: string }

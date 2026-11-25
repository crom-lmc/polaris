import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

/**
 * 后台应用侧栏折叠状态（响应式）
 *
 * - 首屏即按视口宽度决定，避免窄屏先展开再收起的闪烁
 * - 视口跨越断点时自动切换
 * - 窄屏下点击菜单项完成导航、点击侧栏外区域都会自动收起抽屉
 *
 * 需配合 _shared/styles/responsive.css 使用。
 */
export function useResponsiveCollapsed(query = '(max-width: 1024px)'): Ref<boolean> {
  const collapsed = ref<boolean>(
    typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia(query).matches
  )
  let mq: MediaQueryList | null = null

  const onChange = (e: MediaQueryListEvent | MediaQueryList) => {
    collapsed.value = e.matches
  }

  const onDocClick = (e: MouseEvent) => {
    if (!mq?.matches || collapsed.value) return
    const el = e.target as HTMLElement | null
    if (el?.closest('.menu-toggle') || el?.closest('.ac-panel')) return
    if (el?.closest('.sider')) {
      // 点击侧栏里的菜单项视为导航完成，收起抽屉；展开子菜单不收起
      if (el.closest('.ant-menu-item')) collapsed.value = true
      return
    }
    collapsed.value = true
  }

  onMounted(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return
    mq = window.matchMedia(query)
    collapsed.value = mq.matches
    if (mq.addEventListener) mq.addEventListener('change', onChange)
    else mq.addListener(onChange)
    document.addEventListener('click', onDocClick)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('click', onDocClick)
    if (!mq) return
    if (mq.removeEventListener) mq.removeEventListener('change', onChange)
    else mq.removeListener(onChange)
  })

  return collapsed
}

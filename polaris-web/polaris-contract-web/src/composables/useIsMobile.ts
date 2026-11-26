import { onBeforeUnmount, onMounted, ref } from 'vue'

/** 窄屏判断（Pad/手机抽屉宽度用），监听器在卸载时成对清理。 */
export function useIsMobile(breakpoint = 768) {
  const isMobile = ref(false)
  let mq: MediaQueryList | null = null
  const sync = () => { isMobile.value = !!mq?.matches }
  onMounted(() => {
    mq = window.matchMedia(`(max-width: ${breakpoint - 1}px)`)
    sync()
    mq.addEventListener('change', sync)
  })
  onBeforeUnmount(() => mq?.removeEventListener('change', sync))
  return isMobile
}

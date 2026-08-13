// 외부 API 는 전부 캐시 + 폴백. 하나가 죽어도 화면이 안 죽어야 한다. (API계약 §0)

const store = new Map()

/**
 * @param key    캐시 키
 * @param ttlMs  유효기간
 * @param loader 실패하면 throw 하는 비동기 로더
 * @param fallback 로더가 실패했고 캐시도 없을 때 쓸 값(함수 또는 값)
 */
export async function cached(key, ttlMs, loader, fallback) {
  const now = Date.now()
  const hit = store.get(key)
  if (hit && now < hit.expires) return hit.value

  try {
    const value = await loader()
    store.set(key, { value, expires: now + ttlMs, stale: value })
    return value
  } catch (err) {
    // 만료된 캐시라도 있으면 그것을 쓴다 (폴백보다 낫다)
    if (hit) {
      return { ...hit.value, isLive: false, staleReason: err.message }
    }
    const fb = typeof fallback === 'function' ? fallback() : fallback
    return { ...fb, isLive: false, fallbackReason: err.message }
  }
}

export function invalidate(key) {
  store.delete(key)
}

/** 타임아웃이 붙은 fetch — 심사 중 외부 API 가 느려도 화면이 멈추면 안 된다 */
export async function fetchWithTimeout(url, opts = {}, timeoutMs = 6000) {
  const ac = new AbortController()
  const t = setTimeout(() => ac.abort(), timeoutMs)
  try {
    const r = await fetch(url, { ...opts, signal: ac.signal })
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    return r
  } finally {
    clearTimeout(t)
  }
}

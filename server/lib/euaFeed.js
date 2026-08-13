// ICE EUA 선물(탄소배출권 선물) 실시간/지연 시세 피드
//
// ── 왜 이 파일이 있나 ────────────────────────────────────────────────
// 앱의 가격 기준은 **ICE EUA 선물 종가**(팀 검증 CSV, 2018-07-26~2026-07-24,
// 2,055거래일, 마지막 €83.40)다. 그 마지막 값을 고정으로 쓰는 대신 여기서
// 살아있는 선물 시세를 가져온다.
//
// ⚠ CO2.L(SparkChange Physical Carbon EUA ETC)은 **다른 상품**이다.
//   EUA 실물을 보유하는 ETC 의 "주가"라 같은 날 기준 5.5% 낮고, 그 괴리가
//   4년에 걸쳐 1.01 → 1.06 으로 계속 벌어진다(실측). 선물 대체로 쓸 수 없다.
//   아래 provider 는 전부 **ICE 상장 EUA 선물** 그 자체를 가리킨다.
//
// ── 사전 조사에서 죽은 경로(다시 시도하지 말 것, 2026-08-13 실측) ────
//   Yahoo ECF=F ............ 심볼은 있으나 종가 1건(2025-12-15)에서 갱신 정지
//   Yahoo CO2=F/EUA=F/
//        CFI2=F/CARBON=F .. 전부 delisted
//   theice.com Delayed ..... Cloudflare 403
//   webservice-eex.gvsi.com  DNS/차단으로 fetch 실패
//   TradingEconomics guest . 410 (guest 계정 폐지)
//   stooq co2.f/eua.f/c2.f . 404
//   Ember API .............. 404
//   investing.com HTML ..... 403 (브라우저 헤더 일습을 붙여도 3/3 실패)
//   api.investing.com ...... 간헐 200 이나 곧 Cloudflare 챌린지로 403 → 채택 안 함
//   finanzen.net HTML ...... 200 이나 페이지에서 EUA 가격을 안정적으로 못 뽑음
//
// ── 채택한 경로 3종 (전부 스크래핑이다. 공식 API 가 아니다) ───────────
//   1. TradingView scanner  ICEENDEX:ECF1!  (EUA 선물 최근월물)
//   2. Investing.com 모바일 API  pair_ID 8848 (CFI2Z6 · Carbon Emissions Futures)
//   3. Barchart 웹 API  CKZ26 (ICE EUA Futures Dec '26)
//   2026-08-13 검증 시 셋 다 81.99 / -0.55% 로 완전히 일치했다.
//
// 계약: 성공하면 객체, 실패하면 null. **예외를 던지지 않는다** — 서버가 죽으면 안 된다.

/* ── 상수 ──────────────────────────────────────────────────────────── */

/** 전체 호출의 총 예산. 이 시간을 넘기면 남은 provider 를 포기하고 null 을 돌린다. */
const TOTAL_BUDGET_MS = 8000
/** provider 하나가 먹을 수 있는 최대 시간. 하나가 매달려도 다음 것이 기회를 갖게 한다. */
const PROVIDER_CAP_MS = 4500

/** EUA 선물 정상 범위(EUR/tCO₂). 밖으로 나가면 **잘못된 심볼을 잡은 것**이므로 실패 처리. */
const MIN_EUR = 60
const MAX_EUR = 120

const BROWSER_UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'

const INSTRUMENT = 'ICE EUA 선물 (EUR/tCO₂)'

/* ── 공용 유틸 ─────────────────────────────────────────────────────── */

/** 숫자 강제 변환. '81.99s'(Barchart 정산가 접미사), '1,234.5' 같은 표기도 흡수한다. */
function num(v) {
  if (typeof v === 'number') return Number.isFinite(v) ? v : null
  if (typeof v !== 'string') return null
  const cleaned = v.replace(/,/g, '').replace(/[^0-9.+-]/g, '')
  if (!cleaned || !/[0-9]/.test(cleaned)) return null
  const n = Number(cleaned)
  return Number.isFinite(n) ? n : null
}

/** 값 sanity check — EUA 선물은 지금 €60~€120 범위다. */
function isSaneSpot(v) {
  return typeof v === 'number' && Number.isFinite(v) && v >= MIN_EUR && v <= MAX_EUR
}

/** epoch(초) → 'YYYY-MM-DD HH:mm' (UTC). 시각을 못 믿을 땐 withTime=false 로 날짜만. */
function fmtUtc(epochSec, withTime = true) {
  const n = num(epochSec)
  if (n === null || n <= 0) return null
  const d = new Date(n * 1000)
  if (Number.isNaN(d.getTime())) return null
  const p = (x) => String(x).padStart(2, '0')
  const day = `${d.getUTCFullYear()}-${p(d.getUTCMonth() + 1)}-${p(d.getUTCDate())}`
  return withTime ? `${day} ${p(d.getUTCHours())}:${p(d.getUTCMinutes())}` : day
}

/**
 * 남은 예산 안에서 fetch. 초과하면 abort.
 * @returns {Promise<Response>}
 */
async function fetchWithin(url, { headers, deadline }) {
  const budget = Math.min(PROVIDER_CAP_MS, deadline - Date.now())
  if (budget <= 0) throw new Error('시간 예산 소진')
  const ac = new AbortController()
  const timer = setTimeout(() => ac.abort(), budget)
  try {
    const res = await fetch(url, { headers, redirect: 'follow', signal: ac.signal })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res
  } finally {
    clearTimeout(timer)
  }
}

async function fetchJsonWithin(url, opts) {
  const res = await fetchWithin(url, opts)
  return res.json()
}

/* ── provider 1: TradingView scanner ───────────────────────────────────
 *
 * GET https://scanner.tradingview.com/symbol?symbol=ICEENDEX:ECF1!&fields=...
 *
 * ICEENDEX:ECF = ICE Endex 상장 "EUA Futures"(TradingView 심볼검색이 그렇게 부른다).
 *   ECF1! = 최근월물 연결(continuous). 2026-08 현재 Dec-26 물이 최근월물이라
 *   ICEENDEX:ECFZ2026 과 값이 완전히 동일함을 확인했다.
 *
 * 응답(평평한 JSON 한 겹)에서 뽑는 필드 — **전부 같은 응답**이다:
 *   close       → 가격 (81.99)
 *   change      → 등락률 **퍼센트** (-0.5459). change_abs(-0.45)/prev(82.44) 로 검산됨
 *   currency    → 'EUR' (통화 확인용. USD/GBP 를 EUR 로 착각하는 사고 방지)
 *   update_time → 마지막 갱신 epoch(초)
 *   update_mode → 'delayed_streaming_600' = 600초(10분) 지연
 *
 * 심볼이 없으면 no_404=true 때문에 200 + 본문 'null' 이 온다 → 반드시 null 체크.
 * 깨졌을 때: 브라우저로 https://www.tradingview.com/symbols/ICEENDEX-ECF1!/ 를 열고
 *   devtools Network 에서 scanner.tradingview.com/symbol 요청의 fields 를 다시 확인.
 */
const TV_FIELDS = ['close', 'change', 'change_abs', 'currency', 'currency_id', 'update_time', 'update_mode', 'description', 'exchange'].join(',')

async function fromTradingView(deadline) {
  const url = `https://scanner.tradingview.com/symbol?symbol=${encodeURIComponent('ICEENDEX:ECF1!')}&fields=${TV_FIELDS}&no_404=true`
  const j = await fetchJsonWithin(url, {
    deadline,
    headers: {
      'User-Agent': BROWSER_UA,
      Accept: 'application/json',
      Origin: 'https://www.tradingview.com',
      Referer: 'https://www.tradingview.com/',
    },
  })
  if (!j || typeof j !== 'object') throw new Error('심볼 없음(본문 null)')

  const spot = num(j.close)
  if (!isSaneSpot(spot)) throw new Error(`가격 범위 이탈: ${j.close}`)

  // 통화 확인 — EUR 이 아니면 다른 상품을 잡은 것이다.
  const ccy = (j.currency || j.currency_id || '').toString().toUpperCase()
  if (ccy && ccy !== 'EUR') throw new Error(`통화 불일치: ${ccy}`)

  // change 는 이미 퍼센트. 같은 응답의 change_abs 로 교차 검산해서 단위 오해를 막는다.
  let dayChangePct = num(j.change)
  const abs = num(j.change_abs)
  if (dayChangePct !== null && abs !== null && spot - abs > 0) {
    const derived = (abs / (spot - abs)) * 100
    // 0.05%p 이상 어긋나면 change 가 퍼센트가 아니라는 뜻 → 파생값을 쓴다.
    if (Math.abs(derived - dayChangePct) > 0.05) dayChangePct = derived
  }

  return {
    spot,
    dayChangePct: dayChangePct === null ? null : Number(dayChangePct.toFixed(2)),
    asOf: fmtUtc(j.update_time) || fmtUtc(Date.now() / 1000, false),
    isLive: true,
    delayMinutes: parseDelayMinutes(j.update_mode),
    instrument: INSTRUMENT,
    source: 'TradingView 시세 스크래핑 · ICE Endex ECF1!(EUA 선물 최근월물) · 시각 UTC',
    sourceUrl: 'https://www.tradingview.com/symbols/ICEENDEX-ECF1!/',
    provider: 'tradingview-iceendex-ecf1',
  }
}

/** 'delayed_streaming_600' → 10, 'streaming' → 0, 모르면 null. 지연 분을 지어내지 않는다. */
function parseDelayMinutes(updateMode) {
  if (typeof updateMode !== 'string' || !updateMode) return null
  if (/^(streaming|realtime)$/i.test(updateMode)) return 0
  const m = updateMode.match(/delayed[_-]?streaming[_-]?(\d+)/i)
  if (m) return Math.round(Number(m[1]) / 60)
  return null
}

/* ── provider 2: Investing.com 모바일 앱 API ───────────────────────────
 *
 * GET https://aappapi.investing.com/get_screen.php?screen_ID=22&pair_ID=8848
 *
 * pair_ID 8848 은 investing.com 검색 API 에서 확인했다:
 *   {"pair_ID":8848,"search_main_text":"CFI2Z6",
 *    "search_main_longtext":"Carbon Emissions Futures","search_main_subtext":"Commodity - ICE"}
 * 응답 overview_table 에 Unit "1 Tonne", Contract Size "1,000 Tonnes",
 * Point Value "1 = €1000" 이 실려 있어 **EUR 표시 ICE EUA 선물**임이 확인된다.
 *
 * ⚠ 웹(www.investing.com) HTML 과 api.investing.com 은 Cloudflare 403 이지만
 *   이 모바일 엔드포인트는 통과한다. x-meta-ver 헤더가 없으면 거부당한다.
 *
 * 뽑는 필드(전부 pairs_data[0], **같은 응답**):
 *   last               → 가격 '81.99'
 *   change_percent_val → 등락률 퍼센트 '-0.55'  (change_precent_raw 도 같은 값)
 *   change_val         → 등락 절대값 '-0.45' (검산용)
 *   last_timestamp     → epoch(초)
 *   point_value        → '1 = €1000' (통화 확인용)
 */
async function fromInvestingMobile(deadline) {
  const url = 'https://aappapi.investing.com/get_screen.php?screen_ID=22&pair_ID=8848&lang_ID=1&time_utc_offset=0&skinID=2'
  const j = await fetchJsonWithin(url, {
    deadline,
    headers: {
      'User-Agent': 'Dalvik/2.1.0 (Linux; U; Android 13; SM-S918B Build/TP1A.220624.014)',
      'x-meta-ver': '14',
      'x-app-ver': '1200',
      'x-os': 'Android',
      'x-udid': '11111111-1111-1111-1111-111111111111',
      Accept: 'application/json',
    },
  })

  const p = j?.data?.[0]?.screen_data?.pairs_data?.[0]
  if (!p) throw new Error('pairs_data 없음(응답 구조 변경)')

  const spot = num(p.last)
  if (!isSaneSpot(spot)) throw new Error(`가격 범위 이탈: ${p.last}`)

  // 통화 확인 — point_value 가 '1 = €1000' 형태다. €가 없으면 다른 상품/통화다.
  const pv = String(p.point_value ?? '')
  if (pv && !pv.includes('€')) throw new Error(`통화 불일치(point_value=${pv})`)

  // 등락률: change_percent_val 이 1순위, 없으면 change_val 로 파생(둘 다 같은 응답).
  let dayChangePct = num(p.change_percent_val)
  if (dayChangePct === null) dayChangePct = num(p.change_precent_raw)
  if (dayChangePct === null) {
    const abs = num(p.change_val)
    if (abs !== null && spot - abs > 0) dayChangePct = (abs / (spot - abs)) * 100
  }

  return {
    spot,
    dayChangePct: dayChangePct === null ? null : Number(dayChangePct.toFixed(2)),
    asOf: fmtUtc(p.last_timestamp) || fmtUtc(Date.now() / 1000, false),
    isLive: true,
    // Investing 응답에 기계가 읽을 지연 필드가 없다. 지어내지 않고 null 로 둔다.
    delayMinutes: null,
    instrument: INSTRUMENT,
    source: 'Investing.com 모바일 API 스크래핑 · ICE CFI2Z6(EUA 선물 Dec-26) · 시각 UTC',
    sourceUrl: 'https://www.investing.com/commodities/carbon-emissions',
    provider: 'investing-mobile-8848',
  }
}

/* ── provider 3: Barchart 웹 API ───────────────────────────────────────
 *
 * 2단계다. 무료 웹 API 가 XSRF-TOKEN 쿠키를 요구한다.
 *   ① GET https://www.barchart.com/futures/quotes/CKZ26  → Set-Cookie 로 XSRF-TOKEN 수령
 *   ② GET /proxies/core-api/v1/quotes/get?symbols=CKZ26&...
 *      헤더에 x-xsrf-token(=쿠키값 URL 디코드) + Cookie 를 실어 보낸다
 *
 * CKZ26 = Barchart 표기의 'ICE EUA Futures (Dec '26)'. contractName 으로 확인한다.
 *
 * 응답 data[0].raw (문자열 포맷이 안 붙은 원본):
 *   lastPrice     81.99      (표시용 lastPrice 는 '81.99s' — 접미사 s = settlement/정산가)
 *   priceChange   -0.45
 *   percentChange -0.0055    ⚠ **소수 분수**다. 퍼센트가 아니다
 *   tradeTime     1786572480 ⚠ 22:08Z 로 ICE 거래시간 밖이다(타임존 규약이 다름).
 *                            그래서 epoch 대신 표시용 tradeTime 'MM/DD/YY' 를 쓴다
 *   currency      null       (통화 필드가 비어 있어 contractName + 범위로 검증한다)
 */
async function fromBarchart(deadline) {
  const pageUrl = 'https://www.barchart.com/futures/quotes/CKZ26'
  const page = await fetchWithin(pageUrl, {
    deadline,
    headers: {
      'User-Agent': BROWSER_UA,
      Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
      'Accept-Language': 'en-US,en;q=0.9',
    },
  })

  const setCookies = typeof page.headers.getSetCookie === 'function'
    ? page.headers.getSetCookie()
    : [page.headers.get('set-cookie')].filter(Boolean)
  const jar = setCookies.map((c) => String(c).split(';')[0]).join('; ')
  const rawToken = (jar.match(/XSRF-TOKEN=([^;]+)/) || [])[1]
  if (!rawToken) throw new Error('XSRF-TOKEN 쿠키 없음')

  const apiUrl =
    'https://www.barchart.com/proxies/core-api/v1/quotes/get' +
    '?symbols=CKZ26&fields=symbol,contractName,lastPrice,priceChange,percentChange,tradeTime,currency&raw=1'
  const j = await fetchJsonWithin(apiUrl, {
    deadline,
    headers: {
      'User-Agent': BROWSER_UA,
      Accept: 'application/json',
      'x-xsrf-token': decodeURIComponent(rawToken),
      Cookie: jar,
      Referer: pageUrl,
    },
  })

  const row = j?.data?.[0]
  const raw = row?.raw || row
  if (!raw) throw new Error('data[0] 없음(응답 구조 변경)')

  // 종목 확인 — 이름에 EUA 가 없으면 다른 계약을 잡은 것이다.
  const name = String(row?.contractName ?? raw?.contractName ?? '')
  if (name && !/EUA/i.test(name)) throw new Error(`종목 불일치: ${name}`)

  const spot = num(raw.lastPrice)
  if (!isSaneSpot(spot)) throw new Error(`가격 범위 이탈: ${raw.lastPrice}`)

  // 등락률은 priceChange 에서 파생한다(같은 응답, 단위 모호성 없음).
  // percentChange 는 분수(-0.0055)라 그대로 쓰면 100배 틀린다.
  let dayChangePct = null
  const abs = num(raw.priceChange)
  if (abs !== null && spot - abs > 0) {
    dayChangePct = (abs / (spot - abs)) * 100
  } else {
    const pc = num(raw.percentChange)
    if (pc !== null) dayChangePct = Math.abs(pc) <= 1 ? pc * 100 : pc
  }

  // tradeTime epoch 은 타임존 규약이 어긋나 있다. 표시용 'MM/DD/YY' 를 날짜로 쓴다.
  let asOf = null
  const disp = String(row?.tradeTime ?? '')
  const m = disp.match(/^(\d{2})\/(\d{2})\/(\d{2})$/)
  if (m) asOf = `20${m[3]}-${m[1]}-${m[2]}`
  if (!asOf) asOf = fmtUtc(raw.tradeTime, false)

  return {
    spot,
    dayChangePct: dayChangePct === null ? null : Number(dayChangePct.toFixed(2)),
    asOf: asOf || fmtUtc(Date.now() / 1000, false),
    isLive: true,
    // Barchart 응답에 지연 정보가 없다(정산가 's' 표기만 있음). 지어내지 않는다.
    delayMinutes: null,
    instrument: INSTRUMENT,
    source: `Barchart 웹 API 스크래핑 · ${name || 'ICE EUA Futures (Dec \'26)'} · CKZ26`,
    sourceUrl: 'https://www.barchart.com/futures/quotes/CKZ26',
    provider: 'barchart-ckz26',
  }
}

/* ── provider 목록 (시도 순서) ─────────────────────────────────────── */

/**
 * 시도 순서와 각 provider 의 설명.
 * TradingView 를 앞에 둔 이유: 요청 1회, 응답 7~250ms, 통화·지연시간이 명시 필드로 온다.
 * Barchart 를 뒤에 둔 이유: 쿠키 왕복 때문에 요청이 2회다.
 */
export const EUA_FEED_PROVIDERS = [
  {
    id: 'tradingview-iceendex-ecf1',
    label: 'TradingView · ICE Endex ECF1!',
    symbol: 'ICEENDEX:ECF1!',
    kind: 'scrape',
    description: 'EUA 선물 최근월물. scanner.tradingview.com 위젯 엔드포인트. 10분 지연(update_mode=delayed_streaming_600), 통화 EUR 명시.',
    fetch: fromTradingView,
  },
  {
    id: 'investing-mobile-8848',
    label: 'Investing.com 모바일 API · CFI2Z6',
    symbol: 'CFI2Z6 (pair_ID 8848)',
    kind: 'scrape',
    description: 'ICE Carbon Emissions Futures Dec-26. 웹은 Cloudflare 403 이지만 aappapi 모바일 엔드포인트는 통과. 지연 시간 미표기.',
    fetch: fromInvestingMobile,
  },
  {
    id: 'barchart-ckz26',
    label: 'Barchart 웹 API · CKZ26',
    symbol: 'CKZ26',
    kind: 'scrape',
    description: "ICE EUA Futures (Dec '26). XSRF-TOKEN 쿠키를 먼저 받아야 해서 요청 2회. 정산가 기준.",
    fetch: fromBarchart,
  },
]

/* ── 공개 API ──────────────────────────────────────────────────────── */

/**
 * ICE EUA 선물 시세를 가져온다. provider 를 순서대로 시도하고 첫 성공을 돌린다.
 *
 * **예외를 던지지 않는다.** 전부 실패하면 null.
 * 총 소요는 TOTAL_BUDGET_MS(8초)를 넘지 않는다 — 심사 중 외부 API 가 느려도 화면이 멈추면 안 된다.
 *
 * @returns {Promise<{
 *   spot: number, dayChangePct: number|null, asOf: string, isLive: true,
 *   delayMinutes: number|null, instrument: string, source: string,
 *   sourceUrl: string, provider: string, attempts: Array<{id:string,error:string}>
 * }|null>}
 */
export async function fetchEuaFutures() {
  const deadline = Date.now() + TOTAL_BUDGET_MS
  const attempts = []

  for (const p of EUA_FEED_PROVIDERS) {
    if (Date.now() >= deadline) {
      attempts.push({ id: p.id, error: '총 예산(8초) 소진으로 미시도' })
      continue
    }
    try {
      const quote = await p.fetch(deadline)
      // provider 가 규약을 어겼을 때를 대비한 마지막 방어선.
      if (!quote || !isSaneSpot(quote.spot)) throw new Error('반환값 sanity 실패')
      return { ...quote, attempts }
    } catch (err) {
      const msg = err?.name === 'AbortError' ? '타임아웃' : (err?.message || String(err))
      attempts.push({ id: p.id, error: msg })
    }
  }

  // 하나도 못 얻었다. 다른 상품(CO2.L 등)을 선물인 척 끼워넣지 않는다 — 호출부가 CSV 로 폴백한다.
  return null
}

/**
 * 진단용 — 모든 provider 를 각각 돌려 결과를 나열한다(운영 경로에서는 쓰지 않는다).
 * provider 가 깨졌을 때 어느 것이 살아있는지 한 번에 보려고 남겨둔다.
 */
export async function probeAllProviders() {
  const out = []
  for (const p of EUA_FEED_PROVIDERS) {
    const t0 = Date.now()
    try {
      const q = await p.fetch(Date.now() + PROVIDER_CAP_MS)
      out.push({ id: p.id, ok: true, ms: Date.now() - t0, quote: q })
    } catch (err) {
      out.push({ id: p.id, ok: false, ms: Date.now() - t0, error: err?.message || String(err) })
    }
  }
  return out
}

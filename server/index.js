// GlovisHEDGE 서버 — /api 단일 게이트 + 정적 파일 + SPA 폴백.
//   브라우저 ──▶ /api/*  ──┬─▶ 리스크 엔진 (숫자 전담)
//                          ├─▶ 규정 색인   (검색 전담)
//                          └─▶ LLM         (설명 전담)
// 이 경계가 곧 발표 자료의 구성도다. LLM 키는 서버에만 있다.

import express from 'express'
import { readFileSync, existsSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

import { env } from './lib/env.js'
import { cached, fetchWithTimeout } from './lib/cache.js'
import { llmAvailable, llmJson, llmText, currentModel } from './lib/llm.js'
import * as P from './lib/prompts.js'

const __dir = dirname(fileURLToPath(import.meta.url))
const DIST = resolve(__dir, '../dist')
const app = express()
app.use(express.json({ limit: '9mb' }))

const PORT = process.env.PORT || 8080

/* ⚠ 이 서버는 **숫자를 계산하지 않는다.**
 *   항로 거리·경로·총비용·리스크는 전부 순수 함수라 브라우저에서 돈다(`src/engine/`).
 *   서버가 맡는 것은 **서버여야만 하는 것**뿐이다 — LLM 키, 규정 색인, 외부 시세.
 *   덕분에 프런트/백엔드 저장소가 서로를 import 하지 않고 각자 선다. */

/* ── 선택 의존 모듈 — 아직 없어도 서버는 뜬다 ───────────────────── */
let rag = null
try {
  rag = await import('./rag/retrieve.js')
  const st = rag.ragStatus?.()
  console.log('[rag]', st?.ready ? `색인 ${st.articles}조 로드됨` : '색인 미준비')
} catch (e) {
  console.warn('[rag] 미로드:', e.message)
}

/* ── 1. GET /api/eua — EUA 시세 ────────────────────────────────────
 * 출처는 CO2.L(SparkChange Physical Carbon EUA ETC · EUA 실물 1:1 보유).
 * 거래소 원장 직접 수신이 아닌 **프록시**다 — 화면 각주에 그렇게 밝힌다.
 * ⚠ 등락률도 **같은 응답의 값**을 쓴다. 가격만 실시세로 바꾸고 등락률은 정적
 *   스냅샷을 쓰면 티커가 자기 값과 어긋난다. (함정 6)                         */
let fetchEuaFutures = null
try {
  ;({ fetchEuaFutures } = await import('./lib/euaFeed.js'))
  console.log('[euaFeed] 로드됨')
} catch (e) {
  console.warn('[euaFeed] 미로드 — 검증 계열 종가로 폴백합니다:', e.message)
}

/**
 * ⚠ 가격 기준은 **하나의 상품**이어야 한다 — ICE EUA 선물.
 *
 * 실시세와 과거 계열이 같은 상품이라 섞이지 않는다. 예전에는 헤더에 CO2.L(실물 보유 ETC)을
 * 띄웠는데, 같은 날 기준 5.5% 낮고 그 괴리가 4년에 걸쳐 1.01→1.06 으로 벌어진다(실측).
 * 다른 상품 두 개를 나란히 두면 "왜 78인데 83으로 계산하냐"가 나온다. 그래서 걷어냈다.
 *
 * 실시세가 죽으면 **검증 계열의 마지막 종가**로 폴백한다. 다른 상품을 선물인 척
 * 끼워 넣지 않는다 — 그게 가장 나쁜 폴백이다.
 */
/* 실시세를 못 받으면 **spot 을 null 로 돌려준다.** 서버가 대신 옛 값을 지어내지 않는다 —
   프런트가 번들에 갖고 있는 검증 계열의 마지막 종가로 폴백한다(같은 상품이라 섞이지 않는다). */
const FEED_DOWN = {
  spot: null,
  dayChangePct: null,
  asOf: null,
  currency: 'EUR',
  instrument: 'ICE EUA 선물',
  source: '실시세 조회 실패 — 클라이언트가 검증 계열 마지막 종가로 폴백합니다',
  sourceUrl: null,
  provider: null,
  delayMinutes: null,
  isLive: false,
  basis: 'unavailable',
}

app.get('/api/eua', async (_req, res) => {
  const quote = await cached(
    'eua-futures',
    10 * 60 * 1000, // 10분 캐시 — 1순위 피드가 10분 지연이라 더 자주 받아도 같은 값이다
    async () => {
      if (!fetchEuaFutures) throw new Error('피드 모듈 없음')
      const q = await fetchEuaFutures()
      if (!q) throw new Error('전 경로 실패')
      return { ...q, basis: 'live-futures' }
    },
    FEED_DOWN
  )
  res.json(quote)
})

/* ── 2. GET /api/fx — 환율 ─────────────────────────────────────────
 * 키·가입 불필요. 하루 1회 갱신이라 24시간 캐시하면 레이트리밋에 안 걸린다.
 * ⚠ 소수점을 자르지 마라. 전체 정밀도로 계산하고 표시할 때만 반올림한다.
 * 조건: 화면에 출처 표기 필수 — attribution 을 그대로 렌더한다.               */
app.get('/api/fx', async (_req, res) => {
  const data = await cached(
    'fx',
    24 * 60 * 60 * 1000,
    async () => {
      const r = await fetchWithTimeout('https://open.er-api.com/v6/latest/USD', {}, 6000)
      const j = await r.json()
      if (!j?.rates?.EUR) throw new Error('환율 응답 형식 오류')
      return {
        usdPerEur: 1 / j.rates.EUR,
        krwPerEur: j.rates.KRW / j.rates.EUR,
        krwPerUsd: j.rates.KRW,
        asOf: j.time_last_update_utc?.slice(5, 16) || new Date().toISOString().slice(0, 10),
        nextUpdate: j.time_next_update_utc || null,
        source: 'open.er-api.com',
        attribution: 'Rates By Exchange Rate API',
        attributionUrl: 'https://www.exchangerate-api.com',
        isLive: true,
      }
    },
    {
      usdPerEur: 1.1548,
      krwPerEur: 1630.83,
      krwPerUsd: 1412.2,
      asOf: '2026-07-24',
      source: '고정환율(폴백)',
      attribution: 'Rates By Exchange Rate API',
      attributionUrl: 'https://www.exchangerate-api.com',
      isLive: false,
    }
  )
  res.json(data)
})

/* ── 3. 항로 거리·경로는 **서버가 계산하지 않는다** ────────────────
 * 순수 오프라인 계산(웨이포인트 그래프 + 다익스트라)이라 브라우저에서 돈다.
 *   → `src/engine/searoute.js`
 * 왕복이 없어 조건을 바꿀 때 스피너가 안 뜨고(영상 녹화에서 중요), 서버가
 * 프런트 코드를 import 할 이유도 사라진다.                                    */

/* ── 4. POST /api/compare-routes — 항로 2개 비교 리포트 ────────────
 * 금액은 요청에 실려 온 값만 쓴다. LLM 이 다시 계산하지 않는다.               */
app.post('/api/compare-routes', async (req, res) => {
  const { routes = [], context = {} } = req.body || {}
  if (routes.length !== 2) return res.status(400).json({ error: '항로 2개가 필요합니다' })
  if (!llmAvailable()) return res.status(503).json({ error: 'NO_LLM_KEY', message: 'AI 기능은 지금 사용할 수 없습니다(LLM 키 미설정). 계산 기능은 모두 정상 동작합니다.' })

  const fmt = (r) =>
    `[항로 ${r.key}] ${r.name}
- 상태: ${r.status === 'best' ? '추천' : r.status === 'limited' ? '제한적' : '운항 불가'}
- 상태 사유: ${r.statusReason}
- 총비용: ${r.totalUsd == null ? '산출 안 함(운항 불가)' : '$' + r.totalUsd}
- 기본운임: ${r.baseFreightUsd == null ? '산출 안 함' : '$' + Math.round(r.baseFreightUsd)}
- 탄소비용(EU ETS): ${r.carbonCostUsd == null ? '산출 안 함' : '$' + Math.round(r.carbonCostUsd)}
- 전쟁위험보험료: ${r.warRiskUsd == null ? '산출 안 함' : '$' + Math.round(r.warRiskUsd)} (요율 ${r.warRiskRatePct}%)
- 해상 리드타임: ${r.leadTimeDays}일 / 납기 충족: ${r.meetsDeadline ? '예' : '아니오'}
- 항로 거리: ${r.distanceNm}nm`

  const prompt = `${routes.map(fmt).join('\n\n')}

[조회 조건]
- 인코텀즈: ${context.incoterm || 'FOB'} / 화물 단위: ${context.unit || '20ft'} / 납기: ${context.deadline || '제공되지 않음'}

위 두 항로를 비교하는 리포트를 JSON 으로 쓰라. pros/cons 의 키는 각 항로의 key("${routes[0].key}", "${routes[1].key}")를 쓴다.`

  try {
    const out = await llmJson({ system: P.COMPARE, prompt, maxTokens: 8192 })
    res.json({ ...out, via: 'llm', model: currentModel() })
  } catch (e) {
    res.status(502).json({ error: 'LLM_FAILED', message: e.message })
  }
})

/* ── 5. POST /api/chat — AI 어시스턴트 단일 진입점 ─────────────────── */
const SCREENS = [
  ['route-kpi', '경로 추천 · 요약 지표'],
  ['route-map', '경로 추천 · 항로 지도'],
  ['route-table', '경로 추천 · 항로별 총비용 비교'],
  ['route-compare', '경로 추천 · AI 항로 비교'],
  ['route-incoterm', '경로 추천 · 인코텀즈 분석'],
  ['hedge-chart', '선적 시점 분석 · EUA 가격 추이·구간'],
  ['hedge-target', '선적 시점 분석 · 목표 탄소비용 설정'],
  ['hedge-scenario', '선적 시점 분석 · 탄소비용 시나리오'],
  ['hedge-track', '선적 시점 분석 · 예측 성과 추적'],
  ['hedge-regulation', '선적 시점 분석 · 규정 QA'],
  ['scm-leadtime', '공급망 일정 · 전체 리드타임 분해'],
  ['scm-compare', '공급망 일정 · 항로별 일정 비교'],
  ['scm-carrier', '공급망 일정 · 파트너 선사 항차'],
  ['scm-latest', '공급망 일정 · 재고 도착 일정 역산'],
  ['report', '운송 의사결정 근거 리포트'],
]

app.post('/api/chat', async (req, res) => {
  const { botId = 'auto', message = '', ctx = {} } = req.body || {}
  if (!message.trim()) return res.status(400).json({ error: '메시지가 비어 있습니다' })
  if (!llmAvailable()) {
    return res.json({
      via: 'engine',
      botId,
      answer: 'AI 기능은 지금 사용할 수 없습니다(LLM 키 미설정). 계산 기능은 모두 정상 동작합니다.',
      degraded: true,
    })
  }

  try {
    // reg — 규정 검색은 색인이 담당한다
    if (botId === 'reg') {
      if (!rag?.ragStatus?.().ready) {
        return res.json({ via: 'index', botId, answer: '규정 색인이 아직 준비되지 않았습니다.', hits: [], degraded: true })
      }
      const out = await rag.ragAsk({ question: message, k: 4 })
      return res.json({ via: 'index', botId, ...out })
    }

    // term — ⚠ 대화 맥락을 붙이지 않는다. 입력이 조항 원문이라 앞 턴이 섞이면 판정이 오염된다.
    if (botId === 'term') {
      const out = await llmJson({ system: P.TERM, prompt: message.slice(0, 12000), maxTokens: 8192 })
      return res.json({ via: 'llm', botId, ...out })
    }

    // trip — 선택 가능한 값 목록을 주입한다. 없는 항목은 되묻는다.
    if (botId === 'trip') {
      const prompt = `문장: ${message.slice(0, 400)}
오늘: ${ctx.today || '2026-07-24'}

[고를 수 있는 값]
- 출발 항만: ${(ctx.options?.originPorts || []).join(', ') || '제공되지 않음'}
- 도착 항만: ${(ctx.options?.destPorts || []).join(', ') || '제공되지 않음'}
- 화물 유형: container, pctc
- 컨테이너 규격: 20ft, 40ft, 40hc
- 인코텀즈: FOB, CIF

${P.TRIP_TAIL}`
      const out = await llmJson({ system: P.TRIP, prompt, maxTokens: 8192 })
      return res.json({ via: 'llm', botId, ...out })
    }

    // doc — 엔진 스냅샷으로 보고서 초안
    if (botId === 'doc') {
      const prompt = `${P.riskSnapshot(ctx.snapshot || {})}\n\n요청: ${message.slice(0, 500)}`
      const out = await llmJson({ system: P.DOC_REPORT, prompt, maxTokens: 8192 })
      return res.json({ via: 'llm', botId, ...out })
    }

    // risk — 파라미터만 추출한다. 계산은 엔진이 한다.
    if (botId === 'risk') {
      const prompt = `질문: ${message.slice(0, 500)}\n\n${P.riskSnapshot(ctx.snapshot || {})}\n\n이 질문에서 분석 파라미터만 뽑아라.`
      const out = await llmJson({ system: P.ROUTER, prompt: `${prompt}\n\n${P.ROUTER_TAIL}`, maxTokens: 8192 })
      return res.json({ via: 'llm', botId, ...out })
    }

    // auto / nav — 라우터
    const prompt = `질문: ${message.slice(0, 500)}
${P.historyBlock(ctx.history)}
[이동 가능한 화면]
${SCREENS.map(([id, label]) => `- ${id}: ${label}`).join('\n')}

[현재 조회 조건]
${P.riskSnapshot(ctx.snapshot || {})}

${P.ROUTER_TAIL}`
    const out = await llmJson({ system: P.ROUTER, prompt, maxTokens: 8192 })
    res.json({ via: 'llm', botId, ...out })
  } catch (e) {
    res.status(502).json({ error: 'LLM_FAILED', message: e.message, botId })
  }
})

/* ── 6. POST /api/rag/ask — 규정 근거 검색 ─────────────────────────── */
app.post('/api/rag/ask', async (req, res) => {
  const { question = '', k = 4 } = req.body || {}
  if (!question.trim()) return res.status(400).json({ error: '질문이 비어 있습니다' })
  if (!rag?.ragStatus?.().ready) {
    return res.json({
      hits: [],
      result: { answer: '규정 색인이 아직 준비되지 않았습니다.', used: [], caveat: '법률 자문이 아닙니다' },
      degraded: true,
    })
  }
  try {
    res.json(await rag.ragAsk({ question, k }))
  } catch (e) {
    res.status(502).json({ error: 'RAG_FAILED', message: e.message })
  }
})

/* ── 6-2. POST /api/alerts — 목표가 도달 알림 ───────────────────────
 * ⚠ **등록 시 즉시 한 번 판정한다.** 이미 목표가 이하면 그 자리에서 그렇게 답하고
 *   메일을 보낸다(status `sent`). 아직이면 `pending` 으로 두고 감시한다.
 * ⚠ 이 감시는 **서버 메모리에 있다.** 재배포·재시작하면 사라진다 — 화면에 그렇게 밝힌다.
 *   DB 가 없는데 "계속 지켜본다"고 말하면 거짓이 된다.                          */
const alerts = new Map() // email|target -> { email, targetEur, createdAt, status, lastCheck }

function isEmail(s) {
  return typeof s === 'string' && /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(s.trim())
}

async function sendAlertMail({ email, targetEur, spot, asOf }) {
  const key = env('RESEND_API_KEY')
  if (!key) return { ok: false, reason: '메일 발송 키가 없어 등록만 했습니다' }
  try {
    const r = await fetchWithTimeout(
      'https://api.resend.com/emails',
      {
        method: 'POST',
        headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          from: 'GlovisHEDGE <onboarding@resend.dev>',
          to: [email],
          subject: `[GlovisHEDGE] EUA 목표가 €${targetEur} 도달`,
          html: `<div style="font-family:system-ui,sans-serif;max-width:520px">
<h2 style="margin:0 0 8px">목표가에 닿았습니다</h2>
<p style="margin:0 0 16px;color:#4E5968">설정하신 목표가 <b>€${targetEur}/tCO₂</b> 이하로 내려왔습니다.</p>
<table style="border-collapse:collapse;font-size:14px">
<tr><td style="padding:4px 12px 4px 0;color:#6B7684">현재 EUA</td><td><b>€${spot}</b></td></tr>
<tr><td style="padding:4px 12px 4px 0;color:#6B7684">기준 시각</td><td>${asOf}</td></tr>
<tr><td style="padding:4px 12px 4px 0;color:#6B7684">상품</td><td>ICE EUA 선물</td></tr>
</table>
<p style="margin:16px 0 0;font-size:12px;color:#8B95A1">
이 알림은 가격의 방향을 예측하지 않습니다 — 설정하신 값에 실제로 닿았을 때만 보냅니다.<br>
GlovisHEDGE · 팀 GREENWAY</p></div>`,
        }),
      },
      8000
    )
    const j = await r.json().catch(() => null)
    // ⚠ 200 이어도 본문에 오류가 실려 올 수 있다. id 가 있어야 실제 발송이다.
    if (!j?.id) return { ok: false, reason: j?.message || `발송 실패 (HTTP ${r.status})` }
    return { ok: true, id: j.id }
  } catch (e) {
    return { ok: false, reason: e.message }
  }
}

/**
 * ⚠ **메일은 등록 한 건당 정확히 한 번만 나간다.** 구멍이 셋이라 셋 다 막는다:
 *   ① 같은 키로 재등록 → 기존 상태를 보고 **덮어쓰지 않는다**
 *   ② 버튼 연타/동시 요청 → 보내기 **전에** `sending` 으로 선점한다(await 이전에)
 *   ③ 감시 루프와 등록이 겹침 → 같은 선점 플래그를 공유하고, 루프는 재진입을 막는다
 * 상태 전이는 한 방향뿐이다: pending → sending → sent | failed.
 * `sent` 는 **다시 보내지 않는다.** `failed` 만 사용자가 다시 눌러 재시도할 수 있다.
 */
const alertKey = (email, targetEur) => `${String(email).trim().toLowerCase()}|${Number(targetEur)}`

/** 선점에 성공한 호출자만 true 를 받는다. await 이전에 동기적으로 끝난다. */
function claimForSend(rec) {
  if (rec.status === 'sent' || rec.status === 'sending') return false
  rec.status = 'sending'
  return true
}

async function deliver(rec, spot, asOf) {
  const sent = await sendAlertMail({ email: rec.email, targetEur: rec.targetEur, spot, asOf })
  rec.status = sent.ok ? 'sent' : 'failed'
  rec.sentAt = sent.ok ? Date.now() : null
  rec.lastError = sent.ok ? null : sent.reason
  console.log(`[alerts] ${rec.email} 목표 €${rec.targetEur} → ${rec.status}${sent.ok ? ` (id ${sent.id})` : ` (${sent.reason})`}`)
  return sent
}

app.post('/api/alerts', async (req, res) => {
  const { email, targetEur, spot, asOf } = req.body || {}
  if (!isEmail(email)) return res.status(400).json({ error: 'INVALID_EMAIL', message: '이메일 주소를 확인해 주세요.' })
  if (!(targetEur > 0)) return res.status(400).json({ error: 'INVALID_TARGET', message: '목표가를 입력해 주세요.' })

  const id = alertKey(email, targetEur)
  const existing = alerts.get(id)

  // ① 이미 보낸 건은 **다시 보내지 않는다**
  if (existing?.status === 'sent') {
    return res.json({
      status: 'sent',
      duplicate: true,
      message: `이미 ${existing.email} 으로 보냈습니다. 같은 목표가로는 다시 보내지 않습니다.`,
    })
  }
  // ② 발송 중이면 두 번째 요청은 대기 상태만 알려준다
  if (existing?.status === 'sending') {
    return res.json({ status: 'sending', duplicate: true, message: '보내는 중입니다.' })
  }
  // ③ 이미 감시 중이면 중복 등록하지 않는다
  if (existing?.status === 'pending') {
    return res.json({
      status: 'pending',
      duplicate: true,
      message: `이미 등록돼 있습니다. ${existing.email} 으로 한 번만 보냅니다.`,
      caveat: '이 등록은 서버 메모리에 있습니다 — 재배포하면 초기화됩니다(데모에 DB 를 두지 않았습니다).',
    })
  }

  const rec = existing || { email: String(email).trim(), targetEur: Number(targetEur), createdAt: Date.now(), status: 'new' }
  alerts.set(id, rec)

  const alreadyBelow = spot != null && spot <= targetEur
  if (alreadyBelow) {
    if (!claimForSend(rec)) return res.json({ status: rec.status, duplicate: true, message: '이미 처리 중입니다.' })
    const sent = await deliver(rec, spot, asOf)
    return res.json({
      status: rec.status,
      alreadyBelow: true,
      message: sent.ok
        ? `이미 목표가 이하라 방금 ${rec.email} 으로 보냈습니다. 같은 목표가로는 다시 보내지 않습니다.`
        : `이미 목표가 이하지만 발송에 실패했습니다 — ${sent.reason}`,
    })
  }

  rec.status = 'pending'
  res.json({
    status: 'pending',
    alreadyBelow: false,
    message: `${rec.email} 으로 한 번만 보냅니다. 10분마다 시세를 확인합니다.`,
    caveat: '이 등록은 서버 메모리에 있습니다 — 재배포하면 초기화됩니다(데모에 DB 를 두지 않았습니다).',
  })
})

/** 대기 중인 알림 감시 — 10분마다. 실시세가 목표가 이하로 내려오면 그때 **한 번만** 보낸다. */
let watcherBusy = false
setInterval(async () => {
  if (watcherBusy) return // 재진입 방지 — 앞 회차가 아직 보내는 중이면 건너뛴다
  const pending = [...alerts.values()].filter((a) => a.status === 'pending')
  if (!pending.length || !fetchEuaFutures) return
  watcherBusy = true
  try {
    let q = null
    try {
      q = await fetchEuaFutures()
    } catch {
      return // 시세를 못 받으면 아무것도 하지 않는다. 추측으로 보내지 않는다.
    }
    if (!q?.spot) return
    for (const a of pending) {
      if (q.spot > a.targetEur) continue
      if (!claimForSend(a)) continue // 등록 요청이 먼저 선점했으면 건너뛴다
      await deliver(a, q.spot, q.asOf)
    }
  } finally {
    watcherBusy = false
  }
}, 10 * 60 * 1000).unref?.()

/* ── 7. 헬스체크 — 캡처·배포 검증 전에 이걸 강제한다 ──────────────── */
app.get('/api/health', (_req, res) => {
  res.json({
    ok: true,
    time: new Date().toISOString(),
    llm: llmAvailable(),
    rag: rag?.ragStatus?.() || { ready: false },
    dist: existsSync(DIST),
  })
})

/* ── 정적 파일 + SPA 폴백 ──────────────────────────────────────────
 * ⚠ SPA 폴백은 없는 경로에도 200 + index.html 을 돌려준다.
 *   자산 존재 확인을 200 으로 판단하면 오진한다 — content-type 을 봐라.       */
if (existsSync(DIST)) {
  app.use(express.static(DIST, { index: false, maxAge: '1h' }))
  app.get('*', (req, res, next) => {
    if (req.path.startsWith('/api/')) return next()
    res.sendFile(resolve(DIST, 'index.html'))
  })
} else {
  // 백엔드만 단독 배포하면 정상이다. 프런트를 같은 서버가 서빙하려면 빌드 산출물을
  // `dist/` 에 두고 다시 시작한다(프런트 저장소에서 `npm run build`).
  console.warn('[static] dist 없음 — API 전용으로 뜹니다.')
}

app.use('/api', (_req, res) => res.status(404).json({ error: 'NOT_FOUND' }))

app.listen(PORT, () => {
  console.log(`GlovisHEDGE API  http://localhost:${PORT}`)
  console.log(`  LLM ${llmAvailable() ? '사용 가능 (' + currentModel() + ')' : '키 없음 — 계산 기능은 정상'}`)
})

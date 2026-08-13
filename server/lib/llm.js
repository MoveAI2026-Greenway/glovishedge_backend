// LLM 게이트 — **키는 여기서만.** 프런트 번들에 키를 넣지 마라. (API계약 §0)
// ⚠ 게이트웨이가 HTTP 200 본문에 오류 코드를 실어 보낸다(크레딧 소진 402).
//   `res.ok` 만 보면 "응답이 비어 있음"으로 오진한다 — 본문의 오류 필드를 반드시 확인한다.

import { env } from './env.js'
import { fetchWithTimeout } from './cache.js'

const KEY = () => env('GOOGLE_AI_STUDIO_KEY')
const BASE = 'https://generativelanguage.googleapis.com/v1beta'

// 앞에서부터 시도하고 실패하면 다음으로 넘어간다.
// ⚠ 이 키로는 `gemini-2.5-flash`(신규 사용자 불가) · `gemini-2.0-flash`(없음)가 안 된다 —
//   실측으로 확인했다. 되는 것을 앞에 둬야 요청마다 실패 왕복 2회를 낭비하지 않는다.
const MODELS = ['gemini-flash-latest', 'gemini-flash-lite-latest', 'gemini-2.5-flash', 'gemini-2.0-flash']
let resolvedModel = null

export function llmAvailable() {
  return !!KEY()
}

/**
 * ⚠ maxOutputTokens 를 짜게 잡지 마라. Gemini 는 **사고 토큰이 출력 예산을 먼저 먹는다.**
 *   1600 으로 두었더니 JSON 이 첫 문장에서 잘려(97자) 파싱이 계속 실패했다 — 실측이다.
 *   `thinkingConfig: {thinkingBudget: 0}` 은 이 모델에서 400 으로 거부되므로 예산을 늘려 푼다.
 */
async function callModel(model, { system, prompt, json, temperature = 0.2, maxTokens = 8192 }) {
  const body = {
    contents: [{ role: 'user', parts: [{ text: prompt }] }],
    generationConfig: {
      temperature,
      maxOutputTokens: maxTokens,
      ...(json ? { responseMimeType: 'application/json' } : {}),
    },
  }
  if (system) body.systemInstruction = { parts: [{ text: system }] }

  const r = await fetchWithTimeout(
    `${BASE}/models/${model}:generateContent?key=${encodeURIComponent(KEY())}`,
    { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) },
    30000
  )
  const j = await r.json()
  // ⚠ 200 본문에 실려 오는 오류를 반드시 본다
  if (j?.error) throw new Error(`LLM ${j.error.code ?? ''} ${j.error.message ?? 'unknown'}`)
  const cand = j?.candidates?.[0]
  const text = cand?.content?.parts?.map((p) => p.text || '').join('') || ''
  if (!text) {
    const reason = cand?.finishReason || j?.promptFeedback?.blockReason || 'empty'
    throw new Error(`LLM 응답 없음 (${reason})`)
  }
  return text
}

/** 텍스트 생성. 모델 목록을 순서대로 시도한다. */
export async function llmText(opts) {
  if (!llmAvailable()) throw new Error('NO_LLM_KEY')
  const order = resolvedModel ? [resolvedModel, ...MODELS.filter((m) => m !== resolvedModel)] : MODELS
  let lastErr
  for (const m of order) {
    try {
      const out = await callModel(m, opts)
      resolvedModel = m
      return out
    } catch (e) {
      lastErr = e
      if (String(e.message).startsWith('NO_LLM_KEY')) throw e
    }
  }
  throw lastErr || new Error('LLM 실패')
}

/** JSON 생성 — 잘려서 안 닫히면 한 번 재시도한다 (API계약 §5 함정) */
export async function llmJson(opts) {
  const tryParse = (t) => {
    const s = t.indexOf('{')
    const e = t.lastIndexOf('}')
    if (s < 0 || e <= s) return null
    try {
      return JSON.parse(t.slice(s, e + 1))
    } catch {
      return null
    }
  }
  let text = await llmText({ ...opts, json: true })
  let parsed = tryParse(text)
  if (parsed) return parsed
  // 잘린 것일 수 있으니 예산을 키워 한 번 더
  text = await llmText({ ...opts, json: true, temperature: 0, maxTokens: Math.max(opts.maxTokens || 0, 8192) })
  parsed = tryParse(text)
  if (parsed) return parsed
  // ⚠ "해석 실패"만 던지면 원인을 알 수 없다. **원문 앞부분을 붙여라.** (API계약 §11)
  throw new Error(`LLM JSON 파싱 실패 (${text.length}자, 잘림 의심) — 원문 앞부분: ${text.slice(0, 200)}`)
}

export function currentModel() {
  return resolvedModel || MODELS[0]
}

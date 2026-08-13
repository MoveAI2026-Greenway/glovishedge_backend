// .env 로더 — ⚠ 이 워크스페이스의 .env 는 `KEY = "value"` 형태(키 뒤 공백 + 따옴표)라
// `grep '^KEY='` 나 naive split 로는 "키 없음"으로 오판한다.
// ⚠ 값에 닫히지 않은 따옴표가 있으면 헤더에 그대로 실려 나간다(`Bearer "re_…` 로 인증이
//   조용히 실패한 적이 있다). 짝이 안 맞는 따옴표를 걷어낸다. (API계약 §9)

import { readFileSync, existsSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dir = dirname(fileURLToPath(import.meta.url))

const CANDIDATES = [
  resolve(__dir, '../../.env'), // app/.env
  resolve(__dir, '../../../../../.env'), // Contest Workflow/.env
  resolve(process.cwd(), '.env'),
]

function stripQuotes(v) {
  let s = String(v).trim()
  // 짝이 맞는 따옴표만 벗긴다
  if ((s.startsWith('"') && s.endsWith('"') && s.length > 1) || (s.startsWith("'") && s.endsWith("'") && s.length > 1)) {
    s = s.slice(1, -1)
  }
  // 짝이 안 맞는 따옴표는 걷어낸다
  s = s.replace(/^["']+/, '').replace(/["']+$/, '')
  return s.trim()
}

function parseEnvFile(path) {
  const out = {}
  try {
    const text = readFileSync(path, 'utf8')
    for (const raw of text.split(/\r?\n/)) {
      const line = raw.trim()
      if (!line || line.startsWith('#')) continue
      const eq = line.indexOf('=')
      if (eq < 0) continue
      const key = line.slice(0, eq).trim()
      if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key)) continue
      out[key] = stripQuotes(line.slice(eq + 1))
    }
  } catch {
    /* 파일이 없으면 무시 */
  }
  return out
}

const fileEnv = {}
for (const p of CANDIDATES) {
  if (existsSync(p)) Object.assign(fileEnv, parseEnvFile(p))
}

/** process.env 가 우선. 없으면 .env 파일. 빈 문자열은 없는 것으로 본다. */
export function env(key, fallback = '') {
  const v = process.env[key] ?? fileEnv[key] ?? fallback
  return typeof v === 'string' ? v.trim() : v
}

export function hasEnv(key) {
  return !!env(key)
}

export const ENV_SOURCES = CANDIDATES.filter((p) => existsSync(p))

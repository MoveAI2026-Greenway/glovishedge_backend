/**
 * retrieve.js — EU 해운 규정 조문 검색 모듈
 *
 *   export async function ragAsk({ question, k = 4 })
 *   export function ragStatus()
 *
 * 설계 원칙
 *  1) 색인은 서버 기동 시(모듈 로드 시) 1회만 읽어 메모리에 둔다.
 *  2) 색인이 없거나 API 키가 없어도 예외를 던지지 않는다 — 서버가 죽으면 안 된다.
 *     ragStatus().ready === false 로 알리고, ragAsk 는 그 사실을 담은 결과를 돌려준다.
 *  3) 하이브리드 검색: 벡터 0.6 + 키워드(BM25) 0.4. 각 신호를 min-max 정규화한 뒤 가중합한다.
 *     벡터만 쓰면 코사인이 0.68~0.72 에 몰려 변별이 안 된다.
 *  4) 근거(상위 k 조문)에 없으면 답하지 않는다.
 *
 * 의존성 없음 — Node 22 내장 fetch 만 사용.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const INDEX_PATH = path.join(__dirname, 'index.json');

const NOT_FOUND = '제공된 조문에서는 확인되지 않습니다';
const CAVEAT = '법률 자문이 아닙니다';

const W_VEC = 0.6; // 벡터 신호 가중
const W_KW = 0.4; // 키워드(BM25) 신호 가중

/**
 * 재현성 튜닝 상수 — 실측으로 정했다.
 *
 * LLM 번역은 temperature=0 이어도 매번 다른 문장을 낸다(질문에 따라 6회에 6종까지).
 * 그 흔들림이 순위로 새어나오지 않도록, 검색 신호의 '뼈대'를 결정적인 것으로 고정한다.
 *   W_QVEC_KO — 질의 벡터에서 한국어 원문 임베딩이 차지하는 비중.
 *               임베딩 API 자체는 완전히 결정적이다(같은 문장 cos=1.00000000 실측).
 *   W_KW_TRANSLATED — BM25 에서 '번역문에만 등장하는' 토큰의 가중치.
 *               사전(GLOSSARY)에서 뽑은 토큰은 1.0 으로 고정.
 *
 * 관측된 번역 변형(Q1 2종·Q2 6종·Q3 5종)에 대해 상위4 조합이
 * 1종으로 수렴하는 최댓값을 골랐다 — 번역을 죽이지 않으면서 순위는 고정된다.
 */
const W_QVEC_KO = 0.65;
const W_KW_TRANSLATED = 0.1;

/** 답변 생성용 모델 후보 (앞에서부터 시도) */
const GEN_MODELS = ['gemini-flash-latest', 'gemini-flash-lite-latest'];
/** 질의 변환용 — 단순 작업이라 lite 로 충분하다(실측 5.8초 → 1.0초) */
const TRANSLATE_MODELS = ['gemini-flash-lite-latest', 'gemini-flash-latest'];

/* ────────────────────────── .env 읽기 ────────────────────────── */

/**
 * ⚠ 이 .env 는 `KEY = "value"` 형태(키 뒤 공백 + 따옴표)라
 *   grep '^KEY=' 로는 못 찾는다. '=' 기준 split 후 공백·따옴표를 벗긴다.
 */
function readEnv(name) {
  if (process.env[name]) return process.env[name].trim();
  const candidates = [
    path.resolve(__dirname, '../../../../../.env'), // Contest Workflow/.env
    path.resolve(__dirname, '../../../../.env'),
    path.resolve(__dirname, '../../../.env'),
    path.resolve(__dirname, '../../.env'),
    path.resolve(process.cwd(), '.env'),
  ];
  for (const p of candidates) {
    let raw;
    try { raw = fs.readFileSync(p, 'utf8'); } catch { continue; }
    for (const line of raw.split(/\r?\n/)) {
      const i = line.indexOf('=');
      if (i < 0) continue;
      const k = line.slice(0, i).trim();
      if (k !== name || k.startsWith('#')) continue;
      const v = line.slice(i + 1).trim().replace(/^["']|["']$/g, '').trim();
      if (v) return v;
    }
  }
  return '';
}

const API_KEY = readEnv('GOOGLE_AI_STUDIO_KEY');

/* ────────────────────── 색인 로드 (기동 시 1회) ────────────────────── */

const STOP = new Set(
  ('a an the of to in on for and or as by with be is are was were shall may must not this that these those ' +
   'from at it its such any all other where which who whom their there than then have has had do does did ' +
   'if into per under over within out up no nor so but also can could would should we you they he she i'
  ).split(/\s+/)
);

function tokenize(s) {
  return String(s)
    .toLowerCase()
    .split(/[^a-z0-9가-힣]+/)
    .filter((t) => t.length >= 2 && t.length <= 32 && !STOP.has(t));
}

/**
 * 한국어 도메인 용어 → 영문 법령 용어 사전.
 *
 * 왜 필요한가: 키워드(BM25) 신호를 LLM 번역문에만 의존시키면 번역이 흔들릴 때마다
 * 순위가 같이 흔들린다(실측). 이 사전은 원문 질문에서 **결정적으로** 뽑히므로
 * 검색어의 뼈대를 고정해 준다. 벡터 쪽의 '한국어 원문 임베딩 앵커'와 같은 역할이다.
 */
const GLOSSARY = [
  ['모니터링 계획', 'monitoring plan'],
  ['모니터링', 'monitoring'],
  ['배출권', 'allowances'],
  ['제출 의무', 'surrender obligation'],
  ['제출', 'submit surrender'],
  ['선사', 'shipping company'],
  ['해운회사', 'shipping company'],
  ['선박회사', 'shipping company'],
  ['화주', 'cargo owner'],
  ['항해', 'voyage'],
  ['기항', 'port of call'],
  ['항만', 'port of call'],
  ['항구', 'port of call'],
  ['배출량', 'emissions'],
  ['온실가스', 'greenhouse gas'],
  ['검증인', 'verifier'],
  ['검증기관', 'verifier'],
  ['검증', 'verification'],
  ['보고 기간', 'reporting period'],
  ['보고서', 'report'],
  ['보고', 'reporting'],
  ['선박', 'ship'],
  ['총톤수', 'gross tonnage'],
  ['벌금', 'penalty'],
  ['과태료', 'penalty'],
  ['면제', 'exemption'],
  ['적용 범위', 'scope'],
  ['적용', 'scope application'],
  ['기한', 'deadline'],
  ['언제까지', 'deadline by'],
  ['퍼센트', 'percentage'],
  ['역외', 'outside'],
  ['회원국', 'member state'],
  ['의무', 'obligation'],
  ['준수', 'compliance'],
  ['연료', 'fuel'],
  ['탄소', 'carbon'],
  ['에너지 집약도', 'energy intensity'],
  ['집약도', 'intensity'],
  ['정박', 'at berth'],
  ['접안', 'at berth'],
  ['육상전원', 'onshore power supply ops'],
  ['신고', 'declaration'],
  ['수입업자', 'importer'],
  ['무상할당', 'free allocation'],
  ['무료할당', 'free allocation'],
  ['잉여', 'surplus'],
  ['벌칙', 'penalty'],
  ['등록부', 'registry'],
  ['인증서', 'certificate'],
];

/** 원문 질문에서 결정적으로 뽑아내는 영문 검색어 토큰. */
function glossaryTokens(koQuestion) {
  const s = String(koQuestion);
  const out = [];
  for (const [ko, en] of GLOSSARY) {
    if (s.includes(ko)) out.push(...tokenize(en));
  }
  // 원문에 이미 영문/약어가 섞여 있으면(EEA, ETS, CBAM, MRV…) 그대로 살린다.
  for (const t of tokenize(s)) if (/^[a-z0-9]+$/.test(t)) out.push(t);
  return [...new Set(out)];
}

/** 모듈 로드 시 1회 실행되는 색인 로더. 절대 throw 하지 않는다. */
function loadIndex() {
  const state = {
    ready: false,
    reason: '',
    dims: 0,
    docs: [],
    articles: [],
    embedModel: '',
    bm25: null,
  };

  let raw;
  try {
    raw = fs.readFileSync(INDEX_PATH, 'utf8');
  } catch {
    state.reason = `색인 파일이 없습니다 (${INDEX_PATH}). 먼저 'node server/rag/build-index.mjs' 를 실행하세요.`;
    return state;
  }

  let json;
  try {
    json = JSON.parse(raw);
  } catch (e) {
    state.reason = `색인 파일 파싱 실패: ${e.message}`;
    return state;
  }

  const articles = Array.isArray(json?.articles) ? json.articles : [];
  if (!articles.length) {
    state.reason = '색인에 조문이 없습니다.';
    return state;
  }

  state.docs = json.docs || [];
  state.articles = articles;
  state.dims = json.dims || articles[0]?.vec?.length || 0;
  state.embedModel = json.embedModel || 'gemini-embedding-001';

  // ── BM25 역색인 구축 ──
  const N = articles.length;
  const df = new Map();
  const tfs = new Array(N);
  let totalLen = 0;
  for (let i = 0; i < N; i++) {
    const a = articles[i];
    const toks = tokenize(`${a.heading} ${a.title} ${a.title} ${a.text}`); // 표제에 가중치
    const tf = new Map();
    for (const t of toks) tf.set(t, (tf.get(t) || 0) + 1);
    tfs[i] = tf;
    totalLen += toks.length;
    for (const t of tf.keys()) df.set(t, (df.get(t) || 0) + 1);
  }
  state.bm25 = { df, tfs, N, avgdl: totalLen / Math.max(1, N) };

  if (!API_KEY) {
    state.reason = 'GOOGLE_AI_STUDIO_KEY 가 없습니다. 키워드 검색만 가능합니다.';
    // 색인 자체는 살아 있으므로 ready 로 본다 — 벡터 없이 BM25 로 동작한다.
  }

  state.ready = true;
  return state;
}

const IDX = loadIndex();

/* ──────────────────────────── 점수 계산 ──────────────────────────── */

// 문서 길이는 매 질의마다 다시 셀 필요가 없다 — 1회 계산해 캐시한다.
let DL_CACHE = null;
function docLens() {
  if (DL_CACHE) return DL_CACHE;
  DL_CACHE = IDX.bm25.tfs.map((tf) => {
    let s = 0;
    for (const v of tf.values()) s += v;
    return s;
  });
  return DL_CACHE;
}

/**
 * BM25. 질의어마다 가중치를 받는다.
 * @param {Array<[string, number]>} weightedTerms  [토큰, 가중치] 목록
 */
function bm25ScoresFast(weightedTerms) {
  const { df, tfs, N, avgdl } = IDX.bm25;
  const dls = docLens();
  const k1 = 1.5;
  const b = 0.75;
  const scores = new Float64Array(N);
  for (const [t, w] of weightedTerms) {
    const n = df.get(t);
    if (!n || !w) continue;
    const idf = Math.log(1 + (N - n + 0.5) / (n + 0.5));
    for (let i = 0; i < N; i++) {
      const f = tfs[i].get(t);
      if (!f) continue;
      const denom = f + k1 * (1 - b + (b * dls[i]) / avgdl);
      scores[i] += w * idf * ((f * (k1 + 1)) / denom);
    }
  }
  return scores;
}

function cosineScores(qvec) {
  const arts = IDX.articles;
  const out = new Float64Array(arts.length);
  if (!qvec) return out;
  for (let i = 0; i < arts.length; i++) {
    const v = arts[i].vec;
    if (!v) continue;
    let s = 0;
    const n = Math.min(v.length, qvec.length);
    for (let d = 0; d < n; d++) s += v[d] * qvec[d];
    out[i] = s; // 양쪽 모두 L2 정규화되어 있으므로 내적 = 코사인
  }
  return out;
}

/** min-max 정규화. 전부 같으면 0 을 돌려 해당 신호를 무력화한다. */
function minmax(arr) {
  let min = Infinity;
  let max = -Infinity;
  for (const x of arr) {
    if (x < min) min = x;
    if (x > max) max = x;
  }
  const range = max - min;
  const out = new Float64Array(arr.length);
  if (!(range > 1e-12)) return out;
  for (let i = 0; i < arr.length; i++) out[i] = (arr[i] - min) / range;
  return out;
}

/* ──────────────────────────── Gemini 호출 ──────────────────────────── */

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function embedQuery(text) {
  if (!API_KEY) return null;
  const model = IDX.embedModel || 'gemini-embedding-001';
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:embedContent?key=${API_KEY}`;
  const body = JSON.stringify({
    model: `models/${model}`,
    content: { parts: [{ text }] },
    taskType: 'RETRIEVAL_QUERY',
    outputDimensionality: IDX.dims || 768,
  });

  for (let attempt = 0; attempt < 4; attempt++) {
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body,
        signal: AbortSignal.timeout(30_000),
      });
      if (res.status === 429 || res.status >= 500) {
        await sleep(800 * 2 ** attempt);
        continue;
      }
      const j = await res.json();
      if (!res.ok) return null;
      const vals = j?.embedding?.values;
      if (!Array.isArray(vals)) return null;
      // ⚠ gemini-embedding-001 은 768차원에서 정규화되지 않은 벡터를 준다. 직접 정규화한다.
      let s = 0;
      for (const x of vals) s += x * x;
      const nrm = Math.sqrt(s);
      return nrm > 0 ? vals.map((x) => x / nrm) : vals;
    } catch {
      await sleep(600 * 2 ** attempt);
    }
  }
  return null;
}

async function generate(prompt, { maxOutputTokens = 8192, models = GEN_MODELS } = {}) {
  if (!API_KEY) return null;
  for (const model of models) {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${API_KEY}`;
    const body = JSON.stringify({
      contents: [{ role: 'user', parts: [{ text: prompt }] }],
      // temperature 0 — 같은 질문에 같은 답이 나와야 한다(재현성).
      generationConfig: { temperature: 0, topP: 1, maxOutputTokens },
    });
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        const res = await fetch(url, {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body,
          signal: AbortSignal.timeout(60_000),
        });
        if (res.status === 429 || res.status >= 500) {
          await sleep(900 * 2 ** attempt);
          continue;
        }
        const j = await res.json();
        if (!res.ok) break; // 다음 모델로
        const txt = (j?.candidates?.[0]?.content?.parts || [])
          .map((p) => p.text || '')
          .join('')
          .trim();
        if (txt) return txt;
        break;
      } catch {
        await sleep(700 * 2 ** attempt);
      }
    }
  }
  return null;
}

/**
 * 한→영 질의 변환. 실패해도 원문으로 진행한다.
 *
 * ⚠ LLM 번역은 temperature=0 이어도 결정적이지 않다(같은 문장 5회에 서로 다른 출력 4~5종 실측).
 *   그래서 (1) 결과를 캐시해 같은 질문은 같은 번역을 쓰고,
 *        (2) 검색 벡터는 한국어 원문 임베딩으로 고정 앵커를 잡는다(ragAsk 참조).
 */
const TRANSLATION_CACHE = new Map();

async function toEnglish(question) {
  if (!/[가-힣]/.test(question)) return { queryEn: question, translated: false };

  const cacheKey = question.replace(/\s+/g, ' ').trim();
  if (TRANSLATION_CACHE.has(cacheKey)) {
    return { queryEn: TRANSLATION_CACHE.get(cacheKey), translated: true, cached: true };
  }

  const prompt =
    'Convert the Korean question into one short English search query for retrieving articles from EU ' +
    'maritime regulations (EU ETS Directive 2003/87/EC, FuelEU Maritime, MRV Regulation 2015/757, CBAM).\n' +
    'Rules: use the exact official terminology of those acts (monitoring plan, surrender allowances, ' +
    'shipping company, port of call, voyage, verifier, reporting period); keep it under 20 words; ' +
    'output ONLY the query text with no quotes and no explanation.\n\n' +
    'Korean: 모니터링 계획은 언제까지 제출해야 하나요?\n' +
    'Query: deadline for submitting the monitoring plan to the verifier\n\n' +
    'Korean: 배출권 제출 의무자는 선사인가요 화주인가요?\n' +
    'Query: which entity is responsible for surrendering allowances shipping company or cargo owner\n\n' +
    `Korean: ${question}\nQuery:`;

  const out = await generate(prompt, { maxOutputTokens: 2048, models: TRANSLATE_MODELS });
  if (!out) return { queryEn: question, translated: false };

  const clean = (out.split('\n').map((s) => s.trim()).filter(Boolean)[0] || question)
    .replace(/^(query|답|answer)\s*:\s*/i, '')
    .replace(/^["']|["']$/g, '')
    .trim();
  const queryEn = clean || question;

  if (TRANSLATION_CACHE.size < 500) TRANSLATION_CACHE.set(cacheKey, queryEn);
  return { queryEn, translated: true };
}

/* ──────────────────────────── 공개 API ──────────────────────────── */

/**
 * @returns {{ready:boolean, docs:number, articles:number, dims:number}}
 */
export function ragStatus() {
  return {
    ready: Boolean(IDX.ready),
    docs: IDX.docs?.length || 0,
    articles: IDX.articles?.length || 0,
    dims: IDX.dims || 0,
    // 부가 정보(계약 외) — 화면 표시/디버깅용
    embedModel: IDX.embedModel || null,
    hasApiKey: Boolean(API_KEY),
    reason: IDX.reason || null,
  };
}

/**
 * 질문 → 하이브리드 검색 → 근거 기반 답변.
 * 절대 throw 하지 않는다.
 *
 * @param {{question:string, k?:number}} params
 */
export async function ragAsk({ question, k = 4 } = {}) {
  const q = String(question ?? '').trim();
  const topK = Math.max(1, Math.min(10, Number(k) || 4));

  const fail = (msg) => ({
    queryEn: q,
    hits: [],
    result: { answer: msg, used: [], caveat: CAVEAT },
  });

  if (!q) return fail('질문이 비어 있습니다.');

  // 색인 미준비 — 예외를 던지지 않고 상태를 담아 돌려준다.
  if (!IDX.ready) {
    return {
      queryEn: q,
      hits: [],
      result: {
        answer: `조문 색인이 준비되지 않았습니다. ${IDX.reason || ''}`.trim(),
        used: [],
        caveat: CAVEAT,
      },
      status: ragStatus(),
    };
  }

  try {
    // 1) 한→영 질의 변환 (실패해도 원문으로 진행)
    const { queryEn, translated } = await toEnglish(q);

    // 2) 하이브리드 검색
    //
    // 벡터 신호는 [한국어 원문 + 영어 번역] 두 임베딩의 평균으로 만든다.
    //  - 임베딩 API 자체는 완전히 결정적이다(같은 문장 3회 cos=1.00000000 실측).
    //  - 반면 LLM 번역문은 매번 흔들린다. 영어 번역만 쓰면 그 흔들림이 순위로 새어나온다
    //    (번역 5종에 상위4 조합이 3종으로 갈렸다).
    //  - 한국어 원문 임베딩을 고정 앵커로 섞으면 같은 조건에서 상위4가 1종으로 고정됐고,
    //    정답 조문(ETS Art 3ga)이 1위로 올라와 정확도까지 좋아졌다.
    //    gemini-embedding-001 은 다국어 모델이라 한↔영 교차 검색이 성립한다.
    const needsBoth = translated && queryEn !== q;
    const [qvecKo, qvecEn] = await Promise.all([
      embedQuery(q),
      needsBoth ? embedQuery(queryEn) : Promise.resolve(null),
    ]);

    let qvec = null;
    if (qvecKo && qvecEn) {
      const a = W_QVEC_KO;
      const sum = qvecKo.map((x, i) => a * x + (1 - a) * qvecEn[i]);
      let s = 0;
      for (const x of sum) s += x * x;
      const nrm = Math.sqrt(s);
      qvec = nrm > 0 ? sum.map((x) => x / nrm) : sum;
    } else {
      qvec = qvecEn || qvecKo;
    }

    const vecRaw = cosineScores(qvec);

    // 키워드 신호 = [원문에서 사전으로 뽑은 결정적 용어(가중 1.0)]
    //             + [번역문에만 있는 토큰(가중 W_KW_TRANSLATED)].
    // 코퍼스가 영문이라 한글 토큰 자체는 넣지 않는다(잡음만 는다).
    const gTokens = glossaryTokens(q);
    const gSet = new Set(gTokens);
    const terms = new Map();
    for (const t of gTokens) terms.set(t, 1.0);
    for (const t of tokenize(queryEn)) if (!gSet.has(t)) terms.set(t, W_KW_TRANSLATED);
    const kwRaw = bm25ScoresFast([...terms]);

    const vecN = minmax(vecRaw);
    const kwN = minmax(kwRaw);

    const fused = IDX.articles.map((a, i) => ({
      i,
      score: W_VEC * vecN[i] + W_KW * kwN[i],
      vecScore: vecRaw[i],
      kwScore: kwRaw[i],
    }));
    // 동점 시 id 로 안정 정렬 — 재현성 확보
    fused.sort((x, y) => y.score - x.score || IDX.articles[x.i].id.localeCompare(IDX.articles[y.i].id));

    const top = fused.slice(0, topK);
    const hits = top.map((t) => {
      const a = IDX.articles[t.i];
      return {
        id: a.id,
        doc: a.doc,
        cite: a.cite,
        score: +t.score.toFixed(4),
        excerpt: a.excerpt,
        // 부가 정보(계약 외)
        title: a.title,
        url: a.url,
        vecScore: +t.vecScore.toFixed(4),
        kwScore: +t.kwScore.toFixed(4),
      };
    });

    // 3) 상위 k 조문만 근거로 인용 전용 답변 생성
    const evidence = top
      .map((t, n) => {
        const a = IDX.articles[t.i];
        const head = a.title ? `${a.heading} — ${a.title}` : a.heading;
        return `[${n + 1}] ${a.cite} (${head})\n${a.text.slice(0, 4000)}`;
      })
      .join('\n\n---\n\n');

    const prompt =
      '당신은 EU 해운 규제 조문 안내 도우미다. 아래 <근거> 안의 조문 내용만 사용해 한국어로 답한다.\n\n' +
      '규칙:\n' +
      '1. <근거>에 없는 내용은 절대 추측하거나 일반 지식으로 보충하지 않는다.\n' +
      `2. <근거>로 답할 수 없으면 다른 말을 덧붙이지 말고 정확히 이 문장만 출력한다: ${NOT_FOUND}\n` +
      '3. 답변 문장에는 근거 번호를 [1] [2] 형식으로 표기한다. 실제로 사용한 번호만 쓴다.\n' +
      '4. 3~5문장으로 간결하게. 날짜·비율·기한 같은 수치는 조문에 있는 값을 그대로 옮긴다.\n' +
      '5. 조문 번호(예: Article 6)를 문장 안에 함께 언급한다.\n\n' +
      `<질문>\n${q}\n(영문 검색 질의: ${queryEn})\n</질문>\n\n` +
      `<근거>\n${evidence}\n</근거>\n\n답변:`;

    let answer = await generate(prompt, { maxOutputTokens: 8192 });

    if (!answer) {
      return {
        queryEn,
        translated,
        hits,
        result: {
          answer: '답변 생성에 실패했습니다. 아래 검색된 조문을 직접 확인해 주세요.',
          used: [],
          caveat: CAVEAT,
        },
      };
    }

    answer = answer.trim();

    // 근거 부족 판정 — 정확히 지정된 문자열로 정규화한다.
    const refused = answer.includes(NOT_FOUND);
    if (refused) {
      return {
        queryEn,
        translated,
        hits,
        result: { answer: NOT_FOUND, used: [], caveat: CAVEAT },
      };
    }

    // 본문에 실제로 등장한 인용 번호만 used 로 집계
    const used = [...new Set([...answer.matchAll(/\[(\d+)\]/g)].map((m) => +m[1]))]
      .filter((n) => n >= 1 && n <= hits.length)
      .sort((a, b) => a - b);

    return {
      queryEn,
      translated,
      hits,
      result: { answer, used, caveat: CAVEAT },
    };
  } catch (e) {
    // 어떤 경우에도 서버를 죽이지 않는다.
    return fail(`검색 중 오류가 발생했습니다: ${e?.message || e}`);
  }
}

export default { ragAsk, ragStatus };

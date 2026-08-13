#!/usr/bin/env node
/**
 * build-index.mjs — EU 해운 규정 조문 색인 빌더
 *
 * 원문 수집(EUR-Lex) → 조(Article) 단위 청킹 → 임베딩(Gemini) → index.json 저장
 *
 * 사용법:
 *   node server/rag/build-index.mjs              # 전체 빌드
 *   node server/rag/build-index.mjs --limit 5    # 문서당 5개 조문만 (빠른 검증)
 *   node server/rag/build-index.mjs --dry        # 임베딩 없이 파싱만 (조문 수 확인)
 *
 * 의존성 없음 — Node 22 내장 fetch 만 사용한다.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_PATH = path.join(__dirname, 'index.json');

/* ────────────────────────────── 설정 ────────────────────────────── */

const EMBED_MODEL = 'gemini-embedding-001'; // text-embedding-004 는 폐기됨(404)
const DIMS = 768;
const CONCURRENCY = 4;
const EMBED_INPUT_MAX = 6000; // 임베딩 입력 상한(문자). 모델 토큰 한도 여유분.
const EXCERPT_LEN = 320; // 화면 표시용 발췌 길이
const TEXT_STORE_MAX = 6000; // 근거로 LLM 에 넘길 본문 저장 상한

/**
 * 대상 문서.
 * maxArticleNum — 상호참조 방어용 상한. 본문에 인용된 다른 법령의 조 번호
 * (예: MRV 본문의 "Article 192 TFEU")를 자기 조문으로 잡는 사고를 막는다.
 * 각 법령의 실제 마지막 조 번호를 넣는다.
 */
const DOCS = [
  {
    key: 'ETS',
    celex: '02003L0087-20240301',
    title: 'EU ETS 지침 통합본 (Directive 2003/87/EC, consolidated 2024-03-01)',
    citeBase: 'Directive 2003/87/EC',
    maxArticleNum: 33,
  },
  {
    key: 'FuelEU',
    celex: '32023R1805',
    title: 'FuelEU Maritime (Regulation (EU) 2023/1805)',
    citeBase: 'Regulation (EU) 2023/1805',
    maxArticleNum: 32,
  },
  {
    key: 'MRV',
    celex: '32015R0757',
    title: 'MRV 해운 (Regulation (EU) 2015/757)',
    citeBase: 'Regulation (EU) 2015/757',
    maxArticleNum: 26,
  },
  {
    key: 'CBAM',
    celex: '32023R0956',
    title: 'CBAM (Regulation (EU) 2023/956)',
    citeBase: 'Regulation (EU) 2023/956',
    maxArticleNum: 36,
  },
];

/* ──────────────────────────── 유틸 ──────────────────────────── */

/**
 * .env 읽기.
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

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function decodeEntities(s) {
  return s
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCodePoint(parseInt(h, 16)))
    .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(+d))
    .replace(/&[a-z]+;/gi, ' ');
}

/** HTML → 평문. 블록 요소는 줄바꿈으로 바꿔 문단 구분을 살린다. */
function stripTags(html) {
  let s = html.replace(/<(script|style)\b[\s\S]*?<\/\1>/gi, ' ');
  s = s.replace(/<\/(p|div|tr|li|h[1-6]|td|th)>/gi, '\n');
  s = s.replace(/<br\s*\/?>/gi, '\n');
  s = s.replace(/<[^>]+>/g, '');
  return decodeEntities(s)
    .replace(/[ \t ]+/g, ' ')
    .split('\n')
    .map((x) => x.trim())
    .filter(Boolean)
    .join('\n')
    .trim();
}

/** startIdx 의 <div> 에 대응하는 </div> 까지를 깊이 인식으로 잘라낸다. */
function sliceDiv(html, startIdx) {
  const re = /<div\b|<\/div>/gi;
  re.lastIndex = startIdx;
  let depth = 0;
  let m;
  while ((m = re.exec(html))) {
    if (m[0][1] === '/') {
      depth--;
      if (depth === 0) return html.slice(startIdx, m.index);
    } else {
      depth++;
    }
  }
  return html.slice(startIdx);
}

/* ─────────────────────── 원문 수집 · 조문 파싱 ─────────────────────── */

async function fetchDoc(celex) {
  const url = `https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?uri=CELEX:${celex}`;
  let lastErr;
  for (let attempt = 0; attempt < 4; attempt++) {
    try {
      const res = await fetch(url, {
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) GlovisHEDGE-RAG/1.0',
          'Accept-Language': 'en',
        },
        signal: AbortSignal.timeout(60_000),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const html = await res.text();
      if (html.length < 5000) throw new Error(`본문이 너무 짧음 (${html.length}B)`);
      return html;
    } catch (e) {
      lastErr = e;
      await sleep(1500 * (attempt + 1));
    }
  }
  throw new Error(`EUR-Lex 수집 실패 ${celex}: ${lastErr?.message}`);
}

/**
 * 조(Article) 단위 파싱.
 *
 * EUR-Lex HTML 은 두 가지 마크업 방언을 쓴다. 둘 다 조문 컨테이너는
 * <div class="eli-subdivision" id="art_N"> 로 동일하다.
 *   - OJ 원본 (32015R0757 등):  p.oj-ti-art / p.oj-sti-art / p.oj-normal
 *   - 통합본 (02003L0087-…):    p.title-article-norm / p.stitle-article-norm / p.norm
 *
 * ⚠ 문자수 윈도우로 자르지 않는다. 구조 앵커(id="art_…")로만 자르므로
 *   본문에 인용된 다른 법령 조항("Article 192 TFEU" 등)은 애초에 잡히지 않는다.
 */
function parseArticles(html, doc) {
  const found = [];
  const dropped = [];
  const re = /<div[^>]*class="[^"]*eli-subdivision[^"]*"[^>]*id="(art_[^"]+)"[^>]*>/gi;
  const seen = new Set();
  let m;

  while ((m = re.exec(html))) {
    const rawId = m[1];
    if (rawId.includes('.')) continue; // art_6.tit_1 같은 하위 앵커는 제외
    const artNo = rawId.replace(/^art_/, '');

    // 조번호 파싱: "3gb" → num 3, suffix "gb" / "30m" → num 30, suffix "m"
    const nm = artNo.match(/^(\d+)([a-z]*)$/i);
    if (!nm) { dropped.push({ artNo, reason: '조번호 형식 불일치' }); continue; }
    const num = parseInt(nm[1], 10);
    const suffix = (nm[2] || '').toLowerCase();

    // 상호참조 방어: 문서별 최대 조 번호를 넘으면 버린다.
    if (num > doc.maxArticleNum) {
      dropped.push({ artNo, reason: `상한 초과 (>${doc.maxArticleNum})` });
      continue;
    }
    if (seen.has(artNo)) continue;

    const block = sliceDiv(html, m.index);
    const hm = block.match(
      /<p[^>]*class="[^"]*(?:oj-ti-art|title-article-norm)[^"]*"[^>]*>([\s\S]*?)<\/p>/i
    );
    const tm = block.match(
      /<p[^>]*class="[^"]*(?:oj-sti-art|stitle-article-norm)[^"]*"[^>]*>([\s\S]*?)<\/p>/i
    );

    let bodyHtml = block;
    if (hm) bodyHtml = bodyHtml.replace(hm[0], ' ');
    if (tm) bodyHtml = bodyHtml.replace(tm[0], ' ');

    const heading = hm ? stripTags(hm[1]) : `Article ${artNo}`;
    const title = tm ? stripTags(tm[1]) : '';
    const body = stripTags(bodyHtml);

    // 삭제·폐지된 빈 조문은 버린다.
    if (body.replace(/\s/g, '').length < 40) {
      dropped.push({ artNo, reason: '본문 없음(삭제된 조)' });
      continue;
    }

    seen.add(artNo);
    found.push({
      id: `${doc.celex}#art${artNo}`,
      doc: doc.key,
      celex: doc.celex,
      art: artNo,
      num,
      suffix,
      // cite 예: "Regulation (EU) 2015/757, Article 6"
      cite: `${doc.citeBase}, Article ${artNo}`,
      heading: /^article/i.test(heading) ? heading : `Article ${artNo}`,
      title,
      text: body.slice(0, TEXT_STORE_MAX),
      excerpt: body.replace(/\n/g, ' ').slice(0, EXCERPT_LEN),
      url: `https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:${doc.celex}#${rawId}`,
    });
  }

  // 조 번호 순 정렬 (3 → 3a → 3b → 4)
  found.sort((a, b) => a.num - b.num || a.suffix.localeCompare(b.suffix));
  return { articles: found, dropped };
}

/** 임베딩 입력 문자열. 조번호와 표제를 앞에 붙여야 검색 품질이 산다. */
function embedText(a) {
  const head = a.title ? `${a.heading} — ${a.title}` : a.heading;
  return `${head} (${a.cite})\n\n${a.text}`.slice(0, EMBED_INPUT_MAX);
}

/* ──────────────────────────── 임베딩 ──────────────────────────── */

function l2normalize(v) {
  let s = 0;
  for (const x of v) s += x * x;
  const n = Math.sqrt(s);
  if (!n || !isFinite(n)) return v;
  return v.map((x) => x / n);
}

/**
 * ⚠ gemini-embedding-001 은 outputDimensionality<3072 일 때 정규화되지 않은
 *   벡터를 준다(768차원 실측 L2=0.586). 반드시 직접 L2 정규화해야
 *   내적=코사인유사도가 성립한다.
 */
async function embedOne(text, key, taskType = 'RETRIEVAL_DOCUMENT') {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${EMBED_MODEL}:embedContent?key=${key}`;
  const body = JSON.stringify({
    model: `models/${EMBED_MODEL}`,
    content: { parts: [{ text }] },
    taskType,
    outputDimensionality: DIMS,
  });

  let lastErr;
  for (let attempt = 0; attempt < 6; attempt++) {
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body,
        signal: AbortSignal.timeout(60_000),
      });
      if (res.status === 429 || res.status >= 500) {
        // 지수 백오프
        const wait = Math.min(30_000, 1200 * 2 ** attempt) + Math.random() * 500;
        await sleep(wait);
        lastErr = new Error(`HTTP ${res.status}`);
        continue;
      }
      const j = await res.json();
      if (!res.ok) throw new Error(j?.error?.message || `HTTP ${res.status}`);
      const vals = j?.embedding?.values;
      if (!Array.isArray(vals) || vals.length !== DIMS) {
        throw new Error(`임베딩 차원 이상: ${vals?.length}`);
      }
      return l2normalize(vals);
    } catch (e) {
      lastErr = e;
      if (attempt < 5) await sleep(1000 * 2 ** attempt);
    }
  }
  throw new Error(`임베딩 실패: ${lastErr?.message}`);
}

/** 동시 실행 풀 */
async function pool(items, size, worker) {
  const out = new Array(items.length);
  let idx = 0;
  let done = 0;
  const runners = Array.from({ length: Math.min(size, items.length) }, async () => {
    while (idx < items.length) {
      const i = idx++;
      out[i] = await worker(items[i], i);
      done++;
      if (done % 10 === 0 || done === items.length) {
        process.stdout.write(`\r    임베딩 ${done}/${items.length}`);
      }
    }
  });
  await Promise.all(runners);
  process.stdout.write('\n');
  return out;
}

/* ──────────────────────────── 메인 ──────────────────────────── */

async function main() {
  const args = process.argv.slice(2);
  const dry = args.includes('--dry');
  const limitArg = args.indexOf('--limit');
  const limit = limitArg >= 0 ? parseInt(args[limitArg + 1], 10) : 0;

  const key = readEnv('GOOGLE_AI_STUDIO_KEY');
  if (!key && !dry) {
    console.error('✗ GOOGLE_AI_STUDIO_KEY 를 찾지 못했습니다. --dry 로 파싱만 확인할 수 있습니다.');
    process.exit(1);
  }

  console.log('══════════════════════════════════════════════════════════');
  console.log(' EU 해운 규정 조문 색인 빌드');
  console.log(`  임베딩 모델: ${EMBED_MODEL} (${DIMS}차원)${dry ? '  [DRY RUN — 임베딩 생략]' : ''}`);
  console.log('══════════════════════════════════════════════════════════\n');

  const allArticles = [];
  const perDoc = [];
  let totalDropped = 0;
  const dropDetail = [];

  for (const doc of DOCS) {
    process.stdout.write(`▸ ${doc.key.padEnd(7)} CELEX:${doc.celex} … 수집 중`);
    let html;
    try {
      html = await fetchDoc(doc.celex);
    } catch (e) {
      console.log(`\n  ✗ ${e.message}`);
      perDoc.push({ key: doc.key, celex: doc.celex, articles: 0, error: e.message });
      continue;
    }
    process.stdout.write(` (${(html.length / 1024).toFixed(0)}KB) → 파싱`);

    const { articles, dropped } = parseArticles(html, doc);
    let picked = articles;
    if (limit > 0) picked = picked.slice(0, limit);

    totalDropped += dropped.length;
    if (dropped.length) dropDetail.push({ doc: doc.key, dropped });

    console.log(`\n  ✓ 조문 ${picked.length}개  (버림 ${dropped.length}개)`);
    perDoc.push({
      key: doc.key,
      celex: doc.celex,
      title: doc.title,
      citeBase: doc.citeBase,
      articles: picked.length,
      droppedCount: dropped.length,
    });
    allArticles.push(...picked);
  }

  console.log('\n──────────────── 문서별 조문 수 ────────────────');
  for (const d of perDoc) {
    console.log(`  ${d.key.padEnd(8)} ${String(d.articles).padStart(4)}개   ${d.error ? '✗ ' + d.error : ''}`);
  }
  console.log('  ' + '─'.repeat(30));
  console.log(`  ${'합계'.padEnd(7)} ${String(allArticles.length).padStart(4)}개`);
  if (totalDropped) {
    console.log(`\n  상호참조/빈조문 필터로 버린 조문: ${totalDropped}개`);
    for (const d of dropDetail) {
      const byReason = {};
      for (const x of d.dropped) byReason[x.reason] = (byReason[x.reason] || 0) + 1;
      console.log(`    ${d.doc}: ` + Object.entries(byReason).map(([r, c]) => `${r} ${c}`).join(', '));
      console.log(`      → ${d.dropped.map((x) => x.artNo).join(', ')}`);
    }
  }
  console.log('────────────────────────────────────────────────\n');

  if (dry) {
    console.log('DRY RUN 종료 — index.json 을 쓰지 않았습니다.');
    return;
  }
  if (!allArticles.length) {
    console.error('✗ 조문을 하나도 얻지 못했습니다. 색인을 만들지 않습니다.');
    process.exit(1);
  }

  console.log(`▸ 임베딩 ${allArticles.length}건 (동시 ${CONCURRENCY})`);
  const t0 = Date.now();
  const vectors = await pool(allArticles, CONCURRENCY, async (a) => {
    try {
      return await embedOne(embedText(a), key, 'RETRIEVAL_DOCUMENT');
    } catch (e) {
      console.log(`\n  ✗ ${a.id}: ${e.message}`);
      return null;
    }
  });
  const secs = ((Date.now() - t0) / 1000).toFixed(1);

  const kept = [];
  let failed = 0;
  allArticles.forEach((a, i) => {
    if (!vectors[i]) { failed++; return; }
    kept.push({
      id: a.id,
      doc: a.doc,
      celex: a.celex,
      art: a.art,
      cite: a.cite,
      heading: a.heading,
      title: a.title,
      text: a.text,
      excerpt: a.excerpt,
      url: a.url,
      // 소수 6자리로 반올림 — 색인 크기를 절반 이하로 줄인다(검색 품질 영향 없음)
      vec: vectors[i].map((x) => +x.toFixed(6)),
    });
  });

  console.log(`  ✓ ${kept.length}건 완료, 실패 ${failed}건, ${secs}초\n`);

  const index = {
    version: 1,
    builtAt: new Date().toISOString(),
    embedModel: EMBED_MODEL,
    dims: DIMS,
    normalized: true,
    source: 'EUR-Lex (eur-lex.europa.eu) — 조(Article) 단위 구조 앵커 파싱',
    docs: perDoc.map((d) => ({
      key: d.key, celex: d.celex, title: d.title, citeBase: d.citeBase, articles: d.articles,
    })).filter((d) => d.articles > 0),
    articles: kept,
  };

  fs.writeFileSync(OUT_PATH, JSON.stringify(index));
  const bytes = fs.statSync(OUT_PATH).size;
  console.log('══════════════════════════════════════════════════════════');
  console.log(` 색인 저장: ${OUT_PATH}`);
  console.log(` 크기: ${(bytes / 1024 / 1024).toFixed(2)} MB   조문 ${kept.length}개 × ${DIMS}차원`);
  console.log('══════════════════════════════════════════════════════════');
  if (bytes > 20 * 1024 * 1024) {
    console.warn('⚠ 20MB 초과 — EXCERPT_LEN / TEXT_STORE_MAX 를 줄이세요.');
  }
}

main().catch((e) => {
  console.error('✗ 빌드 실패:', e);
  process.exit(1);
});

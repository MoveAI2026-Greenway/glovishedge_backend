# PROVISIONAL 백엔드 결정 사항

이 문서는 명세가 확정되지 않은 상태에서 **사용자 승인을 받아 임시로 채택한 구현 결정**을
정리한다. "확정 명세"가 아니라 "지금 실제로 동작하게 만들기 위한 임시 결정"이다.

여기 적힌 어떤 것도 `docs/specs/*`, `docs/GlovisHEDGE_BACKEND_DB_IMPLEMENTATION_GUIDE.md`
원문을 대체하지 않는다. 팀이 최종 결정을 내리면 아래 "교체 포인트"만 보고 바꾸면 된다.

---

## A. `GET /api/sea-distance` — searoute-ts 실행 위치

**결정**: 별도 Node sidecar(`searoute-service/`)에서 `searoute-ts@2.2.0`을 실행하고,
Spring Boot가 내부 HTTP(`POST /route`)로 호출한다.

**구조**:
```
Browser → Spring Boot GET /api/sea-distance → (internal HTTP) → searoute-service POST /route → searoute-ts
```

**왜**: 현재 API 계약이 임의 좌표(`fromLng/fromLat/toLng/toLat`)를 받으므로 사전 계산 방식은
계약과 맞지 않는다. Java 재구현은 검증 비용이 과도하다. searoute-ts 결과를 그대로 써야
distance와 path가 항상 같은 계산에서 나온다는 불변조건을 가장 쉽게 지킬 수 있다.

**검증**: 실제 searoute-ts@2.2.0로 부산→함부르크 golden 값을 재현했다 —
A/B **11258.91nm**, D **11316.76nm**, C(수에즈·바브엘만데브·파나마 차단) **14548.64nm**.
`02_계산명세.md`의 11259/11317/14549와 오차 1nm 미만. `searoute-service/test/route.test.js`에서
자동 검증한다.

**교체 포인트**: 팀이 다른 방식(사전계산+DB, Java 재구현 등)을 확정하면
`com.glovishedge.searoute.provider.SeaRouteProvider` 인터페이스의 새 구현체만 추가하고
`NodeSeaRouteProvider` 빈 등록을 걷어내면 된다. `SeaDistanceService`/`SeaDistanceController`는
그대로 둔다.

---

## B. `sea-distance`의 path 저장 위치

**결정**: DB에 저장하지 않는다. 매 요청마다 Node sidecar 호출 결과(nm+path)를 그대로 반환한다.
`route_distances` 테이블/migration은 이번에 추가하지 않았다.

**왜**: DB 저장 방식(JSONB vs 별도 리소스, unique 조건 등)이 여전히 미확정이고, 매 요청
계산 비용이 크지 않아(searoute-ts 캐시 덕분에 반복 호출은 빠름) 저장 없이도 실제로 동작한다.

**교체 포인트**: 캐싱/영속화가 필요하다고 팀이 결정하면 `SeaDistanceService`에
cache-aside 계층 하나만 추가하면 된다. `SeaRouteProvider` 인터페이스는 그대로 재사용 가능.

---

## C. LLM Gateway

**결정**: 특정 vendor SDK(OpenAI/Claude/Gemini)를 도메인 코드에 넣지 않는다.
`com.glovishedge.ai.client.LlmGatewayClient` 인터페이스 + 환경변수 기반 HTTP adapter
(`HttpGatewayLlmClient`)로 구현했다.

**환경변수**:
- `LLM_GATEWAY_URL` — 설정된 경우에만 `HttpGatewayLlmClient` 빈이 등록된다
- `LLM_GATEWAY_API_KEY`
- `LLM_MODEL`

**PROVISIONAL 요청/응답 계약** (실제 gateway 계약이 명세 어디에도 없어 이 adapter가 임시로
정의함 — `com.glovishedge.ai.client.LlmGatewayResponse`):
```
요청: POST {LLM_GATEWAY_URL}
      Authorization: Bearer {LLM_GATEWAY_API_KEY}
      { "model": "{LLM_MODEL}", "system": "...", "message": "..." }

응답: { "content": "..." }             // 성공
      { "error": "..." }               // 실패 — HTTP status와 무관하게 실패 처리
```

**미설정 시 동작**: `Optional<LlmGatewayClient>`가 비어 애플리케이션은 정상 기동하고,
LLM이 필요한 endpoint(`compare-routes`, `chat`, `rag/ask`)만 호출 시 503을 반환한다.

**교체 포인트**: 실제 provider가 정해지면 `HttpGatewayLlmClient`를 그 provider 전용
adapter로 교체(또는 새 구현체 추가 후 기존 빈 제거)하면 된다. `CompareRoutesService`,
`ChatService`/각 `BotHandler`, `RagService`는 `LlmGatewayClient` 인터페이스만 알아서
전혀 손댈 필요가 없다.

---

## D. `ChatResponse` PROVISIONAL 계약

**결정**: `03_API계약.md` §5에 request 계약(`botId`/`message`/`ctx`)은 있지만 response
스키마 예시가 없어(다른 6개 endpoint와 다름), 아래 최소 구조를 임시 채택했다.

```json
{
  "bot": "risk",
  "answer": "...",
  "citations": [],
  "data": {}
}
```

- `bot` — 실제 처리한 bot (auto가 위임한 경우 위임받은 bot id)
- `answer` — 사용자에게 보여줄 답변 텍스트
- `citations` — reg 등 출처가 있을 때만 채움(`com.glovishedge.rag.dto.Citation` 재사용)
- `data` — deterministic 계산 결과 등 구조화 데이터. 없으면 빈 맵

오류는 이 바디에 담지 않고 HTTP status + 예외 처리로만 전달한다(`InvalidChatRequestException`
→ 400, `ChatUnavailableException`/`LlmGatewayException` → 503).

**교체 포인트**: 팀이 실제 계약을 확정하면 `com.glovishedge.chat.dto.ChatResponse`와
`ChatController`만 바꾸면 된다 — 7개 `BotHandler` 내부 로직(파싱·검증·위임)은 영향받지 않도록
설계했다.

---

## E. EUA `isLive` / `recentCloses`

**결정**:
- `isLive` — fresh Yahoo 호출 성공 또는 30분 이내 DB cache면 `true`. 실패 후 stale
  fallback이면 `false`. `03_API계약.md` §1의 계약 모호성(정상 응답 예시엔 없지만 실패 시
  "isLive:false를 내려보내라"는 문구는 있음) 중 선택지 A를 채택했다.
- `recentCloses` — 최근 **5 거래일** 종가(시간순, 과거→현재). Yahoo 요청의 `range=5d`와
  맞춘 개수다. 명세 어디에도 정확한 개수가 없어 이 값으로 정했다.

**교체 포인트**: `com.glovishedge.market.eua.service.EuaService`의
`RECENT_CLOSES_COUNT` 상수와 `isLive` 계산 분기 두 곳만 바꾸면 된다. DB 스키마는
변경하지 않았으므로(migration 없음) 다른 결정으로 바뀌어도 스키마 마이그레이션이 필요 없다.

**추가로 실제 조사해 고친 것(PROVISIONAL 아님, 사실 확인)**: Yahoo Finance 비공식 chart
endpoint(`query1.finance.yahoo.com`)는 User-Agent 헤더가 없는 요청을 429(rate limit)로
거부한다 — 실측 확인(헤더 없이 429, 일반 브라우저 User-Agent 첨부 시 200). symbol(`CO2.L`)과
host는 그대로 두고, `EuaMarketClient`의 요청에만 일반적인 User-Agent/Accept 헤더를 최소한으로
추가했다. 다른 EUA provider로 바꾸지 않았다.

---

## F. RAG — pgvector / corpus / embedding

**결정**: `RagRetriever`/`EmbeddingClient`/`LlmGatewayClient` 셋 다 `Optional`로 주입하고,
전부 갖춰진 경우에만 실제로 동작하는 구조로 완성했다. 이 환경(로컬 Windows PostgreSQL)에는
**pgvector extension이 설치돼 있지 않다**(`pg_available_extensions`로 실측 확인 —
설치 가능 목록에조차 없음). `regulation_chunks` 테이블도 없고, 실제 법령 코퍼스도 이
저장소 어디에도 없다.

**그래서**:
- `db/migration/`(Flyway 활성 위치)에 pgvector/regulation_chunks migration을 넣지 않았다.
  대신 `db/migration-optional/V5__enable_pgvector_and_regulation_chunks.sql`에 참고용으로만
  둔다 — 이 파일은 Flyway가 스캔하지 않는다(`spring.flyway.locations: classpath:db/migration`).
- `PgVectorRagRetriever`는 실제 코드로 완성했지만 `@ConditionalOnProperty(name =
  "RAG_ENABLED", havingValue = "true")`로 막아 이 환경에서는 결코 빈으로 등록되지 않는다.
  JPA `@Entity`로 매핑하지 않고 `JdbcTemplate` 원시 쿼리만 쓴다 — Entity로 매핑했다면
  `ddl-auto=validate`가 존재하지 않는 테이블 때문에 **애플리케이션 전체가 기동 실패**했을
  것이다.
- corpus가 0건이면(retriever가 활성화돼도) "제공된 조문에서는 확인되지 않습니다"를
  반환하고 LLM을 호출하지 않는다 — 없는 규정을 만들어내지 않는다.

**실제 활성화 절차** (팀이 pgvector를 준비하면):
1. PostgreSQL에 pgvector extension을 설치한다.
2. `db/migration-optional/V5__...sql`을 `db/migration/`으로 옮긴다.
3. `RAG_ENABLED=true` 환경변수를 설정한다.
4. `EMBEDDING_GATEWAY_URL`(+ `EMBEDDING_GATEWAY_API_KEY`, `EMBEDDING_MODEL`,
   필요시 `EMBEDDING_DIMENSION`)을 설정한다.
5. 실제 법령 코퍼스를 청킹·임베딩해 `regulation_chunks`에 적재하는 배치는 **아직 없다** —
   이번 범위 밖이다.

**교체 포인트**: 위 4단계를 다 하면 `RagService`는 코드 변경 없이 그대로 동작한다.

---

## G. Embedding Gateway

**결정**: LLM Gateway와 동일한 패턴. `com.glovishedge.rag.embedding.EmbeddingClient`
인터페이스 + `GatewayEmbeddingClient`(환경변수 기반 HTTP adapter).

**환경변수**:
- `EMBEDDING_GATEWAY_URL` — 설정된 경우에만 빈 등록
- `EMBEDDING_GATEWAY_API_KEY`
- `EMBEDDING_MODEL`
- `EMBEDDING_DIMENSION` (기본값 768 — `03_API계약.md` §6 "임베딩은 768차원으로 충분하다")

**PROVISIONAL 요청/응답 계약**:
```
요청: POST {EMBEDDING_GATEWAY_URL}  { "model": "...", "input": "..." }
응답: { "embedding": [0.01, -0.02, ...] }   // 성공, 길이가 EMBEDDING_DIMENSION과 다르면 실패 처리
      { "error": "..." }                    // 실패
```

차원이 설정값과 다르면 저장하지 않고 예외를 던진다 — 가짜 zero vector로 채우지 않는다.

---

## H. RAG hybrid score 정규화

**결정**: `03_API계약.md` §6이 확정한 `vector 0.6 + keyword 0.4` 가중치는 그대로 쓴다.
원점수 스케일이 다른 문제는 `com.glovishedge.rag.retriever.RagScoreNormalizer` 하나로
격리했다 — 같은 질의의 검색 결과 집합 내에서 각 신호를 min-max로 `[0,1]` 정규화한 뒤
가중합한다. 모든 후보의 원점수가 동일하면(변별력 없음) 중립값 1.0으로 둔다.

**왜 이 방식**: 명세가 원점수 스케일을 정의하지 않아 절대적으로 옳은 정규화는 없다.
후보 집합 내 상대적 정규화는 구현이 단순하고, 검색 결과 랭킹을 왜곡하지 않는 가장 보수적인
선택이다.

**교체 포인트**: `RagScoreNormalizer.minMaxNormalize`만 바꾸면 다른 정규화(예: softmax,
z-score)로 쉽게 교체된다. 호출부(`RagService`)는 이 컴포넌트의 존재만 안다.

---

## I. Risk 엔진 — Historical Empirical(k=50)

**결정**: `RiskAnalysisEngine` 인터페이스만 만들고 구현체는 없다. `risk` bot과 `auto` bot의
`analysis` 경로는 즉시 `"risk engine not configured"`(503)를 반환한다.

**왜**: `02_계산명세.md` §9는 EWMA 변동성·T영업일 가격구간·첫 통과 확률(BGK 이산감시 보정)
공식을 완전히 명시하지만, 이 계산은 **최근 260 거래일 EUA 종가 시계열**을 입력으로 요구한다.
그 원본 종가 260개는 이 저장소 어디에도 없다(참조 통계값 — 일간 변동성 0.018533 등 —
만 있을 뿐). 게다가 `DB_IMPLEMENTATION_GUIDE.md` §16/§17은 "EWMA + Historical
Empirical(k=50)"이라는 **최신** 방법론의 정확한 산출 절차 자체를 팀 `[결정 필요]`로 명시
남겨두고 있다 — 이 문서가 설명하는 "Historical Empirical"은 `02_계산명세.md` §9-3의 로그정규
분석적 첫 통과 확률 공식과 다른 방법론으로 보이지만, 정확히 어떻게 다른지(리샘플링 절차,
윈도우 크기 등) 어디에도 없다.

즉 공식은 알아도 입력 데이터가 없고, 최신 방법론의 정확한 절차도 미확정이다 — 이 상태로
구현하면 검증 불가능한 확률을 만들게 된다.

**교체 포인트**: `RiskAnalysisEngine` 구현체 하나를 추가하고 `RiskBotHandler`/
`AutoBotHandler`에서 즉시-실패 분기를 실제 호출로 바꾸면 된다.

---

## 요약 — 교체 포인트 한눈에

| 영역 | 지금 상태 | 교체 시 건드릴 곳 |
|---|---|---|
| sea-distance routing | Node sidecar(searoute-ts) | `SeaRouteProvider` 새 구현체 |
| sea-distance path 저장 | 저장 안 함(매 요청 계산) | `SeaDistanceService`에 캐시 계층 추가 |
| LLM provider | 없음(env 없으면 503) | `LlmGatewayClient` 새 구현체 |
| Chat 응답 스키마 | PROVISIONAL 4필드 | `ChatResponse` + `ChatController` |
| EUA isLive/recentCloses | PROVISIONAL(A안, 5개) | `EuaService` 상수/분기 |
| RAG storage | 없음(pgvector 미설치) | migration 이동 + `RAG_ENABLED=true` |
| Embedding provider | 없음(env 없으면 미등록) | `EmbeddingClient` 새 구현체 |
| RAG score 정규화 | min-max(후보집합 내) | `RagScoreNormalizer` |
| Risk 엔진 | 없음(즉시 503) | `RiskAnalysisEngine` 구현체 |

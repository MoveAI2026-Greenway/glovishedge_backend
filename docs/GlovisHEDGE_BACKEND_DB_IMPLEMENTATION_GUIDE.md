# GlovisHEDGE Backend / Database Implementation Guide

## 0. 문서 목적

본 문서는 GlovisHEDGE 백엔드 및 데이터베이스 구현 시 참고할 기술 구현 가이드이다.

기존 기능명세, API 명세, DB 설계, 현장 재구현 문서에서 정의한 서비스 기능이나 계산 공식을 새로 설계하지 않는다. 기존 설계를 Spring Boot와 PostgreSQL 환경에서 실제로 구현하는 데 필요한 구조, 구현 순서, 데이터 흐름, 예외 처리, 캐시 전략, 마이그레이션 전략을 정리한다.

기존 문서 사이에 버전 차이로 인한 충돌이 존재하는 경우 이를 임의로 병합하지 않는다. 최신 현장 재구현 문서의 API 계약, 계산 명세, 데이터 시드, 문구, 컴포넌트 명세를 우선 확인하고, 기존 `API_문서.md`, `DB_설계.md`, 초기 기능명세는 상세 배경 및 참고 자료로 사용한다.

현장 최소 API 문서는 전체 API 중 실제 구현할 최소 집합만 별도로 정의하고 있으며, 외부 API는 캐시와 폴백을 통해 일부 연동 장애가 전체 서비스 장애로 전파되지 않도록 하는 것을 기본 구조로 정의한다.

---

## 1. 기술 스택

백엔드는 다음 기술 스택을 사용한다.

| 영역 | 기술 |
|---|---|
| Language | Java |
| Backend | Spring Boot |
| REST API | Spring Web |
| ORM | Spring Data JPA + Hibernate |
| Database | PostgreSQL |
| Schema Migration | Flyway |
| HTTP Client | Spring 6 `RestClient` 또는 Java `HttpClient` |
| Scheduler | Spring `@Scheduled` |
| Deployment DB | Railway PostgreSQL |
| AI 연동 | 서버 측 LLM Gateway 호출 |
| Vector Search | pgvector — RAG 구현 시에만 적용 |

JPA의 `ddl-auto`는 다음과 같이 사용한다.

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
```

Hibernate가 운영 스키마를 자동 수정하지 않도록 하고, 스키마 변경의 단일 기준은 Flyway migration으로 유지한다.

---

## 2. 전체 시스템 구조

기본 구조는 다음과 같이 구성한다.

```text
Browser / Frontend
        │
        │ /api/*
        ▼
┌─────────────────────────────┐
│         Spring Boot         │
│                             │
│  Controller                 │
│      ↓                      │
│  Service                    │
│      ├─ Repository          │
│      ├─ Calculation Engine  │
│      ├─ External Client     │
│      └─ LLM Client          │
│                             │
│  Scheduler / Batch          │
└──────────────┬──────────────┘
               │
               ▼
          PostgreSQL
```

LLM은 숫자 계산을 담당하지 않는다.

역할은 다음과 같이 분리한다.

```text
계산 엔진 → 숫자
DB / 색인 → 데이터 및 근거
LLM       → 설명 및 문장 생성
```

“숫자는 엔진, 검색은 색인, 설명만 AI”를 시스템 경계로 유지한다.

---

## 3. Spring Boot 패키지 구조

기능 단위 패키지 구조를 사용한다.

프로젝트 전체를 `controller`, `service`, `repository` 세 폴더로만 나누지 않는다. 각 도메인이 독립적으로 Controller-Service-Repository 구조를 가지도록 구성한다.

```text
src/main/java/com/greenway/glovishedge
│
├── common
│   ├── exception
│   ├── response
│   ├── util
│   └── constant
│
├── config
│   ├── RestClientConfig
│   ├── CorsConfig
│   └── SchedulerConfig
│
├── port
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
│
├── route
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
│
├── market
│   ├── eua
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   ├── dto
│   │   └── client
│   │
│   └── fx
│       ├── controller
│       ├── service
│       ├── repository
│       ├── entity
│       ├── dto
│       └── client
│
├── forecast
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
│
├── risk
│   ├── service
│   └── dto
│
├── schedule
│   ├── controller
│   ├── service
│   ├── repository
│   └── entity
│
├── freight
│   ├── service
│   ├── repository
│   └── entity
│
├── ai
│   ├── controller
│   ├── service
│   ├── client
│   └── dto
│
├── regulation
│   └── ...        // RAG 구현 시
│
├── query
│   └── ...        // F-12 구현 시
│
├── auth
│   └── ...        // 로그인 구현 시
│
└── alert
    └── ...        // 목표가 알림 구현 시
```

기능이 구현되지 않은 상태에서는 빈 패키지를 미리 만들 필요는 없다.

---

## 4. 계층별 책임

### 4.1 Controller

HTTP 요청과 응답만 담당한다.

Controller에서 다음 작업을 직접 수행하지 않는다.

- 외부 API 직접 호출
- Repository 직접 조합
- 복잡한 계산
- 캐시 판정
- LLM 프롬프트 조합
- DB Entity 직접 수정 로직

예:

```text
GET /api/eua

EuaController
    ↓
EuaService
    ↓
EuaRepository / YahooEuaClient
```

Controller는 DTO를 받아 Service에 전달하고 Service 결과를 DTO로 반환한다.

### 4.2 Service

비즈니스 로직의 중심 계층으로 사용한다.

다음 작업을 담당한다.

- DB 캐시 조회
- TTL 판정
- 외부 API 호출 여부 판정
- stale/fallback 처리
- Repository 조합
- 계산 엔진 호출
- LLM Client 호출 전 입력 생성
- 트랜잭션 단위 결정
- 최종 응답 DTO 생성

### 4.3 Repository

PostgreSQL 접근만 담당한다.

Spring Data JPA를 우선 사용한다.

단순 CRUD나 최신 데이터 조회를 위해 QueryDSL 같은 별도 라이브러리를 도입하지 않는다. 현재 DB 규모에서는 Spring Data JPA의 파생 쿼리와 필요한 일부 `@Query`만으로 충분하다.

### 4.4 External Client

외부 API별 Client를 분리한다.

```text
YahooEuaClient
ExchangeRateClient
LlmGatewayClient
MailClient
```

Service에서 직접 URL을 조립하거나 HTTP 요청 코드를 작성하지 않는다.

---

## 5. Entity와 API DTO 분리

JPA Entity를 Controller에서 그대로 반환하지 않는다.

다음 형태를 사용한다.

```text
DB
 ↓
Entity
 ↓
Service
 ↓
Response DTO
 ↓
Controller
```

예:

```text
EuaPrice                  // Entity
EuaResponse               // API Response DTO

FxRate                    // Entity
FxResponse                // API Response DTO

RouteDistance             // Entity
SeaDistanceResponse       // API Response DTO
```

`/api/eua` 응답에는 DB Entity에 없는 `dayChangePct`, `recentCloses`, `currency`, `symbol`, `isProxy` 등이 포함되므로 Entity와 API Response를 분리한다.

```json
{
  "spot": 79.01,
  "dayChangePct": 1.77,
  "asOf": "2026-08-07",
  "currency": "EUR",
  "symbol": "CO2.L",
  "recentCloses": [],
  "source": "Yahoo Finance · CO2.L",
  "isProxy": true
}
```

---

## 6. 수치 타입 처리

금액, 비율, 환율에는 `double`보다 `BigDecimal`을 우선 사용한다.

대상은 다음과 같다.

```text
EUA 가격
환율
기본 운임
탄소비용
보험료
총비용
운임지수
확률
비율
```

중간 계산 결과를 화면 표시 단위로 반올림하여 저장하지 않는다.

```text
계산
 ↓
원 정밀도 유지
 ↓
최종 Response 또는 Frontend 표시 단계
 ↓
반올림
```

---

## 7. 데이터베이스 구성

현재 DB 설계는 다수의 테이블을 정의하지만 모든 테이블을 최초 구현 단계에서 동시에 만들 필요는 없다.

### 7.1 Core

우선 구현한다.

```text
ports
routes
fx_rates
eua_prices
eua_forecasts 또는 최신 리스크 결과 저장 구조
route_distances
hazard_zones
```

### 7.2 T2 또는 화면 의존 데이터

```text
vessel_schedules
freight_index_rates
```

### 7.3 후속 기능

```text
saved_queries
regulation_chunks
chat_messages
users
sessions
price_alerts
```

`saved_queries`, `chat_messages`, 인증/알림 관련 테이블은 기능 범위가 확정된 뒤 구현한다.

---

## 8. `ports`

항구 마스터 테이블로 사용한다.

```text
ports

unlocode       VARCHAR(5) PK
name
country_code
lat
lng
role
ets_applies
updated_at
```

`unlocode`를 PK로 사용한다.

`route_distances`, `vessel_schedules`가 `ports.unlocode`를 참조한다.

따라서 마이그레이션 순서는 다음과 같이 한다.

```text
1. ports table 생성
2. ports seed 적재
3. ports를 참조하는 FK 테이블 생성/적재
```

### 최신 데이터 적용

기존 DB 설계 일부에는 44개 항구 데이터가 남아 있으나 최신 데이터 시드는 출발 18개 + 도착 32개 = 총 50개를 정의한다.

최종 구현에서는 **최신 `08_데이터시드`의 50개 목록을 사용한다.**

구버전 `demo/src/data/ports.js`나 DB 설계에 기록된 44개 목록을 최종 시드 기준으로 사용하지 않는다.

---

## 9. `routes`

항로 A/B/C/D 마스터 정보를 저장한다.

```text
routes

id
route_key
name
via_text
lane_note
status
status_reason
lead_time_days
sea_leg_note
distance_nm
base_other_usd
war_risk_rate
risk_level
updated_at
```

`status` 값은 다음과 같이 제한한다.

```text
best
limited
blocked
```

`status_reason`은 `NOT NULL`로 유지한다.

항로 상태만 저장하고 이유를 누락하지 않는다. 해당 사유는 화면 상태 툴팁과 AI 항로 비교 리포트에서 동일하게 사용한다.

---

## 10. `fx_rates`

환율 캐시 테이블이다.

```text
fx_rates

id
base_currency
quote_currency
rate
source
fetched_at
```

인덱스:

```text
(base_currency, quote_currency, fetched_at DESC)
```

### 최신 데이터 소스

최신 API 계약은 `open.er-api.com`을 사용한다.

```text
GET /api/fx
```

응답은 다음 항목을 포함한다.

```text
usdPerEur
krwPerEur
asOf
source
attribution
attributionUrl
```

출처 표기 정보도 응답에 포함한다.

기존 DB DDL에 남아 있는:

```text
frankfurter
/api/fx-rate
```

기준 구현은 사용하지 않는다.

DB 컬럼의 기본 `source='frankfurter'` 역시 실제 migration 작성 전에 최신 API 기준으로 정정한다.

---

## 11. `eua_prices`

현재 EUA 시세와 최근 조회 결과를 저장한다.

```text
eua_prices

id
price_date
price_eur
source
fetched_at
```

`price_date`에 UNIQUE 제약을 둔다. 동일 거래일의 데이터가 재조회되면 새 행을 계속 추가하지 않고 upsert한다.

### 최신 데이터 소스

현재 시세 소스는 Yahoo Finance의 `CO2.L`이다.

해당 상품은 EUA 직접 거래소 원장이 아니라 프록시이므로 API 응답의 `isProxy=true`를 유지한다.

기존 DDL의:

```text
source = oilpriceapi
```

는 최신 명세와 충돌하므로 사용하지 않는다.

---

## 12. EUA 데이터 캐시

EUA 시세 TTL은 30분으로 처리한다.

```text
요청
 ↓
30분 이내 DB 데이터 존재?
 ├─ YES → DB 반환
 └─ NO
     ↓
   Yahoo 조회
     ├─ 성공 → DB upsert → 반환
     └─ 실패
          ↓
        stale/snapshot 반환
```

기존 DB 설계의 `eua_prices = 1일 TTL` 내용은 최신 API 계약보다 오래된 내용이므로 사용하지 않는다.

---

## 13. FX 데이터 캐시

환율은 응답의 `time_next_update_utc`를 우선 캐시 만료 시각으로 사용한다.

단순 구현 시 약 24시간 TTL을 사용해도 된다.

```text
요청
 ↓
유효 캐시 존재
 ├─ YES → 반환
 └─ NO
      ↓
  open.er-api 호출
      ↓
  저장 + 반환
```

---

## 14. 공통 Cache-Aside / Fallback 정책

외부 API가 필요한 데이터는 공통적으로 다음 패턴을 사용한다.

```text
1. DB에서 TTL 이내 최신 row 조회

2. 유효한 row 존재
   → 캐시 데이터 반환

3. 유효한 row 없음
   → 외부 API 호출

4. API 성공
   → DB 저장
   → 신규 데이터 반환

5. API 실패
   → 만료된 stale cache 조회

6. stale cache 존재
   → stale임을 명시하고 반환

7. stale cache도 없음
   → 사전에 정의된 fallback/snapshot 반환
```

외부 서비스 하나가 실패했다고 전체 페이지를 500 오류로 처리하지 않는다.

---

## 15. 외부 데이터 상태 표현

현재 명세에는 다음 필드들이 혼재한다.

```text
isLive
cached
stale
error
source
```

## [결정 필요]

프론트/백엔드 공통 상태 계약을 한 가지로 확정해야 한다.

### 선택지 A — 현재 최소 API 계약 우선

```json
{
  "isLive": true
}
```

fallback:

```json
{
  "isLive": false
}
```

### 선택지 B — 캐시 상태 세분화

```json
{
  "cached": true,
  "stale": false
}
```

### 선택지 C — 별도 상태 enum

```json
{
  "dataStatus": "LIVE"
}
```

가능 값:

```text
LIVE
CACHE
STALE
FALLBACK
```

C는 기존 API 계약 자체를 변경하므로 팀 합의 없이 도입하지 않는다.

---

## 16. `eua_forecasts` 및 최신 리스크 분석 결과

기존 DB에는 다음 구조의 `eua_forecasts`가 존재한다.

```text
forecast_date
target_date
horizon_days
point
lo80
hi80
lo95
hi95
model_id
```

이는 초기 14영업일 일별 예측 산출물을 저장하도록 설계되었다.

그러나 이후 데이터분석 결과는 다음 구조로 변경되었다.

```text
EWMA
→ 80% / 95% 가격 예상구간

Historical Empirical (k=50)
→ 사용자가 입력한 목표가격의 14영업일 내 도달확률
```

최종 분석 결과는 `current_price`, `target_price`, EWMA 가격 시나리오, Historical Empirical 도달확률을 반환하는 구조이다.

## [결정 필요 — 구현 전 반드시 확인]

현재 `eua_forecasts` 구조를 그대로 유지할 것인지 최신 리스크 결과에 맞는 별도 저장구조를 사용할 것인지 결정한다.

### 선택지 A — `eua_forecasts` 유지

기존 일별 forecast 데이터가 실제 화면 또는 다른 기능에서 여전히 필요한 경우 유지한다.

최신 EWMA/Empirical 결과는 런타임 계산 또는 별도 DTO로만 처리한다.

### 선택지 B — 최신 Risk Snapshot 테이블 추가

예:

```text
eua_risk_snapshots

base_date
current_price_eur
horizon_business_days
lo80
hi80
lo95
hi95
model_version
created_at
```

목표가 확률은 사용자 입력에 따라 달라지므로 별도 계산한다.

### 선택지 C — 기존 `eua_forecasts`를 최신 구조로 변경

DB 구조 변경이 가장 크므로 팀이 기존 일별 예측 기능을 완전히 폐기한다고 확정한 경우에만 사용한다.

**백엔드 구현자가 임의로 C를 선택하지 않는다.**

---

## 17. EUA 분석 엔진 연결 방식

데이터분석 코드를 Spring Boot 내부에서 무조건 재작성하지 않는다.

최종 분석 산출물 전달 방식에 따라 다음 중 하나를 사용한다.

### 선택지 A — 분석 결과 사전 산출

```text
Python/R
 ↓
CSV / JSON
 ↓
Spring Import
 ↓
PostgreSQL
```

### 선택지 B — Python 분석 서비스 분리

```text
Spring
 ↓ HTTP
Python Risk Service
 ↓
결과 반환
```

### 선택지 C — Java로 계산식 포팅

EWMA와 Historical Empirical 계산을 Java로 동일하게 구현한다.

Python 원본 결과와 동일한 결과가 나오는지 golden test가 필요하다.

## [결정 필요]

분석 담당 파트에서 최종적으로 어떤 형식의 결과를 백엔드에 전달할지 확정한다.

특히 다음을 확정한다.

```text
파일인지 API인지
CSV인지 JSON인지
갱신 주기
모델 버전 필드
80%만 사용할지 80%+95%를 사용할지
목표가 입력 단위
```

---

## 18. `route_distances`

항구 간 실제 해상경로 거리 결과를 저장한다.

```text
route_distances

id
from_unlocode
to_unlocode
distance_nm
restrictions
source
fetched_at
```

UNIQUE:

```text
(
  from_unlocode,
  to_unlocode,
  restrictions
)
```

같은 부산→함부르크라도 restrictions가 다르면 다른 경로이므로 별도 row로 취급한다.

희망봉 경로 계산 시 Suez와 Bab-el-Mandeb뿐 아니라 Panama도 제한해야 한다.

---

## 19. `route_distances` SQL upsert 주의

현재 DB 문서의 UNIQUE 조건은:

```sql
UNIQUE (
    from_unlocode,
    to_unlocode,
    restrictions
)
```

이다.

기존 SQL 예시 중 일부는:

```sql
ON CONFLICT (
    from_unlocode,
    to_unlocode
)
```

로 작성되어 있으므로 그대로 사용하지 않는다.

실제 SQL 작성 시 UNIQUE 조건과 동일하게 처리한다.

```sql
ON CONFLICT (
    from_unlocode,
    to_unlocode,
    restrictions
)
```

---

## 20. `/api/sea-distance`

최신 최소 계약:

```text
GET /api/sea-distance
```

요청:

```text
fromLng
fromLat
toLng
toLat
```

응답:

```json
{
  "routes": {
    "A": {
      "nm": 11259,
      "path": []
    },
    "C": {
      "nm": 14549,
      "path": []
    }
  }
}
```

거리와 지도 path는 동일한 항로 계산 결과에서 생성한다.

---

## 21. `searoute-ts`와 Spring Boot 호환 문제

현재 명세가 지정한 오프라인 항로 계산 도구는 `searoute-ts`이다.

`searoute-ts`는 Node/TypeScript 계열 라이브러리이므로 Spring Boot Java 코드에서 직접 import하여 사용할 수 없다.

## [결정 필요 — 필수]

다음 중 한 가지 방식으로 확정한다.

### 선택지 A — 사전 계산 + DB 저장

```text
Node script
   ↓
searoute-ts
   ↓
distance + path
   ↓
PostgreSQL
   ↓
Spring 조회
```

장점:

- Spring 런타임이 단순함
- 네트워크가 없어도 동작함
- 시연 중 장애 가능성이 낮음

단점:

- 새로운 항구 조합을 실시간 계산하기 어려움

### 선택지 B — Node 보조 서비스

```text
Spring
 ↓ HTTP
Node Route Service
 ↓
searoute-ts
```

장점:

- 모든 조합 실시간 계산 가능

단점:

- 서비스와 배포 지점이 증가함
- 장애 지점 증가
- 구현 복잡도 증가

### 선택지 C — Java에서 별도 항로망 계산 구현

개발 비용이 가장 크다.

### 구현상 우선 검토안

해커톤 범위에서는 **A 사전 계산 + DB 조회**가 가장 단순하다.

다만 기존 설계에서 명시적으로 확정된 방식은 아니므로 팀 결정 후 적용한다.

---

## 22. 항로 path 저장 위치

현재 `route_distances`에는 거리만 있고 지도 `path` 저장 컬럼이 없다.

그러나 `/api/sea-distance`는 path를 반환해야 한다.

## [결정 필요]

### 선택지 A — PostgreSQL JSONB

```text
path JSONB
```

를 `route_distances`에 추가한다.

### 선택지 B — 별도 정적 JSON

```text
src/main/resources/routes/*.json
```

등에서 관리한다.

### 선택지 C — 요청 시마다 계산

searoute-ts 런타임 문제를 해결해야 한다.

사전 계산 방식을 채택할 경우 A 또는 B 중 하나를 결정한다.

---

## 23. `vessel_schedules`

현재 실시간 선사 API를 전제로 하지 않는다.

목업/운영 큐레이션 데이터를 저장한다.

```text
vessel_schedules

carrier_scac
vessel_name
voyage_no
from_unlocode
to_unlocode
etd
eta
reliability_pct
is_assumption
source
fetched_at
```

`is_assumption`은 반드시 유지한다.

출처가 없는 정시운항율 등의 값을 실제 데이터처럼 표시하지 않고, 프론트에서 가정값임을 구분하기 위한 필드이다.

납기 충족 여부는 항구 도착일이 아니라 **창고 입고 기한**을 기준으로 판정한다.

해상운송 이후:

```text
수입통관 2일
+
내륙운송 2일
=
4일
```

을 누락하지 않는다.

---

## 24. `freight_index_rates`

운임지수 참고 데이터를 저장한다.

```text
freight_index_rates

lane_code
index_date
rate_usd_per_feu
source
fetched_at
```

현재 공개 무료 실시간 API가 확보되지 않았으므로 목업/고정값을 사용하는 구조이다.

`source='mock'`인 경우 프론트에서 반드시 참고값임을 표시한다.

실제 데이터처럼 표시하지 않는다.

---

## 25. `hazard_zones`

위험구역 마스터이다.

```text
hazard_zones

zone_key
name
center_lat
center_lng
radius_km
level
message
updated_at
updated_by
```

자동 외부 API를 전제로 하지 않는다.

운영자가 근거를 확인한 뒤 수동으로 수정하는 큐레이션 데이터로 취급한다.

---

## 26. 계산 엔진의 실행 위치

현재 전체 API 문서에는 `evaluateRoutes`, 탄소비용, CO₂, 납기 역산 등의 계산을 프론트와 백엔드 중 어디에서 수행할지는 구현 단계에서 결정하도록 남겨두었다.

반면 `/api/compare-routes`는 **화면에서 이미 계산된 결과**를 Spring 서버로 보내고 LLM이 설명만 생성하는 계약이다.

## [결정 필요 — 필수]

다음 중 하나를 확정한다.

### 선택지 A — Frontend Application Calculation Module

```text
Frontend
 ├─ calculation/
 │   ├─ evaluateRoutes
 │   ├─ carbonCost
 │   ├─ deadline
 │   └─ insurance
 │
 └─ components
```

React Component 자체에서는 계산하지 않고 별도 순수 계산 모듈을 사용한다.

Spring은 다음을 담당한다.

```text
EUA
환율
거리
DB
LLM
RAG
인증/알림
```

현재 `/api/compare-routes` 계약과 가장 직접적으로 맞는다.

### 선택지 B — Spring Calculation Service

```text
Frontend
 ↓
Spring RouteEvaluationService
 ↓
계산 결과
```

이 경우 현재 API 계약에는 없는 계산 전용 REST API가 필요하다.

예:

```text
POST /api/route-evaluations
```

새 API 추가는 설계 변경이므로 팀 합의 없이 구현하지 않는다.

---

## 27. API 구현 범위

최신 현장 최소 API 명세를 우선 구현한다.

핵심 API:

```text
GET  /api/eua
GET  /api/fx
GET  /api/sea-distance

POST /api/compare-routes
POST /api/chat
POST /api/rag/ask
POST /api/extract-text
```

T1/T2 구현이 완료되지 않은 상태에서 RAG, 계약서 추출, 인증, 알림을 우선 구현하지 않는다.

---

## 28. `/api/eua`

구조:

```text
EuaController
    ↓
EuaService
    ├─ EuaPriceRepository
    └─ YahooEuaClient
```

처리 흐름:

```text
1. 30분 이내 캐시 조회

2. 존재
   → 캐시 기반 Response 생성

3. 없음
   → Yahoo CO2.L 조회

4. 성공
   → eua_prices upsert
   → Response 반환

5. 실패
   → stale/snapshot 반환
```

현재가와 등락률은 동일한 데이터 응답을 기반으로 계산한다.

현재가만 실제 값으로 교체하고 등락률을 기존 정적 데이터에서 가져오지 않는다.

---

## 29. `/api/fx`

구조:

```text
FxController
    ↓
FxService
    ├─ FxRateRepository
    └─ ExchangeRateClient
```

환율은 `open.er-api.com`을 사용한다.

응답의 `attribution`과 `attributionUrl`을 제거하지 않는다.

---

## 30. `/api/compare-routes`

이 API는 항로를 다시 계산하지 않는다.

요청으로 받은 계산 결과만 LLM에 전달한다.

```text
Frontend 계산 결과
 ↓
CompareRouteController
 ↓
CompareRouteService
 ↓
LlmGatewayClient
 ↓
설명 JSON
```

LLM이 다음 값을 새로 생성하거나 재계산하지 않도록 한다.

```text
totalUsd
baseFreightUsd
carbonCostUsd
warRiskUsd
leadTimeDays
meetsDeadline
```

`statusReason`도 요청에 반드시 포함한다.

---

## 31. LLM Client 구현

LLM Gateway 호출은 브라우저에서 직접 하지 않는다.

```text
Frontend
 ↓
Spring
 ↓
LLM Gateway
```

API Key는 서버 환경변수에만 둔다.

HTTP Client는 Spring `RestClient` 또는 Java `HttpClient`를 사용한다.

LLM 응답은 HTTP status만 확인하지 않는다.

Gateway가 HTTP 200 본문 안에 별도 오류 코드를 담는 경우가 있으므로 응답 JSON의 오류 필드까지 검사한다.

---

## 32. `/api/chat`

단일 AI 진입점으로 사용한다.

지원 bot:

```text
auto
nav
trip
risk
term
reg
doc
```

각 bot의 책임을 혼합하지 않는다.

특히:

```text
trip
```

은 입력 문장에 없는 값을 기본값으로 만들어내지 않는다.

```text
term
```

은 계약 조항 자체가 입력이므로 이전 대화 history를 붙이지 않는다.

```text
risk
```

는 수치 파라미터만 추출하고 실제 계산은 엔진에서 수행한다.

---

## 33. RAG

RAG가 실제 구현범위에 포함될 때만 `regulation_chunks`와 pgvector를 추가한다.

```text
regulation_chunks

chunk_key
celex
doc_short
cite
article_no
title
body
embedding
built_at
```

조문 단위로 색인한다.

다른 법령을 참조하는 본문의 조문 번호를 해당 문서의 실제 조문으로 잘못 인식하지 않도록 문서별 조문 상한 필터를 적용한다.

## [결정 필요]

현장 구현에 RAG를 포함할지 결정한다.

미포함이면 core DB migration에 다음을 넣지 않는다.

```sql
CREATE EXTENSION vector;
```

핵심 기능과 관계없는 pgvector 설정 실패가 전체 DB 초기화를 실패시키지 않도록 한다.

---

## 34. `saved_queries`와 F-12

F-12 가격 이탈 감지는 이전 조회값이 필요하다.

입력:

```text
저장된 조회 결과
최신 EUA
기존 예상 범위
```

처리:

```text
기존 범위와 현재 EUA 비교

범위 내
→ 권고 유효

범위 이탈
→ 권고 재계산

deadline 경과
→ 판정 불가
```

F-12를 실제 구현할 경우 `saved_queries` 또는 이에 준하는 persistence가 필요하다.

## [결정 필요]

F-12가 실제 MVP 범위에 포함되는지 확정한다.

미포함이면 `saved_queries` 구현을 뒤로 미룬다.

---

## 35. F-11 리포트 저장

F-11은 기존 계산 결과를 읽기 전용 보고서로 재구성하는 기능이다.

별도 계산 엔진이 필요한 기능이 아니다.

단, 고유 URL을 통한 공유를 실제 구현할 경우 결과 persistence가 필요하다.

## [결정 필요]

### 선택지 A — 페이지 상태 기반

```text
Dashboard state
 ↓
Report page
```

DB에 저장하지 않는다.

새로고침이나 다른 기기 URL 공유는 지원하지 않는다.

### 선택지 B — Analysis Snapshot 저장

```text
분석 결과
 ↓
DB 저장
 ↓
analysisId
 ↓
/report/{analysisId}
```

실제 링크 공유가 가능하다.

---

## 36. 사용자 인증

로그인은 전체 서비스의 선행조건으로 두지 않는다.

계정의 주된 목적은 목표 EUA 도달 이메일 알림이다.

테이블:

```text
users
sessions
price_alerts
```

T1/T2 구현 전에 인증 기능부터 만들지 않는다.

---

## 37. 비밀번호 구현

기존 문서에는:

```text
scrypt
timingSafeEqual
```

이 명시되어 있다.

`timingSafeEqual`은 Node 계열 API 이름이므로 Spring Boot에서는 동일한 보안 목적을 Java/Spring 방식으로 구현한다.

직접 해시 문자열 비교 로직을 새로 작성하는 것보다 검증된 PasswordEncoder 구현을 우선 검토한다.

단, 해시 알고리즘을 scrypt에서 다른 알고리즘으로 변경하는 것은 기존 계약 변경이므로 팀 합의 없이 변경하지 않는다.

---

## 38. 세션

세션을 구현할 경우 DB 세션 방식을 사용한다.

```text
sessions

token
user_id
expires_at
```

세션 토큰은 httpOnly Cookie로 전달한다.

localStorage에 인증 토큰을 저장하지 않는다.

---

## 39. 목표가 알림

`price_alerts`:

```text
id
user_id
email
target_eur
active
status
notified
notify_note
reached_at
reached_spot_eur
created_at
```

판정:

```text
현재 EUA <= 목표가
→ reached
```

등록 직후 한 번 즉시 판정한다.

메일 API Key가 없거나 발송 실패 시 `sent`로 처리하지 않고 `pending` 상태를 유지한다.

---

## 40. Flyway migration 구성

모든 테이블과 모든 후속 기능을 `V1` 하나에 넣지 않는다.

권장 구조:

```text
src/main/resources/db/migration/

V1__create_core_schema.sql
V2__seed_ports.sql
V3__seed_routes.sql
V4__seed_hazard_zones.sql

V5__seed_vessel_schedules.sql
V6__seed_freight_index.sql

V10__create_saved_queries.sql

V20__enable_pgvector.sql
V21__create_regulation_chunks.sql

V30__create_users.sql
V31__create_sessions.sql
V32__create_price_alerts.sql

V40__create_chat_messages.sql
```

실제 기능 구현 여부에 맞추어 migration을 추가한다.

이미 DB에 적용된 migration 파일을 수정하지 않는다.

컬럼 추가나 제약 변경이 필요한 경우 새로운 migration 파일을 생성한다.

---

## 41. Seed와 운영 데이터 분리

다음 값은 Flyway seed가 적합하다.

```text
ports
routes
hazard_zones
초기 vessel schedule 목업
초기 freight index 목업
```

반면 다음 값은 migration에 하드코딩하지 않는다.

```text
매일 바뀌는 EUA 시세
환율
새 forecast 결과
사용자 조회 결과
알림
채팅 이력
```

해당 데이터는 Service, batch, import 작업을 통해 적재한다.

---

## 42. 환경변수

비밀정보는 repository에 저장하지 않는다.

예:

```text
PGHOST
PGPORT
PGDATABASE
PGUSER
PGPASSWORD

LLM_API_KEY
LLM_FALLBACK_API_KEY
MAIL_API_KEY
```

다음 파일에 실제 Key를 커밋하지 않는다.

```text
application.yml
application.properties
.env
README
소스 코드
```

개발용 예시는:

```text
.env.example
application-local.yml.example
```

등에 Key 이름만 기록한다.

---

## 43. 배포 구조

현재 문서에는 두 가지 배포 방식의 흔적이 존재한다.

## [결정 필요]

### 선택지 A — Spring에서 Frontend 정적파일까지 제공

```text
Spring Boot
 ├─ /api/*
 └─ React build
```

장점:

- same origin
- CORS 단순
- 제출 URL 하나

단점:

- 프론트와 백엔드 배포가 결합됨

### 선택지 B — Frontend / Spring 별도 배포

```text
Gateway
 ├─ /*     → Frontend
 └─ /api/* → Spring
```

장점:

- 독립 배포 가능

단점:

- Gateway 및 CORS/Proxy 설정 필요

프론트와 Spring을 같은 서버에서 제공할지 별도 서비스로 배포할지 팀에서 확정한다.

---

## 44. 테스트 구성

### 44.1 Repository

다음을 검증한다.

```text
PortRepository
→ 국가/role별 조회

FxRateRepository
→ 최신 환율 조회

EuaPriceRepository
→ 날짜별 unique/upsert

RouteDistanceRepository
→ restrictions별 경로 구분

ForecastRepository
→ forecast 기준일 조회
```

### 44.2 External API Service

각 외부 API에 대해 최소 네 가지 경우를 테스트한다.

```text
1. cache hit
2. cache miss + API 성공
3. API 실패 + stale 존재
4. API 실패 + stale 없음
```

### 44.3 계산 값

백엔드가 계산 엔진을 담당하는 것으로 결정될 경우, 명세의 검산값과 정확히 비교한다.

값이 화면과 다를 경우 UI에서 임의 보정하지 않는다.

계산 원인을 추적한다.

### 44.4 Contract Test

최소 API에 대해 요청/응답 스키마가 문서와 일치하는지 확인한다.

```text
GET  /api/eua
GET  /api/fx
GET  /api/sea-distance
POST /api/compare-routes
```

필드명을 임의로 camelCase/snake_case 변경하지 않는다.

프론트와 합의한 계약을 그대로 사용한다.

### 44.5 장애 테스트

외부 API 요청을 모두 실패시킨 상태에서도 core 화면이 렌더링 가능한지 확인한다.

```text
Yahoo failure
Exchange Rate API failure
LLM failure
```

LLM 장애 시 숫자 계산 기능은 정상 동작해야 한다.

---

## 45. 구현 우선순위

### Phase 0 — 구현 전 계약 확정

다음 항목부터 팀에서 결정한다.

```text
[ ] 계산 엔진 위치
[ ] searoute-ts 처리 방식
[ ] route path 저장 위치
[ ] EUA 최신 분석 결과의 DB 저장 방식
[ ] 외부 API 상태 필드
[ ] F-11 실제 공유 URL 필요 여부
[ ] F-12 실제 구현 여부
[ ] RAG 구현 여부
[ ] 로그인/알림 구현 여부
[ ] Frontend/Spring 배포 방식
```

### Phase 1 — 프로젝트 기반

```text
Spring Boot 프로젝트 생성
PostgreSQL 연결
Flyway 설정
공통 예외 처리
RestClient 설정
```

### Phase 2 — 기준 DB

```text
ports
routes
hazard_zones
```

구현 및 seed 적재.

### Phase 3 — 환율

```text
fx_rates
ExchangeRateClient
FxService
GET /api/fx
```

Cache-Aside 구현 검증.

### Phase 4 — EUA

```text
eua_prices
YahooEuaClient
EuaService
GET /api/eua
```

30분 캐시와 fallback 구현.

### Phase 5 — 항로 거리

팀 결정 방식에 따라:

```text
route_distances
path
GET /api/sea-distance
```

구현.

### Phase 6 — EUA 분석 결과

데이터분석 파트와 확정한 계약에 따라 forecast/risk 데이터를 연결한다.

### Phase 7 — Frontend API 연동

정적 mock이 아니라 실제 Spring API를 연결한다.

각 API에 대해:

```text
loading
normal
stale/fallback
error
```

상태를 검증한다.

### Phase 8 — LLM

```text
/api/compare-routes
/api/chat
```

구현.

계산 결과가 완성된 후 설명 레이어를 붙인다.

### Phase 9 — 후속 기능

필요한 경우에만:

```text
RAG
F-11 persistence
F-12
auth
alerts
extract-text
chat history
```

순으로 구현한다.

---

## 46. 구현 전 팀 결정 사항 정리

### 결정 1. 계산 엔진 위치

**결정할 것**

```text
A. Frontend 순수 계산 모듈
B. Spring Service
```

B이면 새로운 계산 API가 필요하다.

### 결정 2. `searoute-ts`

**결정할 것**

```text
A. 사전 계산 후 DB 저장
B. Node 보조 서비스
C. Java 재구현
```

해커톤 구현 난이도만 고려하면 A가 가장 낮다.

### 결정 3. 지도 path

**결정할 것**

```text
A. route_distances.path JSONB
B. 별도 JSON 리소스
C. 런타임 계산
```

### 결정 4. 최신 EUA 분석 결과

**결정할 것**

기존:

```text
eua_forecasts
→ 일별 point/lo80/hi80/lo95/hi95
```

최신 분석:

```text
EWMA 예상범위
+
Historical Empirical 목표가 도달확률
```

중 어떤 데이터를 최종 서비스 계약으로 저장하고 제공하는지 확정한다.

### 결정 5. 목표값 단위

데이터분석 코드의 target은:

```text
EUR/tCO2
```

이다.

일부 화면 설계에는 목표 탄소비용:

```text
USD/TEU
```

개념이 포함되어 있다.

어떤 값을 사용자가 입력하고 어디에서 환산할지 결정한다.

백엔드가 임의로 단위를 추정하지 않는다.

### 결정 6. 외부 API 상태

**결정할 것**

```text
isLive
cached/stale
dataStatus enum
```

하나를 최종 계약으로 통일한다.

### 결정 7. 항구 및 항로 조회

현재 DB에는 `ports`, `routes`가 존재하지만 현장 최소 API에는 별도 `/api/ports`, `/api/routes`가 명확하게 포함되어 있지 않다.

**결정할 것**

```text
A. Frontend 정적 seed 사용
B. PostgreSQL을 source of truth로 하고 조회 API 추가
```

B이면 신규 API 계약을 팀에서 먼저 확정한다.

### 결정 8. F-11

**결정할 것**

```text
A. 인쇄 페이지까지만 제공
B. 고유 URL 공유까지 제공
```

B이면 분석 결과 저장이 필요하다.

### 결정 9. F-12

**결정할 것**

```text
MVP 포함
MVP 제외
```

포함이면 `saved_queries` 구현이 사실상 필요하다.

### 결정 10. RAG

**결정할 것**

```text
현장 구현
후순위
제외
```

미구현이면 pgvector 관련 migration도 core에서 제외한다.

### 결정 11. 인증/알림

**결정할 것**

```text
현장 구현
후순위
```

핵심 계산 기능보다 먼저 구현하지 않는다.

### 결정 12. 배포

**결정할 것**

```text
A. Spring이 React 정적파일도 제공
B. Frontend / Backend 별도 배포 + Gateway
```

---

## 47. 구현 시 금지 사항

다음 작업을 임의로 수행하지 않는다.

```text
- 기능명세에 없는 계산식으로 변경하지 않는다.
- LLM으로 금액이나 확률을 계산하지 않는다.
- API 필드명을 임의로 변경하지 않는다.
- 목업 데이터를 실제 데이터라고 표시하지 않는다.
- 외부 API 장애를 전체 서비스 500 오류로 전파하지 않는다.
- Entity를 그대로 API Response로 노출하지 않는다.
- 중간 계산값을 표시 단위로 반올림하여 저장하지 않는다.
- DB 비밀번호/API Key를 repository에 커밋하지 않는다.
- 적용 완료된 Flyway migration을 다시 수정하지 않는다.
- searoute-ts를 다른 거리 공식으로 임의 대체하지 않는다.
- Haversine 직선거리를 실제 해상항로 거리라고 표시하지 않는다.
- 최신/구버전 명세를 임의로 섞어 새로운 사양을 만들지 않는다.
- T3/T4 기능 때문에 T1/T2 구현을 지연시키지 않는다.
```

---

## 48. 구현 완료 기준

백엔드 기본 구현은 최소 다음 조건을 만족해야 한다.

```text
[ ] Spring Boot가 PostgreSQL에 정상 연결된다.
[ ] Flyway만 스키마를 관리한다.
[ ] 최신 ports/routes seed가 적재된다.
[ ] /api/fx가 최신 계약대로 동작한다.
[ ] FX API 장애 시 fallback이 동작한다.
[ ] /api/eua가 최신 계약대로 동작한다.
[ ] EUA가 30분 캐시된다.
[ ] EUA API 장애 시 이전 값/snapshot이 반환된다.
[ ] /api/sea-distance가 실제 항로 거리와 path를 반환한다.
[ ] A/B/C/D 경로 restriction이 올바르게 구분된다.
[ ] 희망봉 경로가 파나마로 우회하지 않는다.
[ ] 금액 계산 중간값을 조기 반올림하지 않는다.
[ ] 화면의 EUA 시세와 계산에 사용하는 EUA가 동일하다.
[ ] LLM Key가 Frontend에 노출되지 않는다.
[ ] LLM이 계산값을 새로 만들지 않는다.
[ ] 외부 API가 모두 실패해도 핵심 화면이 깨지지 않는다.
[ ] 목업/가정값은 source 또는 is_assumption으로 구분된다.
[ ] 기능 미구현 시 잘못된 성공값을 반환하지 않는다.
```

---

## 49. 최종 구현 원칙

GlovisHEDGE 백엔드는 단순 CRUD 서버로 구현하지 않는다.

백엔드의 주요 책임은 다음과 같다.

```text
1. 서비스가 사용하는 기준 데이터를 일관되게 관리한다.
2. 외부 시세와 환율을 안정적으로 수집하고 캐싱한다.
3. 외부 서비스 장애 시 fallback을 제공한다.
4. 계산 결과와 데이터 출처가 화면 전체에서 일치하도록 한다.
5. LLM을 계산 레이어와 분리한다.
6. 추정값, 목업값, 실데이터를 명확히 구분한다.
7. 필요한 상태만 PostgreSQL에 저장한다.
8. 선택 기능 때문에 핵심 기능의 구현 복잡도를 불필요하게 높이지 않는다.
9. 최신 현장 계약과 과거 설계가 충돌하면 임의로 판단하지 않고 팀 결정 사항으로 남긴다.
10. 결정되지 않은 설계는 구현 도구가 임의로 확정하거나 확장하지 않도록 한다.
```

구현 착수 전에는 **46. 구현 전 팀 결정 사항 정리**를 먼저 확인한다.

결정되지 않은 항목이 실제 구현을 막는 경우 임의 구현하지 않고 해당 항목을 확인한 뒤 진행한다.

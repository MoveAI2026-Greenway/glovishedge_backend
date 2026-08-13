# searoute-service

GlovisHEDGE 백엔드용 내부 sea-route sidecar. `searoute-ts`(pinned `2.2.0`)를 감싸서
Spring Boot의 `GET /api/sea-distance`가 내부 HTTP로 호출한다.

**외부(Browser)에 직접 노출하지 않는다.** 공개 API는 Spring의 `GET /api/sea-distance` 하나뿐이다.
자세한 배경은 `../docs/PROVISIONAL_BACKEND_DECISIONS.md` §A/§B 참고.

## 실행

```bash
npm install
npm start        # PORT 환경변수, 기본 3001
```

Spring 쪽은 `SEA_ROUTE_BASE_URL` 환경변수(기본 `http://localhost:3001`)로 이 서비스를 찾는다.
이 서비스가 꺼져 있어도 Spring Boot는 정상 기동하며, `GET /api/sea-distance` 호출 시에만
503을 반환한다.

## API

### `POST /route` (내부 전용)

```jsonc
// 요청
{ "points": [[lng, lat], [lng, lat], ...], "restrictions": ["suez", "panama", ...] }

// 응답
{ "nm": 11258.91, "path": [[lat, lng], ...] }
```

- `points`는 2개 이상(경유지 포함 가능) — `[lng, lat]` 순서(GeoJSON 관례)
- 응답 `path`는 `[lat, lng]` 순서 — 03_API계약.md의 `GET /api/sea-distance` 계약과 맞춘 것
- `restrictions`는 `suez`/`panama`/`babelmandeb` 등 searoute-ts의 named passage 문자열

### `GET /health`

```json
{ "status": "ok" }
```

## 테스트

```bash
npm test
```

부산→함부르크 golden validation 포함 (`02_계산명세.md` §10 대조):
A/B ≈ 11259nm, D ≈ 11317nm, C(수에즈·바브엘만데브·파나마 차단) ≈ 14549nm.

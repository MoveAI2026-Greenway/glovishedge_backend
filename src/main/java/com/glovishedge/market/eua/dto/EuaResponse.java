package com.glovishedge.market.eua.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/eua 응답 — 03_API계약.md §1 필드 그대로.
 *
 * <p>PROVISIONAL(docs/PROVISIONAL_BACKEND_DECISIONS.md 참조):
 * <ul>
 *   <li>{@code recentCloses} — 최근 5 거래일 종가(시간순, 과거→현재). 명세에 정확한 개수가
 *       정의돼 있지 않아 Yahoo 요청의 {@code range=5d}와 맞춰 5개로 정했다.</li>
 *   <li>{@code isLive} — fresh Yahoo 호출 성공 또는 30분 이내 cache면 true, 실패 후 stale
 *       fallback이면 false. 03_API계약.md/구현가이드 §15의 "외부 데이터 상태 표현" 결정 필요 항목
 *       중 선택지 A(isLive)를 임시로 채택한 것이다.</li>
 * </ul>
 */
public record EuaResponse(
        BigDecimal spot,
        BigDecimal dayChangePct,
        String asOf,
        String currency,
        String symbol,
        List<BigDecimal> recentCloses,
        String source,
        boolean isProxy,
        boolean isLive
) {
}

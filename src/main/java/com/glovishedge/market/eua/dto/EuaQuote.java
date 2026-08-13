package com.glovishedge.market.eua.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Yahoo Finance 원본 JSON 구조를 걷어낸 내부 표현 — 시간순(오름차순) 종가 목록.
 * 최소 2개(spot, 전일 종가)를 보장한다({@link com.glovishedge.market.eua.client.EuaMarketClient} 검증).
 */
public record EuaQuote(List<Instant> observedAt, List<BigDecimal> closes) {
}

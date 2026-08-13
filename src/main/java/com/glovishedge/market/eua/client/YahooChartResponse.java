package com.glovishedge.market.eua.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code https://query1.finance.yahoo.com/v8/finance/chart/CO2.L} 응답 — 필요한 필드만 매핑한다.
 * 이 타입은 client 패키지 밖으로 노출하지 않는다(Yahoo 응답 구조 변경이 API 계약에 직접 전파되지 않도록
 * {@link com.glovishedge.market.eua.dto.EuaQuote}로 변환해 내보낸다).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooChartResponse(Chart chart) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chart(List<Result> result, ChartError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(Meta meta, List<Long> timestamp, Indicators indicators) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(String currency, String symbol) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Indicators(List<Quote> quote) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(List<BigDecimal> close) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartError(String code, String description) {
    }
}

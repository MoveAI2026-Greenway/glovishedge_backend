package com.glovishedge.market.fx.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;

/**
 * open.er-api.com {@code GET /v6/latest/USD} 응답. 필요한 필드만 매핑한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExchangeRateApiResponse(
        String result,
        @JsonProperty("base_code") String baseCode,
        @JsonProperty("time_last_update_utc") String timeLastUpdateUtc,
        Map<String, BigDecimal> rates
) {
}

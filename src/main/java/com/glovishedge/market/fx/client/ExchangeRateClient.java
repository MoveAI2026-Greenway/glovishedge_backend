package com.glovishedge.market.fx.client;

import com.glovishedge.market.fx.dto.ExchangeRateApiResponse;
import com.glovishedge.market.fx.exception.ExchangeRateClientException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * open.er-api.com 전용 client. 응답 검증까지 여기서 끝내, Service는 항상 유효한
 * {@link ExchangeRateApiResponse}만 받는다 — result/rates/EUR/KRW/날짜 형식 문제는
 * 전부 {@link ExchangeRateClientException}으로 통일해 FxService의 fallback 분기 하나로 처리한다.
 */
@Component
public class ExchangeRateClient {

    private static final String LATEST_USD_URL = "https://open.er-api.com/v6/latest/USD";

    private final RestClient restClient;

    public ExchangeRateClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public ExchangeRateApiResponse fetchUsdBaseRates() {
        ExchangeRateApiResponse response;
        try {
            response = restClient.get()
                    .uri(LATEST_USD_URL)
                    .retrieve()
                    .body(ExchangeRateApiResponse.class);
        } catch (RestClientException e) {
            throw new ExchangeRateClientException("open.er-api.com 호출 실패", e);
        }

        validate(response);
        return response;
    }

    private void validate(ExchangeRateApiResponse response) {
        if (response == null) {
            throw new ExchangeRateClientException("open.er-api.com 응답이 비어 있습니다");
        }
        if (!"success".equals(response.result())) {
            throw new ExchangeRateClientException(
                    "open.er-api.com 응답 result가 success가 아닙니다: " + response.result());
        }
        if (response.rates() == null) {
            throw new ExchangeRateClientException("open.er-api.com 응답에 rates가 없습니다");
        }

        BigDecimal eurPerUsd = response.rates().get("EUR");
        BigDecimal krwPerUsd = response.rates().get("KRW");

        if (eurPerUsd == null) {
            throw new ExchangeRateClientException("open.er-api.com 응답에 EUR 환율이 없습니다");
        }
        if (krwPerUsd == null) {
            throw new ExchangeRateClientException("open.er-api.com 응답에 KRW 환율이 없습니다");
        }
        if (eurPerUsd.compareTo(BigDecimal.ZERO) == 0) {
            throw new ExchangeRateClientException("open.er-api.com EUR 환율이 0입니다");
        }
        if (response.timeLastUpdateUtc() == null) {
            throw new ExchangeRateClientException("open.er-api.com 응답에 time_last_update_utc가 없습니다");
        }
        try {
            ZonedDateTime.parse(response.timeLastUpdateUtc(), DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new ExchangeRateClientException(
                    "open.er-api.com 응답의 time_last_update_utc 파싱 실패: " + response.timeLastUpdateUtc(), e);
        }
    }
}

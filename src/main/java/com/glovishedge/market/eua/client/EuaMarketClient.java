package com.glovishedge.market.eua.client;

import com.glovishedge.market.eua.dto.EuaQuote;
import com.glovishedge.market.eua.exception.EuaMarketClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Yahoo Finance CO2.L chart 전용 client. 응답 검증까지 여기서 끝내, Service는 항상 유효한
 * {@link EuaQuote}(시간순 종가 2개 이상)만 받는다 — HTTP 실패/빈 응답/에러 응답/데이터 누락은
 * 전부 {@link EuaMarketClientException}으로 통일해 EuaService의 fallback 분기 하나로 처리한다.
 *
 * <p>PROVISIONAL: Yahoo의 비공식 chart endpoint는 User-Agent가 없는 요청을 429(rate limit)로
 * 거부한다(실측 확인 — 헤더 없이 429, 브라우저 User-Agent 첨부 시 200). 다른 EUA provider로
 * 바꾸지 않고 symbol(CO2.L)과 query1 host도 그대로 유지한 채, 이 client 요청에만 일반적인
 * User-Agent/Accept 헤더를 최소한으로 추가한다. docs/PROVISIONAL_BACKEND_DECISIONS.md 참조.
 */
@Component
public class EuaMarketClient {

    private static final Logger log = LoggerFactory.getLogger(EuaMarketClient.class);

    private static final String CHART_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/CO2.L?range=5d&interval=1d";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

    private final RestClient restClient;

    public EuaMarketClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public EuaQuote fetchLatestQuote() {
        YahooChartResponse response;
        try {
            response = restClient.get()
                    .uri(CHART_URL)
                    .header(HttpHeaders.USER_AGENT, USER_AGENT)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .body(YahooChartResponse.class);
        } catch (RestClientResponseException e) {
            // 응답 body/헤더는 로그에 남기지 않는다 — status code만 남긴다.
            log.warn("Yahoo Finance 호출 실패: HTTP {}", e.getStatusCode().value());
            throw new EuaMarketClientException("Yahoo Finance 호출 실패: HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            log.warn("Yahoo Finance 연결 실패: {}", e.getClass().getSimpleName());
            throw new EuaMarketClientException("Yahoo Finance 연결 실패", e);
        } catch (RestClientException e) {
            log.warn("Yahoo Finance 응답 처리 실패: {}", e.getClass().getSimpleName());
            throw new EuaMarketClientException("Yahoo Finance 호출 실패", e);
        }
        return extract(response);
    }

    private EuaQuote extract(YahooChartResponse response) {
        if (response == null || response.chart() == null) {
            throw new EuaMarketClientException("Yahoo Finance 응답이 비어 있습니다");
        }
        if (response.chart().error() != null) {
            throw new EuaMarketClientException(
                    "Yahoo Finance 오류 응답: " + response.chart().error().description());
        }

        List<YahooChartResponse.Result> results = response.chart().result();
        if (results == null || results.isEmpty()) {
            throw new EuaMarketClientException("Yahoo Finance 응답에 CO2.L 데이터가 없습니다");
        }

        YahooChartResponse.Result result = results.get(0);
        if (result.meta() == null) {
            throw new EuaMarketClientException("Yahoo Finance 응답에 meta(symbol) 정보가 없습니다");
        }

        List<Long> timestamps = result.timestamp();
        if (timestamps == null || timestamps.isEmpty()) {
            throw new EuaMarketClientException("Yahoo Finance 응답에 timestamp가 없습니다");
        }

        if (result.indicators() == null
                || result.indicators().quote() == null
                || result.indicators().quote().isEmpty()) {
            throw new EuaMarketClientException("Yahoo Finance 응답에 가격 데이터가 없습니다");
        }

        List<BigDecimal> closes = result.indicators().quote().get(0).close();
        if (closes == null || closes.isEmpty()) {
            throw new EuaMarketClientException("Yahoo Finance 응답에 종가(price)가 없습니다");
        }
        if (closes.size() != timestamps.size()) {
            throw new EuaMarketClientException("Yahoo Finance 응답의 timestamp/종가 개수가 일치하지 않습니다");
        }

        List<Instant> validTimestamps = new ArrayList<>();
        List<BigDecimal> validCloses = new ArrayList<>();
        for (int i = 0; i < closes.size(); i++) {
            BigDecimal close = closes.get(i);
            Long ts = timestamps.get(i);
            if (close != null && ts != null) {
                validTimestamps.add(Instant.ofEpochSecond(ts));
                validCloses.add(close);
            }
        }

        if (validCloses.isEmpty()) {
            throw new EuaMarketClientException("Yahoo Finance 응답에 유효한 종가가 없습니다");
        }
        if (validCloses.size() < 2) {
            throw new EuaMarketClientException("Yahoo Finance 응답에 전일 종가(previous close)가 없습니다");
        }

        return new EuaQuote(validTimestamps, validCloses);
    }
}

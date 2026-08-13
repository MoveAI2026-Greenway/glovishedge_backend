package com.glovishedge.market.eua.service;

import com.glovishedge.market.eua.client.EuaMarketClient;
import com.glovishedge.market.eua.dto.EuaQuote;
import com.glovishedge.market.eua.dto.EuaResponse;
import com.glovishedge.market.eua.entity.EuaPrice;
import com.glovishedge.market.eua.exception.EuaMarketClientException;
import com.glovishedge.market.eua.exception.EuaUnavailableException;
import com.glovishedge.market.eua.repository.EuaPriceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Cache-aside: eua_prices에 30분 이내 값이 있으면 그대로 쓰고, 없으면 Yahoo Finance(CO2.L)를
 * 호출해 갱신한다. eua_prices에는 등락률/전일종가 컬럼이 없으므로(DB 구조 변경 없이) dayChangePct와
 * recentCloses(PROVISIONAL: 최근 5 거래일)는 항상 "가장 최근 거래일 최대 5개 행"에서 산출한다 —
 * cache hit이든 fresh fetch 직후든 동일한 방식이라 두 경로가 서로 다른 값을 말할 위험이 없다.
 *
 * <p>외부 API가 실패하면 stale DB 값(2개 행 이상 있을 때)으로 폴백하고, 그마저도 없으면 임의 숫자를
 * 만들지 않고 {@link EuaUnavailableException}을 던진다.
 *
 * <p>PROVISIONAL: {@code isLive}는 30분 이내 fresh cache/fresh fetch면 true, 실패 후 stale
 * fallback이면 false다. 확정 명세가 아니므로 docs/PROVISIONAL_BACKEND_DECISIONS.md 참조.
 */
@Service
public class EuaService {

    private static final String SYMBOL = "CO2.L";
    private static final String CURRENCY = "EUR";
    private static final String SOURCE = "Yahoo Finance · CO2.L";
    private static final boolean IS_PROXY = true;
    private static final int RECENT_CLOSES_COUNT = 5;

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final DateTimeFormatter AS_OF_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final EuaPriceRepository euaPriceRepository;
    private final EuaMarketClient euaMarketClient;

    public EuaService(EuaPriceRepository euaPriceRepository, EuaMarketClient euaMarketClient) {
        this.euaPriceRepository = euaPriceRepository;
        this.euaMarketClient = euaMarketClient;
    }

    @Transactional
    public EuaResponse getEua() {
        Instant now = Instant.now();
        List<EuaPrice> latestFive = euaPriceRepository.findTop5ByOrderByPriceDateDesc();

        if (isCacheFresh(latestFive, now)) {
            return fromCached(latestFive, true);
        }

        try {
            EuaQuote quote = euaMarketClient.fetchLatestQuote();
            return fetchAndPersist(quote, now);
        } catch (EuaMarketClientException e) {
            if (latestFive.size() >= 2) {
                return fromCached(latestFive, false);
            }
            throw new EuaUnavailableException(
                    "EUA 시세를 가져올 수 없습니다 (Yahoo Finance 실패, 사용 가능한 캐시도 없음)", e);
        }
    }

    private boolean isCacheFresh(List<EuaPrice> latestFive, Instant now) {
        return latestFive.size() >= 2 && isFresh(latestFive.get(0).getFetchedAt(), now);
    }

    private boolean isFresh(Instant fetchedAt, Instant now) {
        return fetchedAt != null && Duration.between(fetchedAt, now).compareTo(CACHE_TTL) < 0;
    }

    private EuaResponse fromCached(List<EuaPrice> latestFive, boolean isLive) {
        EuaPrice latest = latestFive.get(0);
        EuaPrice previous = latestFive.get(1);
        BigDecimal dayChangePct = computeDayChangePct(latest.getPriceEur(), previous.getPriceEur());
        String asOf = AS_OF_FORMATTER.format(latest.getPriceDate());
        List<BigDecimal> recentCloses = chronological(latestFive);
        return buildResponse(latest.getPriceEur(), dayChangePct, asOf, recentCloses, isLive);
    }

    private EuaResponse fetchAndPersist(EuaQuote quote, Instant fetchedAt) {
        int lastIndex = quote.closes().size() - 1;
        BigDecimal spot = quote.closes().get(lastIndex);
        BigDecimal previousClose = quote.closes().get(lastIndex - 1);
        LocalDate priceDate = quote.observedAt().get(lastIndex).atZone(ZoneOffset.UTC).toLocalDate();

        BigDecimal dayChangePct = computeDayChangePct(spot, previousClose);

        EuaPrice row = euaPriceRepository.findByPriceDate(priceDate)
                .map(existing -> {
                    existing.updateSnapshot(spot, SOURCE, fetchedAt);
                    return existing;
                })
                .orElseGet(() -> new EuaPrice(priceDate, spot, SOURCE, fetchedAt));
        euaPriceRepository.save(row);

        List<BigDecimal> recentCloses = lastN(quote.closes(), RECENT_CLOSES_COUNT);
        String asOf = AS_OF_FORMATTER.format(priceDate);
        return buildResponse(spot, dayChangePct, asOf, recentCloses, true);
    }

    /**
     * DB 조회 결과(최신순, 최대 5건)를 시간순(과거→현재)으로 뒤집는다 — Yahoo 원본 quote.closes()와
     * 같은 방향으로 맞춰 cache/fresh 경로가 같은 순서 규칙을 쓰게 한다.
     */
    private List<BigDecimal> chronological(List<EuaPrice> pricesDesc) {
        List<BigDecimal> closes = new ArrayList<>();
        for (int i = pricesDesc.size() - 1; i >= 0; i--) {
            closes.add(pricesDesc.get(i).getPriceEur());
        }
        return closes;
    }

    private List<BigDecimal> lastN(List<BigDecimal> closes, int n) {
        int from = Math.max(0, closes.size() - n);
        return List.copyOf(closes.subList(from, closes.size()));
    }

    private BigDecimal computeDayChangePct(BigDecimal spot, BigDecimal previousClose) {
        if (previousClose.compareTo(BigDecimal.ZERO) == 0) {
            throw new EuaUnavailableException("전일 종가가 0이라 등락률을 계산할 수 없습니다", null);
        }
        return spot.subtract(previousClose)
                .divide(previousClose, MathContext.DECIMAL128)
                .multiply(BigDecimal.valueOf(100));
    }

    private EuaResponse buildResponse(BigDecimal spot, BigDecimal dayChangePct, String asOf,
                                       List<BigDecimal> recentCloses, boolean isLive) {
        return new EuaResponse(spot, dayChangePct, asOf, CURRENCY, SYMBOL, recentCloses, SOURCE, IS_PROXY, isLive);
    }
}

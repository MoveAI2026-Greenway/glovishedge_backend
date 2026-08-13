package com.glovishedge.market.fx.service;

import com.glovishedge.market.fx.client.ExchangeRateClient;
import com.glovishedge.market.fx.dto.ExchangeRateApiResponse;
import com.glovishedge.market.fx.dto.FxResponse;
import com.glovishedge.market.fx.entity.FxRate;
import com.glovishedge.market.fx.exception.ExchangeRateClientException;
import com.glovishedge.market.fx.exception.FxUnavailableException;
import com.glovishedge.market.fx.repository.FxRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Cache-aside: DB(fx_rates)에 24시간 이내 값이 있으면 그대로 쓰고, 없으면 open.er-api.com을
 * 호출해 EUR→USD/EUR→KRW 두 행으로 갱신한다. 외부 API가 실패하면 stale DB 값으로 폴백하고,
 * stale 값도 없으면 임의 숫자를 만들지 않고 {@link FxUnavailableException}을 던진다.
 */
@Service
public class FxService {

    private static final String BASE_CURRENCY = "EUR";
    private static final String USD = "USD";
    private static final String KRW = "KRW";

    private static final String SOURCE = "open.er-api.com";
    private static final String ATTRIBUTION = "Rates By Exchange Rate API";
    private static final String ATTRIBUTION_URL = "https://www.exchangerate-api.com";

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final DateTimeFormatter AS_OF_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final FxRateRepository fxRateRepository;
    private final ExchangeRateClient exchangeRateClient;

    public FxService(FxRateRepository fxRateRepository, ExchangeRateClient exchangeRateClient) {
        this.fxRateRepository = fxRateRepository;
        this.exchangeRateClient = exchangeRateClient;
    }

    @Transactional
    public FxResponse getFx() {
        Instant now = Instant.now();

        Optional<FxRate> cachedUsd = fxRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(BASE_CURRENCY, USD);
        Optional<FxRate> cachedKrw = fxRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(BASE_CURRENCY, KRW);

        if (isPairFresh(cachedUsd, cachedKrw, now)) {
            return fromCachedPair(cachedUsd.get(), cachedKrw.get());
        }

        try {
            ExchangeRateApiResponse apiResponse = exchangeRateClient.fetchUsdBaseRates();
            return fetchAndPersist(apiResponse, now);
        } catch (ExchangeRateClientException e) {
            if (cachedUsd.isPresent() && cachedKrw.isPresent()) {
                return fromCachedPair(cachedUsd.get(), cachedKrw.get());
            }
            throw new FxUnavailableException(
                    "환율 정보를 가져올 수 없습니다 (open.er-api.com 실패, 사용 가능한 캐시도 없음)", e);
        }
    }

    private boolean isPairFresh(Optional<FxRate> usd, Optional<FxRate> krw, Instant now) {
        return usd.isPresent() && krw.isPresent()
                && isFresh(usd.get().getFetchedAt(), now)
                && isFresh(krw.get().getFetchedAt(), now);
    }

    private boolean isFresh(Instant fetchedAt, Instant now) {
        return fetchedAt != null && Duration.between(fetchedAt, now).compareTo(CACHE_TTL) < 0;
    }

    private FxResponse fetchAndPersist(ExchangeRateApiResponse apiResponse, Instant fetchedAt) {
        // ExchangeRateClient가 이미 검증했으므로 파싱은 실패하지 않는다.
        String asOf = formatAsOf(ZonedDateTime.parse(
                apiResponse.timeLastUpdateUtc(), DateTimeFormatter.RFC_1123_DATE_TIME));

        BigDecimal eurPerUsd = apiResponse.rates().get(BASE_CURRENCY);
        BigDecimal krwPerUsd = apiResponse.rates().get(KRW);

        BigDecimal usdPerEur = BigDecimal.ONE.divide(eurPerUsd, MathContext.DECIMAL128);
        BigDecimal krwPerEur = krwPerUsd.divide(eurPerUsd, MathContext.DECIMAL128);

        fxRateRepository.save(new FxRate(BASE_CURRENCY, USD, usdPerEur, SOURCE, fetchedAt));
        fxRateRepository.save(new FxRate(BASE_CURRENCY, KRW, krwPerEur, SOURCE, fetchedAt));

        return new FxResponse(usdPerEur, krwPerEur, asOf, SOURCE, ATTRIBUTION, ATTRIBUTION_URL);
    }

    private FxResponse fromCachedPair(FxRate usdRow, FxRate krwRow) {
        String asOf = formatAsOf(usdRow.getFetchedAt().atZone(ZoneOffset.UTC));
        return new FxResponse(usdRow.getRate(), krwRow.getRate(), asOf, SOURCE, ATTRIBUTION, ATTRIBUTION_URL);
    }

    private String formatAsOf(ZonedDateTime zonedDateTime) {
        return AS_OF_FORMATTER.format(zonedDateTime);
    }
}

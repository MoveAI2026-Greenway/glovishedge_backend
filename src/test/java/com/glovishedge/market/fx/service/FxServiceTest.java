package com.glovishedge.market.fx.service;

import com.glovishedge.market.fx.client.ExchangeRateClient;
import com.glovishedge.market.fx.dto.ExchangeRateApiResponse;
import com.glovishedge.market.fx.dto.FxResponse;
import com.glovishedge.market.fx.entity.FxRate;
import com.glovishedge.market.fx.exception.ExchangeRateClientException;
import com.glovishedge.market.fx.exception.FxUnavailableException;
import com.glovishedge.market.fx.repository.FxRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FxServiceTest {

    @Mock
    private FxRateRepository fxRateRepository;

    @Mock
    private ExchangeRateClient exchangeRateClient;

    private FxService fxService;

    @BeforeEach
    void setUp() {
        fxService = new FxService(fxRateRepository, exchangeRateClient);
    }

    private FxRate cachedRate(String quote, BigDecimal rate, Instant fetchedAt) {
        return new FxRate("EUR", quote, rate, "open.er-api.com", fetchedAt);
    }

    private ExchangeRateApiResponse successResponse() {
        return new ExchangeRateApiResponse(
                "success",
                "USD",
                "Sun, 09 Aug 2026 00:00:02 +0000",
                Map.of(
                        "EUR", new BigDecimal("0.8659527500000001"),
                        "KRW", new BigDecimal("1412.35")
                )
        );
    }

    @Test
    void cacheHit_doesNotCallExternalApiAndReturnsDbValue() {
        Instant fresh = Instant.now().minus(1, ChronoUnit.HOURS);
        when(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc("EUR", "USD"))
                .thenReturn(Optional.of(cachedRate("USD", new BigDecimal("1.15"), fresh)));
        when(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc("EUR", "KRW"))
                .thenReturn(Optional.of(cachedRate("KRW", new BigDecimal("1630.5"), fresh)));

        FxResponse response = fxService.getFx();

        assertThat(response.usdPerEur()).isEqualByComparingTo("1.15");
        assertThat(response.krwPerEur()).isEqualByComparingTo("1630.5");
        assertThat(response.source()).isEqualTo("open.er-api.com");
        assertThat(response.attribution()).isEqualTo("Rates By Exchange Rate API");
        assertThat(response.attributionUrl()).isEqualTo("https://www.exchangerate-api.com");
        verify(exchangeRateClient, never()).fetchUsdBaseRates();
    }

    @Test
    void cacheMiss_callsApiComputesRatesAndSaves() {
        when(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(eq("EUR"), any()))
                .thenReturn(Optional.empty());
        when(exchangeRateClient.fetchUsdBaseRates()).thenReturn(successResponse());

        FxResponse response = fxService.getFx();

        // 1 EUR = 1 / 0.8659527500000001 USD
        BigDecimal expectedUsdPerEur = BigDecimal.ONE.divide(
                new BigDecimal("0.8659527500000001"), java.math.MathContext.DECIMAL128);
        assertThat(response.usdPerEur()).isEqualByComparingTo(expectedUsdPerEur);

        BigDecimal expectedKrwPerEur = new BigDecimal("1412.35").divide(
                new BigDecimal("0.8659527500000001"), java.math.MathContext.DECIMAL128);
        assertThat(response.krwPerEur()).isEqualByComparingTo(expectedKrwPerEur);

        assertThat(response.asOf()).isEqualTo("09 Aug 2026");

        verify(fxRateRepository).save(argThatQuote("USD", expectedUsdPerEur));
        verify(fxRateRepository).save(argThatQuote("KRW", expectedKrwPerEur));
    }

    private FxRate argThatQuote(String quote, BigDecimal rate) {
        return org.mockito.ArgumentMatchers.argThat(fxRate ->
                fxRate.getQuoteCurrency().equals(quote) && fxRate.getRate().compareTo(rate) == 0);
    }

    @Test
    void apiFailure_withStaleCache_returnsStaleValue() {
        Instant stale = Instant.now().minus(48, ChronoUnit.HOURS);
        when(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc("EUR", "USD"))
                .thenReturn(Optional.of(cachedRate("USD", new BigDecimal("1.10"), stale)));
        when(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc("EUR", "KRW"))
                .thenReturn(Optional.of(cachedRate("KRW", new BigDecimal("1600.0"), stale)));
        when(exchangeRateClient.fetchUsdBaseRates())
                .thenThrow(new ExchangeRateClientException("open.er-api.com 호출 실패"));

        FxResponse response = fxService.getFx();

        assertThat(response.usdPerEur()).isEqualByComparingTo("1.10");
        assertThat(response.krwPerEur()).isEqualByComparingTo("1600.0");
        verify(fxRateRepository, never()).save(any());
    }

    @Test
    void apiFailure_withoutAnyCache_throwsFxUnavailable() {
        when(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(eq("EUR"), any()))
                .thenReturn(Optional.empty());
        when(exchangeRateClient.fetchUsdBaseRates())
                .thenThrow(new ExchangeRateClientException("open.er-api.com 호출 실패"));

        assertThatThrownBy(() -> fxService.getFx())
                .isInstanceOf(FxUnavailableException.class);
    }

    @Test
    void eurMissing_isTreatedAsClientFailure_fallsBackToStale() {
        Instant stale = Instant.now().minus(48, ChronoUnit.HOURS);
        when(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc("EUR", "USD"))
                .thenReturn(Optional.of(cachedRate("USD", new BigDecimal("1.10"), stale)));
        when(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc("EUR", "KRW"))
                .thenReturn(Optional.of(cachedRate("KRW", new BigDecimal("1600.0"), stale)));
        // ExchangeRateClient는 EUR 누락을 자체적으로 검증해 예외를 던진다 — 여기서는 그 계약을 mock으로 재현한다.
        when(exchangeRateClient.fetchUsdBaseRates())
                .thenThrow(new ExchangeRateClientException("open.er-api.com 응답에 EUR 환율이 없습니다"));

        FxResponse response = fxService.getFx();

        assertThat(response.usdPerEur()).isEqualByComparingTo("1.10");
    }

    @Test
    void krwMissing_isTreatedAsClientFailure_throwsWhenNoCache() {
        when(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(eq("EUR"), any()))
                .thenReturn(Optional.empty());
        when(exchangeRateClient.fetchUsdBaseRates())
                .thenThrow(new ExchangeRateClientException("open.er-api.com 응답에 KRW 환율이 없습니다"));

        assertThatThrownBy(() -> fxService.getFx())
                .isInstanceOf(FxUnavailableException.class);
    }

    @Test
    void eurRateZero_isTreatedAsClientFailure_preventsDivideByZero() {
        when(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(eq("EUR"), any()))
                .thenReturn(Optional.empty());
        when(exchangeRateClient.fetchUsdBaseRates())
                .thenThrow(new ExchangeRateClientException("open.er-api.com EUR 환율이 0입니다"));

        assertThatThrownBy(() -> fxService.getFx())
                .isInstanceOf(FxUnavailableException.class);
    }
}

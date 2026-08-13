package com.glovishedge.market.eua.service;

import com.glovishedge.market.eua.client.EuaMarketClient;
import com.glovishedge.market.eua.dto.EuaQuote;
import com.glovishedge.market.eua.dto.EuaResponse;
import com.glovishedge.market.eua.entity.EuaPrice;
import com.glovishedge.market.eua.exception.EuaMarketClientException;
import com.glovishedge.market.eua.exception.EuaUnavailableException;
import com.glovishedge.market.eua.repository.EuaPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EuaServiceTest {

    @Mock
    private EuaPriceRepository euaPriceRepository;

    @Mock
    private EuaMarketClient euaMarketClient;

    private EuaService euaService;

    @BeforeEach
    void setUp() {
        euaService = new EuaService(euaPriceRepository, euaMarketClient);
    }

    private EuaPrice row(LocalDate date, BigDecimal price, Instant fetchedAt) {
        return new EuaPrice(date, price, "Yahoo Finance · CO2.L", fetchedAt);
    }

    // DB findTop5는 최신순(desc)으로 반환한다.
    private List<EuaPrice> desc(EuaPrice... rows) {
        return List.of(rows);
    }

    @Test
    void cacheHit_withFreshRows_doesNotCallExternalApiAndIsLiveTrue() {
        Instant fresh = Instant.now().minus(10, ChronoUnit.MINUTES);
        EuaPrice today = row(LocalDate.of(2026, 8, 7), new BigDecimal("79.01"), fresh);
        EuaPrice yesterday = row(LocalDate.of(2026, 8, 6), new BigDecimal("77.65"), fresh);
        when(euaPriceRepository.findTop5ByOrderByPriceDateDesc()).thenReturn(desc(today, yesterday));

        EuaResponse response = euaService.getEua();

        assertThat(response.spot()).isEqualByComparingTo("79.01");
        assertThat(response.asOf()).isEqualTo("2026-08-07");
        assertThat(response.currency()).isEqualTo("EUR");
        assertThat(response.symbol()).isEqualTo("CO2.L");
        assertThat(response.source()).isEqualTo("Yahoo Finance · CO2.L");
        assertThat(response.isProxy()).isTrue();
        assertThat(response.isLive()).isTrue();
        // DB는 최신순으로 왔지만 recentCloses는 시간순(과거→현재)이어야 한다.
        assertThat(response.recentCloses()).containsExactly(new BigDecimal("77.65"), new BigDecimal("79.01"));

        BigDecimal expectedPct = new BigDecimal("79.01").subtract(new BigDecimal("77.65"))
                .divide(new BigDecimal("77.65"), java.math.MathContext.DECIMAL128)
                .multiply(BigDecimal.valueOf(100));
        assertThat(response.dayChangePct()).isEqualByComparingTo(expectedPct);

        verify(euaMarketClient, never()).fetchLatestQuote();
    }

    @Test
    void cacheMiss_callsApiComputesChangeSavesAndIsLiveTrue() {
        when(euaPriceRepository.findTop5ByOrderByPriceDateDesc()).thenReturn(List.of());

        Instant t0 = Instant.parse("2026-08-06T16:30:00Z");
        Instant t1 = Instant.parse("2026-08-07T16:30:00Z");
        EuaQuote quote = new EuaQuote(List.of(t0, t1), List.of(new BigDecimal("77.65"), new BigDecimal("79.01")));
        when(euaMarketClient.fetchLatestQuote()).thenReturn(quote);
        when(euaPriceRepository.findByPriceDate(LocalDate.of(2026, 8, 7))).thenReturn(Optional.empty());

        EuaResponse response = euaService.getEua();

        assertThat(response.spot()).isEqualByComparingTo("79.01");
        assertThat(response.asOf()).isEqualTo("2026-08-07");
        assertThat(response.isLive()).isTrue();
        assertThat(response.recentCloses()).containsExactly(new BigDecimal("77.65"), new BigDecimal("79.01"));
        verify(euaPriceRepository).save(any(EuaPrice.class));
    }

    @Test
    void cacheExpired_callsApiAgain() {
        Instant stale = Instant.now().minus(31, ChronoUnit.MINUTES);
        EuaPrice today = row(LocalDate.of(2026, 8, 7), new BigDecimal("79.01"), stale);
        EuaPrice yesterday = row(LocalDate.of(2026, 8, 6), new BigDecimal("77.65"), stale);
        when(euaPriceRepository.findTop5ByOrderByPriceDateDesc()).thenReturn(desc(today, yesterday));

        Instant t0 = Instant.parse("2026-08-07T16:30:00Z");
        Instant t1 = Instant.parse("2026-08-08T16:30:00Z");
        EuaQuote quote = new EuaQuote(List.of(t0, t1), List.of(new BigDecimal("79.01"), new BigDecimal("80.00")));
        when(euaMarketClient.fetchLatestQuote()).thenReturn(quote);
        when(euaPriceRepository.findByPriceDate(LocalDate.of(2026, 8, 8))).thenReturn(Optional.empty());

        EuaResponse response = euaService.getEua();

        assertThat(response.spot()).isEqualByComparingTo("80.00");
        verify(euaMarketClient).fetchLatestQuote();
    }

    @Test
    void apiFailure_withStaleRows_returnsStaleComputedChangeAndIsLiveFalse() {
        Instant stale = Instant.now().minus(2, ChronoUnit.HOURS);
        EuaPrice today = row(LocalDate.of(2026, 8, 7), new BigDecimal("79.01"), stale);
        EuaPrice yesterday = row(LocalDate.of(2026, 8, 6), new BigDecimal("77.65"), stale);
        when(euaPriceRepository.findTop5ByOrderByPriceDateDesc()).thenReturn(desc(today, yesterday));
        when(euaMarketClient.fetchLatestQuote())
                .thenThrow(new EuaMarketClientException("Yahoo Finance 호출 실패"));

        EuaResponse response = euaService.getEua();

        assertThat(response.spot()).isEqualByComparingTo("79.01");
        assertThat(response.isLive()).isFalse();
        verify(euaPriceRepository, never()).save(any());
    }

    @Test
    void apiFailure_withoutEnoughCache_throwsEuaUnavailable() {
        when(euaPriceRepository.findTop5ByOrderByPriceDateDesc()).thenReturn(List.of());
        when(euaMarketClient.fetchLatestQuote())
                .thenThrow(new EuaMarketClientException("Yahoo Finance 호출 실패"));

        assertThatThrownBy(() -> euaService.getEua())
                .isInstanceOf(EuaUnavailableException.class);
    }

    @Test
    void apiFailure_withOnlyOneCachedRow_throwsEuaUnavailable() {
        Instant stale = Instant.now().minus(2, ChronoUnit.HOURS);
        EuaPrice onlyRow = row(LocalDate.of(2026, 8, 7), new BigDecimal("79.01"), stale);
        when(euaPriceRepository.findTop5ByOrderByPriceDateDesc()).thenReturn(desc(onlyRow));
        when(euaMarketClient.fetchLatestQuote())
                .thenThrow(new EuaMarketClientException("Yahoo Finance 호출 실패"));

        assertThatThrownBy(() -> euaService.getEua())
                .isInstanceOf(EuaUnavailableException.class);
    }

    @Test
    void malformedResponse_isTreatedAsClientFailure_fallsBackToStale() {
        Instant stale = Instant.now().minus(2, ChronoUnit.HOURS);
        EuaPrice today = row(LocalDate.of(2026, 8, 7), new BigDecimal("79.01"), stale);
        EuaPrice yesterday = row(LocalDate.of(2026, 8, 6), new BigDecimal("77.65"), stale);
        when(euaPriceRepository.findTop5ByOrderByPriceDateDesc()).thenReturn(desc(today, yesterday));
        when(euaMarketClient.fetchLatestQuote())
                .thenThrow(new EuaMarketClientException("Yahoo Finance 응답에 종가(price)가 없습니다"));

        EuaResponse response = euaService.getEua();

        assertThat(response.spot()).isEqualByComparingTo("79.01");
    }

    @Test
    void previousCloseZero_throwsEuaUnavailable() {
        when(euaPriceRepository.findTop5ByOrderByPriceDateDesc()).thenReturn(List.of());

        Instant t0 = Instant.parse("2026-08-06T16:30:00Z");
        Instant t1 = Instant.parse("2026-08-07T16:30:00Z");
        EuaQuote quote = new EuaQuote(List.of(t0, t1), List.of(BigDecimal.ZERO, new BigDecimal("79.01")));
        when(euaMarketClient.fetchLatestQuote()).thenReturn(quote);

        assertThatThrownBy(() -> euaService.getEua())
                .isInstanceOf(EuaUnavailableException.class);
    }

    @Test
    void recentCloses_cappedAtFiveFromFreshFetch() {
        when(euaPriceRepository.findTop5ByOrderByPriceDateDesc()).thenReturn(List.of());

        List<Instant> timestamps = List.of(
                Instant.parse("2026-08-03T16:30:00Z"), Instant.parse("2026-08-04T16:30:00Z"),
                Instant.parse("2026-08-05T16:30:00Z"), Instant.parse("2026-08-06T16:30:00Z"),
                Instant.parse("2026-08-07T16:30:00Z"), Instant.parse("2026-08-08T16:30:00Z"));
        List<BigDecimal> closes = List.of(
                new BigDecimal("75"), new BigDecimal("76"), new BigDecimal("77"),
                new BigDecimal("78"), new BigDecimal("79"), new BigDecimal("80"));
        when(euaMarketClient.fetchLatestQuote()).thenReturn(new EuaQuote(timestamps, closes));
        when(euaPriceRepository.findByPriceDate(any())).thenReturn(Optional.empty());

        EuaResponse response = euaService.getEua();

        assertThat(response.recentCloses()).hasSize(5);
        assertThat(response.recentCloses()).containsExactly(
                new BigDecimal("76"), new BigDecimal("77"), new BigDecimal("78"),
                new BigDecimal("79"), new BigDecimal("80"));
    }
}

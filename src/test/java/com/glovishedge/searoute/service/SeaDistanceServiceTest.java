package com.glovishedge.searoute.service;

import com.glovishedge.searoute.dto.RouteResult;
import com.glovishedge.searoute.dto.SeaDistanceResponse;
import com.glovishedge.searoute.exception.InvalidCoordinateException;
import com.glovishedge.searoute.exception.SeaRouteProviderException;
import com.glovishedge.searoute.exception.SeaRouteUnavailableException;
import com.glovishedge.searoute.provider.SeaRouteProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeaDistanceServiceTest {

    private static final double BUSAN_LNG = 129.04;
    private static final double BUSAN_LAT = 35.1;
    private static final double HAMBURG_LNG = 10;
    private static final double HAMBURG_LAT = 53.55;

    @Test
    void allFourRoutesComputed_whenProviderSucceeds() {
        SeaRouteProvider provider = mock(SeaRouteProvider.class);
        when(provider.computeRoute(any(), any()))
                .thenReturn(new RouteResult(11259.0, List.of(List.of(35.1, 129.04), List.of(53.55, 10.0))));
        SeaDistanceService service = new SeaDistanceService(provider);

        SeaDistanceResponse response = service.getSeaDistance(BUSAN_LNG, BUSAN_LAT, HAMBURG_LNG, HAMBURG_LAT);

        assertThat(response.routes()).containsOnlyKeys("A", "B", "C", "D");
    }

    @Test
    void routeC_usesCapeRestrictions() {
        SeaRouteProvider provider = mock(SeaRouteProvider.class);
        when(provider.computeRoute(any(), any()))
                .thenReturn(new RouteResult(1.0, List.of()));
        SeaDistanceService service = new SeaDistanceService(provider);

        service.getSeaDistance(BUSAN_LNG, BUSAN_LAT, HAMBURG_LNG, HAMBURG_LAT);

        verify(provider).computeRoute(any(), eq(List.of("suez", "babelmandeb", "panama")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void routeA_and_D_includeFixedSingaporeAndColomboWaypoints() {
        SeaRouteProvider provider = mock(SeaRouteProvider.class);
        when(provider.computeRoute(any(), any())).thenReturn(new RouteResult(1.0, List.of()));
        SeaDistanceService service = new SeaDistanceService(provider);

        ArgumentCaptor<List<double[]>> pointsCaptor = ArgumentCaptor.forClass(List.class);
        service.getSeaDistance(BUSAN_LNG, BUSAN_LAT, HAMBURG_LNG, HAMBURG_LAT);
        verify(provider, times(4)).computeRoute(pointsCaptor.capture(), any());

        List<List<double[]>> allCalls = pointsCaptor.getAllValues();
        // A(1st call): from, Singapore, to = 3 points
        assertThat(allCalls.get(0)).hasSize(3);
        assertThat(allCalls.get(0).get(1)).containsExactly(103.85, 1.28);
        // B(2nd call): from, to = 2 points, direct
        assertThat(allCalls.get(1)).hasSize(2);
        // D(4th call): from, Singapore, Colombo, to = 4 points
        assertThat(allCalls.get(3)).hasSize(4);
        assertThat(allCalls.get(3).get(1)).containsExactly(103.85, 1.28);
        assertThat(allCalls.get(3).get(2)).containsExactly(79.85, 6.9);
    }

    @Test
    void oneRouteFails_othersStillReturned() {
        SeaRouteProvider provider = mock(SeaRouteProvider.class);
        when(provider.computeRoute(any(), eq(List.of("suez", "babelmandeb", "panama"))))
                .thenThrow(new SeaRouteProviderException("Node 실패"));
        when(provider.computeRoute(any(), eq(List.of())))
                .thenReturn(new RouteResult(11259.0, List.of()));
        SeaDistanceService service = new SeaDistanceService(provider);

        SeaDistanceResponse response = service.getSeaDistance(BUSAN_LNG, BUSAN_LAT, HAMBURG_LNG, HAMBURG_LAT);

        assertThat(response.routes()).containsKeys("A", "B", "D");
        assertThat(response.routes()).doesNotContainKey("C");
    }

    @Test
    void allRoutesFail_throwsUnavailable() {
        SeaRouteProvider provider = mock(SeaRouteProvider.class);
        when(provider.computeRoute(any(), any())).thenThrow(new SeaRouteProviderException("Node 다운"));
        SeaDistanceService service = new SeaDistanceService(provider);

        assertThatThrownBy(() -> service.getSeaDistance(BUSAN_LNG, BUSAN_LAT, HAMBURG_LNG, HAMBURG_LAT))
                .isInstanceOf(SeaRouteUnavailableException.class);
    }

    @Test
    void invalidCoordinate_rejectedBeforeCallingProvider() {
        SeaRouteProvider provider = mock(SeaRouteProvider.class);
        SeaDistanceService service = new SeaDistanceService(provider);

        assertThatThrownBy(() -> service.getSeaDistance(999, 0, HAMBURG_LNG, HAMBURG_LAT))
                .isInstanceOf(InvalidCoordinateException.class);
    }
}

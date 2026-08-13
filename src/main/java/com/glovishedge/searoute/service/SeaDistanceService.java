package com.glovishedge.searoute.service;

import com.glovishedge.searoute.dto.RouteResult;
import com.glovishedge.searoute.dto.SeaDistanceResponse;
import com.glovishedge.searoute.exception.InvalidCoordinateException;
import com.glovishedge.searoute.exception.SeaRouteProviderException;
import com.glovishedge.searoute.exception.SeaRouteUnavailableException;
import com.glovishedge.searoute.provider.SeaRouteProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 08_데이터시드.md §5의 항로별 경유지·통과 금지 정의를 그대로 옮긴다:
 * A 싱가포르 경유 / B 직항 / C 희망봉(수에즈·바브엘만데브·파나마 차단) / D 싱가포르→콜롬보 경유.
 * 사용자가 고르는 출발/도착 좌표는 매 요청 그대로 쓰고, 경유지(싱가포르·콜롬보)는 항로 고정값이다.
 *
 * <p>nm과 path는 항상 {@link SeaRouteProvider}의 같은 호출 결과에서 나온다 — 절대 다른 계산을
 * 섞지 않는다. 항로 하나의 계산이 실패해도 나머지는 계속 반환한다(개별 폴백); 넷 다 실패하면
 * 가짜 거리를 만드는 대신 {@link SeaRouteUnavailableException}을 던진다.
 */
@Service
public class SeaDistanceService {

    private static final double[] SINGAPORE = {103.85, 1.28};
    private static final double[] COLOMBO = {79.85, 6.9};

    private static final List<String> CAPE_RESTRICTIONS = List.of("suez", "babelmandeb", "panama");
    private static final List<String> NO_RESTRICTIONS = List.of();

    private final SeaRouteProvider seaRouteProvider;

    public SeaDistanceService(SeaRouteProvider seaRouteProvider) {
        this.seaRouteProvider = seaRouteProvider;
    }

    public SeaDistanceResponse getSeaDistance(double fromLng, double fromLat, double toLng, double toLat) {
        validateCoordinate(fromLng, fromLat, "from");
        validateCoordinate(toLng, toLat, "to");

        double[] from = {fromLng, fromLat};
        double[] to = {toLng, toLat};

        Map<String, RouteResult> routes = new LinkedHashMap<>();
        tryCompute(routes, "A", List.of(from, SINGAPORE, to), NO_RESTRICTIONS);
        tryCompute(routes, "B", List.of(from, to), NO_RESTRICTIONS);
        tryCompute(routes, "C", List.of(from, to), CAPE_RESTRICTIONS);
        tryCompute(routes, "D", List.of(from, SINGAPORE, COLOMBO, to), NO_RESTRICTIONS);

        if (routes.isEmpty()) {
            throw new SeaRouteUnavailableException(
                    "해상 항로 거리 계산 서비스에 연결할 수 없습니다 (모든 항로 계산 실패)");
        }
        return new SeaDistanceResponse(routes);
    }

    private void tryCompute(Map<String, RouteResult> routes, String key,
                             List<double[]> points, List<String> restrictions) {
        try {
            routes.put(key, seaRouteProvider.computeRoute(points, restrictions));
        } catch (SeaRouteProviderException e) {
            // 계산 실패한 항로만 개별 폴백 — 03_API계약.md §3. 나머지 항로는 계속 진행한다.
        }
    }

    private void validateCoordinate(double lng, double lat, String label) {
        if (Double.isNaN(lng) || Double.isNaN(lat) || lng < -180 || lng > 180 || lat < -90 || lat > 90) {
            throw new InvalidCoordinateException(
                    label + " 좌표가 유효하지 않습니다: lng=" + lng + ", lat=" + lat);
        }
    }
}

package com.glovishedge.searoute.provider;

import com.glovishedge.searoute.dto.RouteResult;
import com.glovishedge.searoute.exception.SeaRouteProviderException;

import java.util.List;

/**
 * 해상 항로 거리+경로 계산 추상화. PROVISIONAL(docs/PROVISIONAL_BACKEND_DECISIONS.md):
 * 현재는 {@link NodeSeaRouteProvider}(Node sidecar, searoute-ts) 하나만 구현돼 있다.
 * 향후 다른 routing 백엔드로 교체하더라도 Service/Controller는 이 인터페이스만 알면 된다.
 */
public interface SeaRouteProvider {

    /**
     * @param pointsLngLat 출발→(경유)→도착 순서의 [lng, lat] 좌표 목록(2개 이상)
     * @param restrictions 통과 금지 passage 이름 목록(예: "suez", "panama", "babelmandeb")
     */
    RouteResult computeRoute(List<double[]> pointsLngLat, List<String> restrictions) throws SeaRouteProviderException;
}

package com.glovishedge.searoute.provider;

import com.glovishedge.searoute.dto.RouteResult;
import com.glovishedge.searoute.exception.SeaRouteProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PROVISIONAL(docs/PROVISIONAL_BACKEND_DECISIONS.md §A) — searoute-ts를 Java에 직접 포팅하지
 * 않고, 별도 Node sidecar(searoute-service/)의 내부 {@code POST /route}를 호출한다.
 * base URL은 하드코딩하지 않고 {@code SEA_ROUTE_BASE_URL} 환경변수로 받는다. 이 빈은 생성 시
 * 네트워크 호출을 하지 않으므로, Node sidecar가 꺼져 있어도 Spring Boot는 정상 기동한다 —
 * 실패는 실제 {@link #computeRoute} 호출 시점에만 {@link SeaRouteProviderException}으로 드러난다.
 */
@Component
public class NodeSeaRouteProvider implements SeaRouteProvider {

    private final RestClient restClient;
    private final String baseUrl;

    public NodeSeaRouteProvider(RestClient.Builder restClientBuilder,
                                 @Value("${sea-route.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = baseUrl;
    }

    @Override
    public RouteResult computeRoute(List<double[]> pointsLngLat, List<String> restrictions) {
        List<List<Double>> points = pointsLngLat.stream()
                .map(p -> List.of(p[0], p[1]))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("points", points);
        body.put("restrictions", restrictions);

        NodeRouteResponse response;
        try {
            response = restClient.post()
                    .uri(baseUrl + "/route")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(NodeRouteResponse.class);
        } catch (RestClientException e) {
            throw new SeaRouteProviderException("searoute-service(Node sidecar) 호출 실패", e);
        }

        if (response == null || response.nm() == null || response.path() == null) {
            throw new SeaRouteProviderException("searoute-service 응답이 비어 있거나 불완전합니다");
        }

        return new RouteResult(response.nm(), response.path());
    }
}

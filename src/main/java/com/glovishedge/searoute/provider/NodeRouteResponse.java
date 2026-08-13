package com.glovishedge.searoute.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * searoute-service(Node sidecar) {@code POST /route} 응답. provider 패키지 내부 전용 —
 * 외부에는 {@link com.glovishedge.searoute.dto.RouteResult}로만 노출한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NodeRouteResponse(Double nm, List<List<Double>> path) {
}

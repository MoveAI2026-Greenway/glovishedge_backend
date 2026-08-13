package com.glovishedge.searoute.exception;

/**
 * 4개 항로 전부 계산 실패(Node sidecar 다운 등). 가짜 거리를 만들지 않고 명확히 실패시킨다. 503.
 */
public class SeaRouteUnavailableException extends RuntimeException {

    public SeaRouteUnavailableException(String message) {
        super(message);
    }
}

package com.glovishedge.searoute.exception;

/**
 * {@link com.glovishedge.searoute.provider.SeaRouteProvider} 구현체(Node sidecar 등) 호출 실패.
 * Service는 이 예외를 항로 하나의 개별 실패로 취급해 나머지 항로 계산을 계속 진행한다.
 */
public class SeaRouteProviderException extends RuntimeException {

    public SeaRouteProviderException(String message) {
        super(message);
    }

    public SeaRouteProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}

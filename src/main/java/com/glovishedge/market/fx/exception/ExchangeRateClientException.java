package com.glovishedge.market.fx.exception;

/**
 * open.er-api.com 호출/응답 검증 실패. FxService는 이 예외를 "외부 API 실패"로 취급해
 * stale cache fallback 여부를 판단한다.
 */
public class ExchangeRateClientException extends RuntimeException {

    public ExchangeRateClientException(String message) {
        super(message);
    }

    public ExchangeRateClientException(String message, Throwable cause) {
        super(message, cause);
    }
}

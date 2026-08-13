package com.glovishedge.market.eua.exception;

/**
 * Yahoo Finance 호출/응답 검증 실패. EuaService는 이 예외를 "외부 API 실패"로 취급해
 * stale cache fallback 여부를 판단한다.
 */
public class EuaMarketClientException extends RuntimeException {

    public EuaMarketClientException(String message) {
        super(message);
    }

    public EuaMarketClientException(String message, Throwable cause) {
        super(message, cause);
    }
}

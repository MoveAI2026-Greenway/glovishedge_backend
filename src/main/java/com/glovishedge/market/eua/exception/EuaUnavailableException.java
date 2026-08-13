package com.glovishedge.market.eua.exception;

/**
 * 외부 API 호출 실패 + 사용 가능한 stale cache도 없는 경우(또는 등락률을 계산할 수 없는 경우).
 * 임의 EUA 가격/등락률을 만들지 않고 던진다.
 */
public class EuaUnavailableException extends RuntimeException {

    public EuaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.glovishedge.market.fx.exception;

/**
 * 외부 API 호출 실패 + 사용 가능한 stale cache도 없는 경우. 임의 숫자를 만들지 않고 던진다.
 */
public class FxUnavailableException extends RuntimeException {

    public FxUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

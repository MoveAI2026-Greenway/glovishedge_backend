package com.glovishedge.ai.exception;

/**
 * LlmGatewayClient 호출/응답 처리 실패(HTTP 실패, 본문 오류 필드, 응답 JSON 파싱 실패 등). 503.
 */
public class LlmGatewayException extends RuntimeException {

    public LlmGatewayException(String message) {
        super(message);
    }

    public LlmGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}

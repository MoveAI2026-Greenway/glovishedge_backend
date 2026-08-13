package com.glovishedge.ai.exception;

/**
 * LLM provider/API key가 아직 확정되지 않아 {@link com.glovishedge.ai.client.LlmGatewayClient}
 * 구현체가 없는 경우. 가짜 설명을 만들지 않고 명확히 실패시킨다. 503.
 */
public class LlmGatewayNotConfiguredException extends RuntimeException {

    public LlmGatewayNotConfiguredException(String message) {
        super(message);
    }
}

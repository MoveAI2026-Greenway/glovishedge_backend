package com.glovishedge.ai.client;

/**
 * LLM Gateway 추상화 — provider/model이 명세에서 아직 확정되지 않아 구현체가 없다
 * (com.glovishedge.ai.service.CompareRoutesService에서 {@code Optional<LlmGatewayClient>}로
 * 주입받아, 빈이 없어도 애플리케이션이 정상 기동하도록 한다).
 *
 * <p>구현체는 HTTP status만으로 성공을 판단하지 말고, 게이트웨이가 200 본문에 실어 보내는
 * 오류(예: 크레딧 소진)까지 확인해 실패 시 {@link LlmGatewayException}을 던져야 한다
 * (03_API계약.md §5 "실제로 겪은 함정").
 */
public interface LlmGatewayClient {

    String complete(String systemPrompt, String userMessage);
}

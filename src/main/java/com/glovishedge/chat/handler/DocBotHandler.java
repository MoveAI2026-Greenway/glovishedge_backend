package com.glovishedge.chat.handler;

import com.glovishedge.ai.client.LlmGatewayClient;
import com.glovishedge.ai.exception.LlmGatewayException;
import com.glovishedge.chat.dto.ChatRequest;
import com.glovishedge.chat.dto.ChatResponse;
import com.glovishedge.chat.exception.ChatUnavailableException;
import com.glovishedge.chat.exception.InvalidChatRequestException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 05_프롬프트_전문.md §8 — 월간 리스크 보고서 초안. 엔진 계산값은 여기서 새로 만들지 않고
 * 호출자가 message/ctx로 실어 보낸 스냅샷만 그대로 인용한다(risk 엔진 자체는 이 환경에
 * 미구성 — RiskBotHandler 참고). 화면에 보여준 파일 추출 텍스트가 있다면 그것도
 * message에 이미 포함돼 온다는 전제(POST /api/extract-text 결과를 프런트가 입력창에 채우는
 * 기존 흐름, 09_문구집.md §11)를 따른다 — 여기서 별도 파싱을 다시 하지 않는다.
 */
@Component
public class DocBotHandler implements BotHandler {

    private static final String SYSTEM_PROMPT = """
            당신은 리스크 관리 보고서 작성자다. 주어진 지표로 월간 정기보고 초안을 만든다.
            절대 규칙: 주어진 입력에 없는 수치를 새로 만들어내지 마라. 금액·확률·비율은 입력값을
            그대로 인용만 하고, 계산이 필요하면 입력값끼리의 단순 비교(더 크다/작다)까지만 하라.
            모르는 것은 "제공된 자료로는 알 수 없음"이라고 쓴다.
            검증 사실(반드시 한계로 언급): 80% 예측구간의 실제 포함률은 워크포워드 1,728일 검증에서
            77.8~84.2%로 목표치에 근접했다. 그러나 가격의 방향(점예측)은 랜덤워크를 이기지 못했고,
            Brier 기준 우위는 통계적으로 유의하지 않다. 따라서 "가격이 오를/내릴 것"이라고 단정하지 마라.
            모든 출력은 한국어로 작성한다(고유명사·코드 제외).
            JSON: {"title":"제목","period":"대상 기간","executiveSummary":"3~4문장 요약",
             "sections":[{"heading":"소제목","body":"본문","keyNumbers":["핵심 수치"]}]}""";

    private final Optional<LlmGatewayClient> llmGatewayClient;
    private final ObjectMapper objectMapper;

    public DocBotHandler(Optional<LlmGatewayClient> llmGatewayClient, ObjectMapper objectMapper) {
        this.llmGatewayClient = llmGatewayClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String botId() {
        return "doc";
    }

    @Override
    public ChatResponse handle(ChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw new InvalidChatRequestException("doc bot은 보고서 재료가 될 message가 필요합니다");
        }
        if (llmGatewayClient.isEmpty()) {
            throw new ChatUnavailableException("doc bot을 사용하려면 LLM Gateway가 필요합니다");
        }

        String raw = llmGatewayClient.get().complete(SYSTEM_PROMPT, request.message());

        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (JacksonException e) {
            throw new LlmGatewayException("doc bot 응답을 예상된 JSON 형식으로 해석할 수 없습니다", e);
        }

        String summary = String.valueOf(parsed.getOrDefault("executiveSummary", ""));
        return new ChatResponse("doc", summary, List.of(), parsed);
    }
}

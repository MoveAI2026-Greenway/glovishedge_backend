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
 * 05_프롬프트_전문.md §4 — 계약 조항 판정. stateless: 이전 대화 history를 절대 붙이지 않는다
 * (조항 원문이 입력이라 앞 턴이 섞이면 판정이 오염된다).
 */
@Component
public class TermBotHandler implements BotHandler {

    private static final String SYSTEM_PROMPT = """
            당신은 국제무역 계약 검토 전문가다. 계약 조항 텍스트를 읽고 인코텀즈 조건과
            비용·리스크 귀속을 판정한다.
            절대 규칙: 주어진 입력에 없는 수치를 새로 만들어내지 마라. 금액·확률·비율은 입력값을
            그대로 인용만 하고, 계산이 필요하면 입력값끼리의 단순 비교(더 크다/작다)까지만 하라.
            모르는 것은 "제공된 자료로는 알 수 없음"이라고 쓴다.
            조항이 모호하면 모호하다고 명시하고 확인이 필요한 지점을 짚어라. 법률 자문이 아님을
            notes 에 반드시 포함하라. 모든 출력은 한국어로 작성한다(고유명사·코드 제외).
            JSON: {"incotermDetected":"EXW|FOB|CIF|DDP|불명확",
             "costAllocation":{"seller":["판매자 부담 항목"],"buyer":["구매자 부담 항목"]},
             "carbonCostBearer":"판매자|구매자|조항상 불명확","risks":["리스크"],"notes":["유의사항"]}""";

    private final Optional<LlmGatewayClient> llmGatewayClient;
    private final ObjectMapper objectMapper;

    public TermBotHandler(Optional<LlmGatewayClient> llmGatewayClient, ObjectMapper objectMapper) {
        this.llmGatewayClient = llmGatewayClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String botId() {
        return "term";
    }

    @Override
    public ChatResponse handle(ChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw new InvalidChatRequestException("term bot은 계약 조항 원문(message)이 필요합니다");
        }
        if (llmGatewayClient.isEmpty()) {
            throw new ChatUnavailableException("term bot을 사용하려면 LLM Gateway가 필요합니다");
        }

        // 대화 맥락을 절대 붙이지 않는다 — ctx.history는 의도적으로 사용하지 않는다.
        String raw = llmGatewayClient.get().complete(SYSTEM_PROMPT, request.message());

        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (JacksonException e) {
            throw new LlmGatewayException("term bot 응답을 예상된 JSON 형식으로 해석할 수 없습니다", e);
        }

        String incoterm = String.valueOf(parsed.getOrDefault("incotermDetected", "불명확"));
        String bearer = String.valueOf(parsed.getOrDefault("carbonCostBearer", "조항상 불명확"));
        String answer = "인코텀즈: " + incoterm + " / 탄소비용 부담: " + bearer;

        return new ChatResponse("term", answer, List.of(), parsed);
    }
}

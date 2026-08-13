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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 05_프롬프트_전문.md §1 lookup 갈래 + 08_데이터시드.md §7 "화면 앵커" 15개 목록.
 * 목록에 없는 id를 만들어내지 않는다 — LLM이 벗어난 값을 내면 unsupported로 처리한다.
 */
@Component
public class NavBotHandler implements BotHandler {

    // 08_데이터시드.md §7 원문 그대로 — 총 15개.
    private static final Set<String> SCREEN_ANCHORS = Set.of(
            "route-kpi", "route-map", "route-table", "route-compare", "route-incoterm",
            "hedge-chart", "hedge-target", "hedge-scenario", "hedge-track", "hedge-regulation",
            "scm-leadtime", "scm-compare", "scm-carrier", "scm-latest", "report");

    private static final String SYSTEM_PROMPT = """
            당신은 화면 길잡이다. 사용자의 질문이 화면에 이미 있는 값을 묻는 것이면, 아래 목록에서
            id 하나를 고른다.
            목록: route-kpi, route-map, route-table, route-compare, route-incoterm,
            hedge-chart, hedge-target, hedge-scenario, hedge-track, hedge-regulation,
            scm-leadtime, scm-compare, scm-carrier, scm-latest, report
            목록에 없는 id 를 만들어내지 마라. 마땅한 화면이 없으면 targetId를 null로 두고
            why에 이유를 적어라.
            모든 출력은 한국어로 작성한다(고유명사·코드 제외).
            JSON: {"targetId":"목록의 id 또는 null","why":"그 화면에서 무엇을 볼 수 있는지 한 줄"}""";

    private final Optional<LlmGatewayClient> llmGatewayClient;
    private final ObjectMapper objectMapper;

    public NavBotHandler(Optional<LlmGatewayClient> llmGatewayClient, ObjectMapper objectMapper) {
        this.llmGatewayClient = llmGatewayClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String botId() {
        return "nav";
    }

    @Override
    public ChatResponse handle(ChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw new InvalidChatRequestException("nav bot은 message가 필요합니다");
        }
        if (llmGatewayClient.isEmpty()) {
            throw new ChatUnavailableException("nav bot을 사용하려면 LLM Gateway가 필요합니다");
        }

        String raw = llmGatewayClient.get().complete(SYSTEM_PROMPT, request.message());

        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (JacksonException e) {
            throw new LlmGatewayException("nav bot 응답을 예상된 JSON 형식으로 해석할 수 없습니다", e);
        }

        Object targetIdRaw = parsed.get("targetId");
        String targetId = targetIdRaw == null ? null : String.valueOf(targetIdRaw);
        // 목록 밖 id를 LLM이 지어냈다면 신뢰하지 않고 걷어낸다.
        if (targetId != null && !SCREEN_ANCHORS.contains(targetId)) {
            targetId = null;
        }

        String why = String.valueOf(parsed.getOrDefault("why", ""));
        String answer = targetId != null ? why : "마땅한 화면을 찾지 못했습니다: " + why;

        return new ChatResponse("nav", answer, List.of(), Collections.singletonMap("targetId", targetId));
    }
}

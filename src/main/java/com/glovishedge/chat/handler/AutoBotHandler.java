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
import java.util.Set;

/**
 * 05_프롬프트_전문.md §1 — 라우터. 질문을 analysis(계산 필요)/lookup(화면에 이미 있는 값)/
 * answer(개념·용어) 세 갈래로 분류한다. 모델이 반환 가능한 값은 이 세 개로 제한하고,
 * lookup 인 경우에도 targetId는 08_데이터시드.md §7 화면 앵커 목록 밖 값을 신뢰하지 않는다.
 * analysis 는 결국 risk engine이 필요한데 이 환경에는 없어 "risk engine not configured"로
 * 끝난다(RiskBotHandler와 같은 이유).
 */
@Component
public class AutoBotHandler implements BotHandler {

    private static final Set<String> SCREEN_ANCHORS = Set.of(
            "route-kpi", "route-map", "route-table", "route-compare", "route-incoterm",
            "hedge-chart", "hedge-target", "hedge-scenario", "hedge-track", "hedge-regulation",
            "scm-leadtime", "scm-compare", "scm-carrier", "scm-latest", "report");

    private static final Set<String> VALID_ROUTES = Set.of("analysis", "lookup", "answer");

    private static final String SYSTEM_PROMPT = """
            무엇을 묻든 "파라미터 추출"만 하니, 화면에 이미 있는 값을 물어도 엉뚱한 계산 조건이
            튀어나올 수 있다. 물음의 종류가 다르면 답하는 방식도 달라야 한다.

            (1) analysis — 아직 계산되지 않은 확률·구간·도달가능성을 묻는 경우.
                horizonDays는 영업일로 환산한다(1주=5, 1개월=21, 3개월=63, 6개월=126, 1년=252).
                목표가가 비율이면 targetChangePct, 절대값이면 targetPrice 에 넣는다.

            (2) lookup — 화면에 이미 있는 값을 묻는 경우. 아래 목록에서 id 하나를 고른다.
                목록: route-kpi, route-map, route-table, route-compare, route-incoterm,
                hedge-chart, hedge-target, hedge-scenario, hedge-track, hedge-regulation,
                scm-leadtime, scm-compare, scm-carrier, scm-latest, report
                목록에 없는 id 를 만들어내지 마라. 마땅한 화면이 없으면 answer 로 보낸다.

            (3) answer — 개념·용어·절차 등 일반 질문. 이동도 계산도 필요 없다.

            절대 규칙: 금액·확률·날짜를 지어내지 마라. answer 안에서 구체적 수치를 말하지 말고,
            그런 것을 물으면 lookup 이나 analysis 로 보내라. 모르면 모른다고 쓴다.
            모든 출력은 한국어로 작성한다(고유명사·코드 제외).

            JSON 형식으로만 답한다:
            {"route":"analysis|lookup|answer",
             "reason":"한 줄 판단 근거",
             "analysis":{"asset":"EUA|BUNKER|불명확","targetPrice":숫자 또는 null,
               "targetChangePct":숫자 또는 null,"horizonDays":숫자 또는 null,
               "interpretation":"해석한 문장","missing":["빠진 정보"]} 또는 null,
             "lookup":{"targetId":"목록의 id","why":"그 화면에서 무엇을 볼 수 있는지 한 줄"} 또는 null,
             "answer":{"text":"2~4문장 답변","caveat":"주의할 점 한 줄 또는 null"} 또는 null}

            고른 route 에 해당하는 키만 채우고 나머지 둘은 null 로 둬라.""";

    private final Optional<LlmGatewayClient> llmGatewayClient;
    private final ObjectMapper objectMapper;

    public AutoBotHandler(Optional<LlmGatewayClient> llmGatewayClient, ObjectMapper objectMapper) {
        this.llmGatewayClient = llmGatewayClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String botId() {
        return "auto";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChatResponse handle(ChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw new InvalidChatRequestException("auto bot은 message가 필요합니다");
        }
        if (llmGatewayClient.isEmpty()) {
            throw new ChatUnavailableException("auto bot을 사용하려면 LLM Gateway가 필요합니다");
        }

        String raw = llmGatewayClient.get().complete(SYSTEM_PROMPT, request.message());

        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (JacksonException e) {
            throw new LlmGatewayException("auto bot 응답을 예상된 JSON 형식으로 해석할 수 없습니다", e);
        }

        String route = String.valueOf(parsed.get("route"));
        if (!VALID_ROUTES.contains(route)) {
            // 모델이 임의 route 이름을 만들어내면 신뢰하지 않는다.
            throw new LlmGatewayException("auto bot이 알 수 없는 route를 반환했습니다: " + route);
        }

        return switch (route) {
            case "answer" -> handleAnswer(parsed);
            case "lookup" -> handleLookup(parsed);
            default -> throw new ChatUnavailableException("risk engine not configured");
        };
    }

    @SuppressWarnings("unchecked")
    private ChatResponse handleAnswer(Map<String, Object> parsed) {
        Map<String, Object> answer = (Map<String, Object>) parsed.get("answer");
        String text = answer == null ? "" : String.valueOf(answer.getOrDefault("text", ""));
        return new ChatResponse("auto", text, List.of(), Map.of("route", "answer"));
    }

    @SuppressWarnings("unchecked")
    private ChatResponse handleLookup(Map<String, Object> parsed) {
        Map<String, Object> lookup = (Map<String, Object>) parsed.get("lookup");
        Object targetIdRaw = lookup == null ? null : lookup.get("targetId");
        String targetId = targetIdRaw == null ? null : String.valueOf(targetIdRaw);
        if (targetId != null && !SCREEN_ANCHORS.contains(targetId)) {
            targetId = null;
        }
        String why = lookup == null ? "" : String.valueOf(lookup.getOrDefault("why", ""));
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("route", "lookup");
        data.put("targetId", targetId);
        return new ChatResponse("auto", why, List.of(), data);
    }
}

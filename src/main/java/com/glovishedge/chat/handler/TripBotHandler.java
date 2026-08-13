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
 * 05_프롬프트_전문.md §2 — 조건 파싱. 문장에 없는 항목은 null/missing으로 두고 절대 임의
 * 기본값을 채우지 않는다. 항만/인코텀즈/화물유형은 ctx.options로 받은 목록 밖 값을 만들어내지
 * 않는다(목록 밖 값은 LLM 스스로 missing/unsupported로 표시하게 하고, 여기서는 그 결과를
 * 그대로 data에 싣는다 — 값을 지어내는 건 여기 코드가 아니라 LLM의 책임이므로, 프롬프트에
 * 목록을 반드시 주입한다).
 */
@Component
public class TripBotHandler implements BotHandler {

    private static final String SYSTEM_PROMPT = """
            당신은 해상운송 조회 조건 파서다. 사용자의 한 문장을 읽고 조회 폼에 넣을 값만 뽑는다.
            계산·추천·설명을 하지 마라. 값 추출만 한다.

            반드시 아래 목록에 있는 값 중에서만 고른다. 목록에 없으면 null 로 두고 missing 에 적는다.
            항만·인코텀즈·화물유형을 지어내지 마라 — 없는 항만을 만들어내는 것이 이 기능의 최대 실패다.

            날짜는 오늘 기준으로 해석해 YYYY-MM-DD 로 쓴다. 컨테이너 규격은 20ft / 40ft 중에서 고른다.
            완성차(PCTC)면 대수(units)로 표현될 수 있다.
            모든 출력은 한국어로 한다(코드값 제외).
            JSON: {"fromCountry":"국가코드|null","fromPort":"항만코드|null",
             "toCountry":"국가코드|null","toPort":"항만코드|null",
             "cargoType":"container|pctc|null","unit":"단위값|null","incoterm":"코드|null",
             "deadline":"YYYY-MM-DD|null","interpretation":"해석한 문장 한 줄",
             "missing":["못 뽑은 항목"],"unsupported":["목록에 없어서 못 쓴 값"]}

            문장에 없는 항목은 null 로 두고 missing 에 적어라. 기본값으로 채우지 마라.""";

    private final Optional<LlmGatewayClient> llmGatewayClient;
    private final ObjectMapper objectMapper;

    public TripBotHandler(Optional<LlmGatewayClient> llmGatewayClient, ObjectMapper objectMapper) {
        this.llmGatewayClient = llmGatewayClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String botId() {
        return "trip";
    }

    @Override
    public ChatResponse handle(ChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw new InvalidChatRequestException("trip bot은 message가 필요합니다");
        }
        if (llmGatewayClient.isEmpty()) {
            throw new ChatUnavailableException("trip bot을 사용하려면 LLM Gateway가 필요합니다");
        }

        String userMessage = buildUserMessage(request);
        String raw = llmGatewayClient.get().complete(SYSTEM_PROMPT, userMessage);

        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (JacksonException e) {
            throw new LlmGatewayException("trip bot 응답을 예상된 JSON 형식으로 해석할 수 없습니다", e);
        }

        String interpretation = String.valueOf(parsed.getOrDefault("interpretation", ""));
        return new ChatResponse("trip", interpretation, List.of(), parsed);
    }

    private String buildUserMessage(ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.message());
        if (request.ctx() != null && request.ctx().options() != null) {
            sb.append("\n\n[고를 수 있는 값 목록]\n").append(request.ctx().options());
        }
        return sb.toString();
    }
}

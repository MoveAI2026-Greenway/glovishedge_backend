package com.glovishedge.ai.service;

import com.glovishedge.ai.client.LlmGatewayClient;
import com.glovishedge.ai.dto.CompareRoutesContext;
import com.glovishedge.ai.dto.CompareRoutesRequest;
import com.glovishedge.ai.dto.CompareRoutesResponse;
import com.glovishedge.ai.dto.RouteSnapshot;
import com.glovishedge.ai.exception.InvalidCompareRoutesRequestException;
import com.glovishedge.ai.exception.LlmGatewayException;
import com.glovishedge.ai.exception.LlmGatewayNotConfiguredException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 03_API계약.md §4 / DB가이드 §30 — 이 서비스는 항로를 재계산하지 않는다. 요청에 실려 온
 * 스냅샷 숫자만 그대로 LLM에 전달해 설명 텍스트를 받는다.
 *
 * <p>LLM provider/model이 아직 확정되지 않아 {@link LlmGatewayClient} 구현체가 없는 상태에서도
 * 애플리케이션은 정상 기동해야 하므로 {@code Optional}로 주입받는다. 검증(routes 개수 ·
 * status 값 · statusReason 필수)과 sanitize(blocked 항로 totalUsd 제거)는 LLM 유무와 무관하게
 * 항상 수행한다 — "운항 불가 항로는 총비용을 만들지 않는다"는 이 API에서도 지켜야 하는 불변조건이다.
 */
@Service
public class CompareRoutesService {

    private static final Set<String> VALID_STATUSES = Set.of("best", "limited", "blocked");

    // 05_프롬프트_전문.md §0-① + 공통 꼬리 + §5 추가 문장 — 원문 그대로.
    static final String SYSTEM_PROMPT = """
            절대 규칙: 주어진 입력에 없는 수치를 새로 만들어내지 마라. 금액·확률·비율은 입력값을 \
            그대로 인용만 하고, 계산이 필요하면 입력값끼리의 단순 비교(더 크다/작다)까지만 하라. \
            모르는 것은 "제공된 자료로는 알 수 없음"이라고 쓴다.

            모든 출력은 한국어로 작성한다(고유명사·코드 제외).

            '제한적' 또는 '운항 불가' 상태인 항로는 결론(summary)과 단점(cons) 항목에 \
            그 항로의 '상태 사유' 내용을 반드시 근거로 포함해 설명하세요. \
            상태만 말하고 이유를 빼지 마세요.""";

    private final Optional<LlmGatewayClient> llmGatewayClient;
    private final ObjectMapper objectMapper;

    public CompareRoutesService(Optional<LlmGatewayClient> llmGatewayClient, ObjectMapper objectMapper) {
        this.llmGatewayClient = llmGatewayClient;
        this.objectMapper = objectMapper;
    }

    public CompareRoutesResponse compare(CompareRoutesRequest request) {
        validate(request);
        List<RouteSnapshot> sanitized = sanitize(request.routes());

        if (llmGatewayClient.isEmpty()) {
            throw new LlmGatewayNotConfiguredException(
                    "LLM Gateway가 구성되지 않았습니다 — provider/API key가 아직 확정되지 않아 "
                            + "항로 비교 리포트를 생성할 수 없습니다");
        }

        String userMessage = buildUserMessage(sanitized, request.context());
        String raw = llmGatewayClient.get().complete(SYSTEM_PROMPT, userMessage);
        return parseResponse(raw);
    }

    void validate(CompareRoutesRequest request) {
        if (request == null || request.routes() == null || request.routes().size() != 2) {
            throw new InvalidCompareRoutesRequestException("routes는 정확히 2개여야 합니다");
        }
        for (RouteSnapshot route : request.routes()) {
            if (route.key() == null || route.key().isBlank()) {
                throw new InvalidCompareRoutesRequestException("route.key는 필수입니다");
            }
            if (route.status() == null || !VALID_STATUSES.contains(route.status())) {
                throw new InvalidCompareRoutesRequestException(
                        "route.status 값이 올바르지 않습니다: " + route.status());
            }
            if (route.statusReason() == null || route.statusReason().isBlank()) {
                throw new InvalidCompareRoutesRequestException(
                        "route.statusReason은 필수입니다: " + route.key());
            }
        }
    }

    List<RouteSnapshot> sanitize(List<RouteSnapshot> routes) {
        return routes.stream()
                .map(route -> "blocked".equals(route.status()) ? route.withoutTotalUsd() : route)
                .toList();
    }

    String buildUserMessage(List<RouteSnapshot> routes, CompareRoutesContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("routes", routes);
        payload.put("context", context);
        try {
            String json = objectMapper.writeValueAsString(payload);
            return "아래 항로 비교 데이터를 바탕으로 다음 JSON 형식으로만 답하세요: "
                    + "{\"summary\":\"한 문장 결론\",\"pros\":{\"<key>\":[...]},"
                    + "\"cons\":{\"<key>\":[...]},\"recommendation\":\"...\",\"caveats\":[...]}"
                    + "\n\n[데이터]\n" + json;
        } catch (JacksonException e) {
            throw new IllegalStateException("compare-routes 요청 직렬화에 실패했습니다", e);
        }
    }

    CompareRoutesResponse parseResponse(String raw) {
        try {
            return objectMapper.readValue(raw, CompareRoutesResponse.class);
        } catch (JacksonException e) {
            throw new LlmGatewayException("LLM 응답을 예상된 JSON 형식으로 해석할 수 없습니다", e);
        }
    }
}

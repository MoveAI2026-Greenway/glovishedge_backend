package com.glovishedge.ai.service;

import com.glovishedge.ai.client.LlmGatewayClient;
import com.glovishedge.ai.dto.CompareRoutesContext;
import com.glovishedge.ai.dto.CompareRoutesRequest;
import com.glovishedge.ai.dto.CompareRoutesResponse;
import com.glovishedge.ai.dto.RouteSnapshot;
import com.glovishedge.ai.exception.InvalidCompareRoutesRequestException;
import com.glovishedge.ai.exception.LlmGatewayNotConfiguredException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompareRoutesServiceTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    private RouteSnapshot routeA() {
        return new RouteSnapshot("A", "싱가포르 경유", "limited",
                "바브엘만데브는 피하지만 같은 홍해·수에즈 권역이라...",
                new BigDecimal("2916"), new BigDecimal("2840"),
                new BigDecimal("67"), new BigDecimal("9"), 32, true);
    }

    private RouteSnapshot routeB() {
        return new RouteSnapshot("B", "홍해 직항", "blocked",
                "2026년 7월 20일 후티의 대사우디 해상봉쇄 선언으로...",
                new BigDecimal("3085"), new BigDecimal("2950"),
                new BigDecimal("67"), new BigDecimal("68"), 25, true);
    }

    private CompareRoutesRequest requestOf(RouteSnapshot... routes) {
        return new CompareRoutesRequest(List.of(routes),
                new CompareRoutesContext("FOB", "20ft", LocalDate.of(2026, 10, 5)));
    }

    @Test
    void validate_rejectsWhenNotExactlyTwoRoutes() {
        CompareRoutesService service = new CompareRoutesService(Optional.empty(), objectMapper);
        CompareRoutesRequest request = new CompareRoutesRequest(List.of(routeA()), null);

        assertThatThrownBy(() -> service.compare(request))
                .isInstanceOf(InvalidCompareRoutesRequestException.class);
    }

    @Test
    void validate_rejectsMissingStatusReason() {
        CompareRoutesService service = new CompareRoutesService(Optional.empty(), objectMapper);
        RouteSnapshot noReason = new RouteSnapshot("A", "싱가포르 경유", "limited", "  ",
                new BigDecimal("2916"), new BigDecimal("2840"), new BigDecimal("67"),
                new BigDecimal("9"), 32, true);

        assertThatThrownBy(() -> service.compare(requestOf(noReason, routeB())))
                .isInstanceOf(InvalidCompareRoutesRequestException.class);
    }

    @Test
    void validate_rejectsInvalidStatus() {
        CompareRoutesService service = new CompareRoutesService(Optional.empty(), objectMapper);
        RouteSnapshot badStatus = new RouteSnapshot("A", "싱가포르 경유", "recommended", "사유",
                new BigDecimal("2916"), new BigDecimal("2840"), new BigDecimal("67"),
                new BigDecimal("9"), 32, true);

        assertThatThrownBy(() -> service.compare(requestOf(badStatus, routeB())))
                .isInstanceOf(InvalidCompareRoutesRequestException.class);
    }

    @Test
    void sanitize_removesTotalUsdOnlyForBlockedRoutes() {
        CompareRoutesService service = new CompareRoutesService(Optional.empty(), objectMapper);

        List<RouteSnapshot> sanitized = service.sanitize(List.of(routeA(), routeB()));

        RouteSnapshot a = sanitized.stream().filter(r -> r.key().equals("A")).findFirst().orElseThrow();
        RouteSnapshot b = sanitized.stream().filter(r -> r.key().equals("B")).findFirst().orElseThrow();
        assertThat(a.totalUsd()).isEqualByComparingTo("2916");
        assertThat(b.totalUsd()).isNull();
    }

    @Test
    void buildUserMessage_containsStatusReasonAndExcludesBlockedTotalUsd() {
        CompareRoutesService service = new CompareRoutesService(Optional.empty(), objectMapper);

        List<RouteSnapshot> sanitized = service.sanitize(List.of(routeA(), routeB()));
        String message = service.buildUserMessage(sanitized,
                new CompareRoutesContext("FOB", "20ft", LocalDate.of(2026, 10, 5)));

        assertThat(message).contains("2026년 7월 20일 후티의 대사우디 해상봉쇄 선언으로");
        assertThat(message).doesNotContain("3085");
        assertThat(message).contains("2916");
    }

    @Test
    void systemPrompt_carriesNoNumberGenerationAndStatusReasonRules() {
        assertThat(CompareRoutesService.SYSTEM_PROMPT).contains("새로 만들어내지 마라");
        assertThat(CompareRoutesService.SYSTEM_PROMPT).contains("상태만 말하고 이유를 빼지 마세요");
        assertThat(CompareRoutesService.SYSTEM_PROMPT).contains("한국어로 작성한다");
    }

    @Test
    void llmGatewayAbsent_throwsNotConfigured() {
        CompareRoutesService service = new CompareRoutesService(Optional.empty(), objectMapper);

        assertThatThrownBy(() -> service.compare(requestOf(routeA(), routeB())))
                .isInstanceOf(LlmGatewayNotConfiguredException.class);
    }

    @Test
    void llmGatewayPresent_parsesResponse() {
        LlmGatewayClient client = mock(LlmGatewayClient.class);
        when(client.complete(any(), any())).thenReturn(
                "{\"summary\":\"C가 낫다\",\"pros\":{\"A\":[\"싸다\"]},\"cons\":{\"B\":[\"운항 불가\"]},"
                        + "\"recommendation\":\"A\",\"caveats\":[\"참고용\"]}");
        CompareRoutesService service = new CompareRoutesService(Optional.of(client), objectMapper);

        CompareRoutesResponse response = service.compare(requestOf(routeA(), routeB()));

        assertThat(response.summary()).isEqualTo("C가 낫다");
        assertThat(response.recommendation()).isEqualTo("A");
    }
}

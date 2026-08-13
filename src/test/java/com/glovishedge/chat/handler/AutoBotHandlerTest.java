package com.glovishedge.chat.handler;

import com.glovishedge.ai.client.LlmGatewayClient;
import com.glovishedge.ai.exception.LlmGatewayException;
import com.glovishedge.chat.dto.ChatRequest;
import com.glovishedge.chat.dto.ChatResponse;
import com.glovishedge.chat.exception.ChatUnavailableException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutoBotHandlerTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void answerRoute_returnsAnswerText() {
        LlmGatewayClient llm = mock(LlmGatewayClient.class);
        when(llm.complete(any(), any())).thenReturn(
                "{\"route\":\"answer\",\"reason\":\"개념 질문\",\"analysis\":null,\"lookup\":null,"
                        + "\"answer\":{\"text\":\"EU ETS는 배출권거래제입니다.\",\"caveat\":null}}");
        AutoBotHandler handler = new AutoBotHandler(Optional.of(llm), objectMapper);

        ChatResponse response = handler.handle(new ChatRequest("auto", "EU ETS가 뭐야?", null));

        assertThat(response.answer()).contains("배출권거래제");
    }

    @Test
    void lookupRoute_withInventedAnchor_isRejectedToNull() {
        LlmGatewayClient llm = mock(LlmGatewayClient.class);
        when(llm.complete(any(), any())).thenReturn(
                "{\"route\":\"lookup\",\"reason\":\"화면 값\",\"analysis\":null,"
                        + "\"lookup\":{\"targetId\":\"not-a-real-anchor\",\"why\":\"...\"},\"answer\":null}");
        AutoBotHandler handler = new AutoBotHandler(Optional.of(llm), objectMapper);

        ChatResponse response = handler.handle(new ChatRequest("auto", "싱가포르 얼마야?", null));

        assertThat(response.data().get("targetId")).isNull();
    }

    @Test
    void analysisRoute_throwsRiskEngineNotConfigured() {
        LlmGatewayClient llm = mock(LlmGatewayClient.class);
        when(llm.complete(any(), any())).thenReturn(
                "{\"route\":\"analysis\",\"reason\":\"확률 질문\","
                        + "\"analysis\":{\"asset\":\"EUA\",\"targetPrice\":70,\"targetChangePct\":null,"
                        + "\"horizonDays\":63,\"interpretation\":\"3개월 안에 70 아래\",\"missing\":[]},"
                        + "\"lookup\":null,\"answer\":null}");
        AutoBotHandler handler = new AutoBotHandler(Optional.of(llm), objectMapper);

        assertThatThrownBy(() -> handler.handle(new ChatRequest("auto", "EUA 70 아래로 갈 확률?", null)))
                .isInstanceOf(ChatUnavailableException.class);
    }

    @Test
    void unknownRoute_isRejected() {
        LlmGatewayClient llm = mock(LlmGatewayClient.class);
        when(llm.complete(any(), any())).thenReturn(
                "{\"route\":\"made-up-route\",\"reason\":\"x\",\"analysis\":null,\"lookup\":null,\"answer\":null}");
        AutoBotHandler handler = new AutoBotHandler(Optional.of(llm), objectMapper);

        assertThatThrownBy(() -> handler.handle(new ChatRequest("auto", "질문", null)))
                .isInstanceOf(LlmGatewayException.class);
    }
}

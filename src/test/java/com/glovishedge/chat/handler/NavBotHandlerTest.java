package com.glovishedge.chat.handler;

import com.glovishedge.ai.client.LlmGatewayClient;
import com.glovishedge.chat.dto.ChatRequest;
import com.glovishedge.chat.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NavBotHandlerTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void validAnchor_isPassedThrough() {
        LlmGatewayClient llm = mock(LlmGatewayClient.class);
        when(llm.complete(any(), any()))
                .thenReturn("{\"targetId\":\"route-table\",\"why\":\"항로별 총비용 비교표\"}");
        NavBotHandler handler = new NavBotHandler(Optional.of(llm), objectMapper);

        ChatResponse response = handler.handle(new ChatRequest("nav", "싱가포르 경유 얼마야?", null));

        assertThat(response.data().get("targetId")).isEqualTo("route-table");
    }

    @Test
    void modelInventedAnchor_isRejectedToNull() {
        LlmGatewayClient llm = mock(LlmGatewayClient.class);
        when(llm.complete(any(), any()))
                .thenReturn("{\"targetId\":\"made-up-screen\",\"why\":\"...\"}");
        NavBotHandler handler = new NavBotHandler(Optional.of(llm), objectMapper);

        ChatResponse response = handler.handle(new ChatRequest("nav", "이상한 질문", null));

        assertThat(response.data().get("targetId")).isNull();
        assertThat(response.data()).containsKey("targetId");
    }
}

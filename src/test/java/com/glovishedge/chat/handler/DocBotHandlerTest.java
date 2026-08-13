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

class DocBotHandlerTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void parsesMonthlyReportJson() {
        LlmGatewayClient llm = mock(LlmGatewayClient.class);
        when(llm.complete(any(), any())).thenReturn(
                "{\"title\":\"월간 리스크 보고서\",\"period\":\"2026-07\",\"executiveSummary\":\"요약입니다.\","
                        + "\"sections\":[]}");
        DocBotHandler handler = new DocBotHandler(Optional.of(llm), objectMapper);

        ChatResponse response = handler.handle(new ChatRequest("doc", "지난달 리스크 지표: ...", null));

        assertThat(response.answer()).isEqualTo("요약입니다.");
        assertThat(response.data().get("title")).isEqualTo("월간 리스크 보고서");
    }
}

package com.glovishedge.chat.handler;

import com.glovishedge.ai.client.LlmGatewayClient;
import com.glovishedge.chat.dto.ChatContext;
import com.glovishedge.chat.dto.ChatHistoryTurn;
import com.glovishedge.chat.dto.ChatRequest;
import com.glovishedge.chat.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TermBotHandlerTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void doesNotForwardConversationHistory() {
        LlmGatewayClient llm = mock(LlmGatewayClient.class);
        when(llm.complete(any(), anyString())).thenReturn(
                "{\"incotermDetected\":\"FOB\",\"costAllocation\":{\"seller\":[],\"buyer\":[]},"
                        + "\"carbonCostBearer\":\"구매자\",\"risks\":[],\"notes\":[\"법률 자문 아님\"]}");
        TermBotHandler handler = new TermBotHandler(Optional.of(llm), objectMapper);

        ChatContext ctxWithHistory = new ChatContext(Map.of(), Map.of(), "FOB",
                List.of(new ChatHistoryTurn("이전 질문", "이전 답변")));
        ChatResponse response = handler.handle(new ChatRequest("term", "이 조항의 인코텀즈는?", ctxWithHistory));

        // 사용자 메시지는 조항 원문 그대로만 전달되고, 이전 대화가 섞이지 않는다.
        verify(llm).complete(any(), eq("이 조항의 인코텀즈는?"));
        assertThat(response.answer()).contains("FOB");
    }
}

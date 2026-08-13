package com.glovishedge.chat.handler;

import com.glovishedge.ai.client.LlmGatewayClient;
import com.glovishedge.chat.dto.ChatRequest;
import com.glovishedge.chat.dto.ChatResponse;
import com.glovishedge.chat.exception.ChatUnavailableException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TripBotHandlerTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void missingFields_stayNull_notDefaulted() {
        LlmGatewayClient llm = mock(LlmGatewayClient.class);
        when(llm.complete(any(), any())).thenReturn(
                "{\"fromCountry\":\"KR\",\"fromPort\":\"KRPUS\",\"toCountry\":null,\"toPort\":null,"
                        + "\"cargoType\":\"container\",\"unit\":\"20ft\",\"incoterm\":null,\"deadline\":null,"
                        + "\"interpretation\":\"부산에서 20피트\",\"missing\":[\"toCountry\",\"deadline\"],"
                        + "\"unsupported\":[]}");
        TripBotHandler handler = new TripBotHandler(Optional.of(llm), objectMapper);

        ChatResponse response = handler.handle(new ChatRequest("trip", "부산에서 20피트로", null));

        assertThat(response.data().get("toCountry")).isNull();
        assertThat(response.data().get("deadline")).isNull();
        @SuppressWarnings("unchecked")
        List<Object> missing = (List<Object>) response.data().get("missing");
        assertThat(missing).contains("toCountry", "deadline");
    }

    @Test
    void llmGatewayAbsent_throwsUnavailable() {
        TripBotHandler handler = new TripBotHandler(Optional.empty(), objectMapper);

        assertThatThrownBy(() -> handler.handle(new ChatRequest("trip", "부산에서", null)))
                .isInstanceOf(ChatUnavailableException.class);
    }
}

package com.glovishedge.chat.handler;

import com.glovishedge.chat.dto.ChatRequest;
import com.glovishedge.chat.exception.ChatUnavailableException;
import com.glovishedge.risk.engine.RiskAnalysisEngine;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskBotHandlerTest {

    @Test
    void engineNotConfigured_throwsUnavailableWithClearMessage() {
        RiskBotHandler handler = new RiskBotHandler(Optional.<RiskAnalysisEngine>empty());

        assertThatThrownBy(() -> handler.handle(new ChatRequest("risk", "EUA 80 아래로 갈 확률?", null)))
                .isInstanceOf(ChatUnavailableException.class)
                .hasMessageContaining("risk engine not configured");
    }
}

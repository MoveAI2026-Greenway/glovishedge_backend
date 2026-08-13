package com.glovishedge.chat.service;

import com.glovishedge.chat.dto.ChatContext;
import com.glovishedge.chat.dto.ChatHistoryTurn;
import com.glovishedge.chat.dto.ChatRequest;
import com.glovishedge.chat.dto.ChatResponse;
import com.glovishedge.chat.exception.InvalidChatRequestException;
import com.glovishedge.chat.handler.BotHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private static final List<String> BOT_IDS = List.of("auto", "nav", "trip", "risk", "term", "reg", "doc");

    private BotHandler mockHandler(String id) {
        BotHandler handler = mock(BotHandler.class);
        when(handler.botId()).thenReturn(id);
        when(handler.handle(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ChatResponse(id, "ok", List.of(), Map.of()));
        return handler;
    }

    private List<BotHandler> allHandlers() {
        return BOT_IDS.stream().map(this::mockHandler).toList();
    }

    @Test
    void dispatchesToEachOfSevenBots() {
        ChatService service = new ChatService(allHandlers());

        for (String botId : BOT_IDS) {
            ChatResponse response = service.handle(new ChatRequest(botId, "hello", null));
            assertThat(response.bot()).isEqualTo(botId);
        }
    }

    @Test
    void invalidBotId_isRejected() {
        ChatService service = new ChatService(allHandlers());

        assertThatThrownBy(() -> service.handle(new ChatRequest("unknown", "hello", null)))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    @Test
    void missingBotId_isRejected() {
        ChatService service = new ChatService(allHandlers());

        assertThatThrownBy(() -> service.handle(new ChatRequest(null, "hello", null)))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    @Test
    void historyLongerThanFour_isTruncatedToLastFour() {
        BotHandler termHandler = mockHandler("term");
        ChatService service = new ChatService(List.of(termHandler));

        List<ChatHistoryTurn> sixTurns = List.of(
                new ChatHistoryTurn("q1", "a1"), new ChatHistoryTurn("q2", "a2"),
                new ChatHistoryTurn("q3", "a3"), new ChatHistoryTurn("q4", "a4"),
                new ChatHistoryTurn("q5", "a5"), new ChatHistoryTurn("q6", "a6"));
        ChatContext ctx = new ChatContext(Map.of(), Map.of(), "FOB", sixTurns);

        service.handle(new ChatRequest("term", "질문", ctx));

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(termHandler).handle(captor.capture());
        List<ChatHistoryTurn> passed = captor.getValue().ctx().history();
        assertThat(passed).hasSize(4);
        assertThat(passed.get(0).q()).isEqualTo("q3");
        assertThat(passed.get(3).q()).isEqualTo("q6");
    }
}

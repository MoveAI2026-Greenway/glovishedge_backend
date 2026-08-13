package com.glovishedge.chat.handler;

import com.glovishedge.chat.dto.ChatRequest;
import com.glovishedge.chat.dto.ChatResponse;
import com.glovishedge.chat.exception.ChatUnavailableException;
import com.glovishedge.rag.dto.Citation;
import com.glovishedge.rag.dto.RagAskResponse;
import com.glovishedge.rag.dto.RagAskResult;
import com.glovishedge.rag.exception.RagUnavailableException;
import com.glovishedge.rag.service.RagService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegBotHandlerTest {

    @Test
    void delegatesToRagServiceAndPassesThroughCitations() {
        RagService ragService = mock(RagService.class);
        Citation citation = new Citation("1", "MRV 해운", "Regulation (EU) 2015/757, Article 6", 0.9, "모니터링 계획...");
        when(ragService.ask(any())).thenReturn(new RagAskResponse(
                "monitoring plan deadline", List.of(citation),
                new RagAskResult("답변 [1]", List.of(1), "법률 자문이 아닙니다")));

        RegBotHandler handler = new RegBotHandler(ragService);
        ChatResponse response = handler.handle(new ChatRequest("reg", "모니터링 계획 언제까지?", null));

        assertThat(response.bot()).isEqualTo("reg");
        assertThat(response.citations()).containsExactly(citation);
        assertThat(response.answer()).isEqualTo("답변 [1]");
    }

    @Test
    void ragUnavailable_isMappedToChatUnavailable() {
        RagService ragService = mock(RagService.class);
        when(ragService.ask(any())).thenThrow(new RagUnavailableException("RAG 미구성"));

        RegBotHandler handler = new RegBotHandler(ragService);

        assertThatThrownBy(() -> handler.handle(new ChatRequest("reg", "질문", null)))
                .isInstanceOf(ChatUnavailableException.class);
    }
}

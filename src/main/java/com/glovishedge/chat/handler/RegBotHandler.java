package com.glovishedge.chat.handler;

import com.glovishedge.chat.dto.ChatRequest;
import com.glovishedge.chat.dto.ChatResponse;
import com.glovishedge.chat.exception.ChatUnavailableException;
import com.glovishedge.chat.exception.InvalidChatRequestException;
import com.glovishedge.rag.dto.RagAskRequest;
import com.glovishedge.rag.dto.RagAskResponse;
import com.glovishedge.rag.exception.RagUnavailableException;
import com.glovishedge.rag.service.RagService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * reg bot은 직접 LLM 일반지식으로 답하지 않는다 — 반드시 {@link RagService}(색인 검색 +
 * citation 전용 답변)를 통해서만 답한다. RAG가 비활성/미구성이면 명확한 503을 낸다.
 */
@Component
public class RegBotHandler implements BotHandler {

    private final RagService ragService;

    public RegBotHandler(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public String botId() {
        return "reg";
    }

    @Override
    public ChatResponse handle(ChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw new InvalidChatRequestException("reg bot은 message(질문)가 필요합니다");
        }

        RagAskResponse ragResponse;
        try {
            ragResponse = ragService.ask(new RagAskRequest(request.message(), null));
        } catch (RagUnavailableException e) {
            throw new ChatUnavailableException(e.getMessage());
        }

        return new ChatResponse("reg", ragResponse.result().answer(), ragResponse.hits(),
                Map.of("queryEn", ragResponse.queryEn(), "used", ragResponse.result().used(),
                        "caveat", ragResponse.result().caveat()));
    }
}

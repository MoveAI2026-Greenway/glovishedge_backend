package com.glovishedge.chat.service;

import com.glovishedge.chat.dto.ChatContext;
import com.glovishedge.chat.dto.ChatRequest;
import com.glovishedge.chat.dto.ChatResponse;
import com.glovishedge.chat.exception.InvalidChatRequestException;
import com.glovishedge.chat.handler.BotHandler;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 03_API계약.md §5 — 단일 진입점. botId로 알맞은 {@link BotHandler}를 찾아 위임한다.
 * 직전 4턴만 넘긴다는 규칙은 여기서 한 번만 적용해 모든 bot이 같은 규칙을 따르게 한다.
 */
@Service
public class ChatService {

    private static final int MAX_HISTORY_TURNS = 4;

    private final Map<String, BotHandler> handlersById;

    public ChatService(List<BotHandler> handlers) {
        this.handlersById = handlers.stream()
                .collect(Collectors.toMap(BotHandler::botId, Function.identity()));
    }

    public ChatResponse handle(ChatRequest request) {
        if (request == null || request.botId() == null) {
            throw new InvalidChatRequestException("botId는 필수입니다");
        }
        BotHandler handler = handlersById.get(request.botId());
        if (handler == null) {
            throw new InvalidChatRequestException("알 수 없는 botId입니다: " + request.botId());
        }

        ChatRequest truncated = truncateHistory(request);
        return handler.handle(truncated);
    }

    private ChatRequest truncateHistory(ChatRequest request) {
        if (request.ctx() == null || request.ctx().history() == null
                || request.ctx().history().size() <= MAX_HISTORY_TURNS) {
            return request;
        }
        ChatContext ctx = request.ctx();
        List<com.glovishedge.chat.dto.ChatHistoryTurn> lastFour = ctx.history()
                .subList(ctx.history().size() - MAX_HISTORY_TURNS, ctx.history().size());
        ChatContext truncatedCtx = new ChatContext(ctx.defaults(), ctx.options(), ctx.incotermCode(), lastFour);
        return new ChatRequest(request.botId(), request.message(), truncatedCtx);
    }
}

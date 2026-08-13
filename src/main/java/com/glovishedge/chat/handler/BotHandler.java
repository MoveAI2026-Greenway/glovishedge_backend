package com.glovishedge.chat.handler;

import com.glovishedge.chat.dto.ChatRequest;
import com.glovishedge.chat.dto.ChatResponse;

/**
 * bot 하나의 처리 경계. ChatService는 botId로 알맞은 구현체를 찾아 위임만 한다 — 하나의
 * 거대한 switch에 7개 bot의 비즈니스 로직을 몰아넣지 않는다.
 */
public interface BotHandler {

    String botId();

    ChatResponse handle(ChatRequest request);
}

package com.glovishedge.chat.exception;

/**
 * LLM Gateway 미구성, RAG 미구성, risk engine 미구성 등으로 해당 bot이 이 환경에서 응답을
 * 만들 수 없는 경우. 가짜 답변을 만드는 대신 명확히 503으로 실패시킨다.
 */
public class ChatUnavailableException extends RuntimeException {

    public ChatUnavailableException(String message) {
        super(message);
    }
}

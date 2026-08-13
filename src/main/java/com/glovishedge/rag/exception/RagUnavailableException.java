package com.glovishedge.rag.exception;

/**
 * RAG가 이 환경에서 비활성화/미구성(pgvector 없음, corpus 없음, embedding/LLM gateway 없음)됨.
 * 근거 없는 규정 답변을 만드는 대신 명확히 503으로 실패시킨다.
 */
public class RagUnavailableException extends RuntimeException {

    public RagUnavailableException(String message) {
        super(message);
    }
}

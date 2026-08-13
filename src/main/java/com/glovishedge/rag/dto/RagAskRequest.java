package com.glovishedge.rag.dto;

/**
 * POST /api/rag/ask 요청 — 03_API계약.md §6. {@code k}가 없으면 PROVISIONAL 기본값 4를 쓴다
 * (명세 예시의 값과 동일).
 */
public record RagAskRequest(String question, Integer k) {
}

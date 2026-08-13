package com.glovishedge.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * PROVISIONAL(docs/PROVISIONAL_BACKEND_DECISIONS.md §C) — 실제 gateway 계약이 명세에 없어
 * 이 adapter가 임시로 정의한 최소 응답 형태. {@code error}가 있으면 HTTP status와 무관하게 실패로
 * 취급한다(03_API계약.md §5 "실제로 겪은 함정" — HTTP 200 본문 오류).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmGatewayResponse(String content, String error) {
}

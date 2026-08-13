package com.glovishedge.document.dto;

/**
 * POST /api/extract-text 응답. 03_API계약.md §7에는 리터럴 JSON 스키마가 없어
 * 추출된 텍스트를 담는 최소 필드 하나만 둔다 — 필드명 {@code text}는 [제안]이며 확정된 계약이 아니다.
 */
public record ExtractTextResponse(String text) {
}

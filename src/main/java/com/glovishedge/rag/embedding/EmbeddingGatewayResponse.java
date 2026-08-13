package com.glovishedge.rag.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * PROVISIONAL — 실제 embedding gateway 계약이 명세에 없어 이 adapter가 임시로 정의한 응답 형태.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmbeddingGatewayResponse(List<Float> embedding, String error) {
}

package com.glovishedge.rag.dto;

import java.util.List;

/**
 * POST /api/rag/ask 응답 — 03_API계약.md §6 그대로.
 */
public record RagAskResponse(String queryEn, List<Citation> hits, RagAskResult result) {
}

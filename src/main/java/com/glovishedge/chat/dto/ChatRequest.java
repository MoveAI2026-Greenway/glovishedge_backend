package com.glovishedge.chat.dto;

/**
 * POST /api/chat 요청 — 03_API계약.md §5 그대로.
 */
public record ChatRequest(String botId, String message, ChatContext ctx) {
}

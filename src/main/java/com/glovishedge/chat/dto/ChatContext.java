package com.glovishedge.chat.dto;

import java.util.List;
import java.util.Map;

/**
 * 03_API계약.md §5 ctx 그대로. history는 "직전 4턴만" 넘긴다는 규칙을 Service에서 적용한다.
 */
public record ChatContext(Map<String, Object> defaults, Map<String, Object> options,
                           String incotermCode, List<ChatHistoryTurn> history) {
}

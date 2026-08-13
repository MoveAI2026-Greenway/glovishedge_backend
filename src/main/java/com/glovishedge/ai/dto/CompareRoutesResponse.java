package com.glovishedge.ai.dto;

import java.util.List;
import java.util.Map;

/**
 * 03_API계약.md §4 응답 그대로: {"summary", "pros", "cons", "recommendation", "caveats"}.
 */
public record CompareRoutesResponse(
        String summary,
        Map<String, List<String>> pros,
        Map<String, List<String>> cons,
        String recommendation,
        List<String> caveats
) {
}

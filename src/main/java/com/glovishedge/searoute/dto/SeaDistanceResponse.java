package com.glovishedge.searoute.dto;

import java.util.Map;

/**
 * GET /api/sea-distance 응답 — 03_API계약.md §3: {"routes": {"A": {...}, "B": {...}, ...}}.
 * 계산 실패한 항로는 이 map에서 빠질 수 있다(전부 아니면 전무로 실패시키지 않는다).
 */
public record SeaDistanceResponse(Map<String, RouteResult> routes) {
}

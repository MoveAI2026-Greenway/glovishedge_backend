package com.glovishedge.ai.dto;

import java.math.BigDecimal;

/**
 * POST /api/compare-routes 요청의 routes[] 원소 — 03_API계약.md §4.
 * 프런트가 이미 계산한 스냅샷을 그대로 받는다. 백엔드는 이 값을 재계산하지 않는다.
 */
public record RouteSnapshot(
        String key,
        String name,
        String status,
        String statusReason,
        BigDecimal totalUsd,
        BigDecimal baseFreightUsd,
        BigDecimal carbonCostUsd,
        BigDecimal warRiskUsd,
        Integer leadTimeDays,
        Boolean meetsDeadline
) {

    /**
     * blocked 항로의 totalUsd를 지운 사본을 만든다 — 운항 불가 항로는 총비용을 노출하지 않는다.
     */
    public RouteSnapshot withoutTotalUsd() {
        return new RouteSnapshot(key, name, status, statusReason, null,
                baseFreightUsd, carbonCostUsd, warRiskUsd, leadTimeDays, meetsDeadline);
    }
}

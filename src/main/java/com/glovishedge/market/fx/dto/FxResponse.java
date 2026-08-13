package com.glovishedge.market.fx.dto;

import java.math.BigDecimal;

/**
 * GET /api/fx 응답 — 03_API계약.md §2 필드 그대로.
 */
public record FxResponse(
        BigDecimal usdPerEur,
        BigDecimal krwPerEur,
        String asOf,
        String source,
        String attribution,
        String attributionUrl
) {
}

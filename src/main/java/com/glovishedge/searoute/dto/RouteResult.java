package com.glovishedge.searoute.dto;

import java.util.List;

/**
 * 03_API계약.md §3 — routes.{key} 원소. nm과 path는 항상 같은 searoute-ts 호출 결과에서 나온다.
 */
public record RouteResult(Double nm, List<List<Double>> path) {
}

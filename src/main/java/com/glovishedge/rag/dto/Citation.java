package com.glovishedge.rag.dto;

/**
 * 03_API계약.md §6 hits[] 원소. id/doc/cite/excerpt는 실제 retrieved chunk에서만 가져온다 —
 * LLM이 citation을 새로 만들 수 없다.
 */
public record Citation(String id, String doc, String cite, double score, String excerpt) {
}

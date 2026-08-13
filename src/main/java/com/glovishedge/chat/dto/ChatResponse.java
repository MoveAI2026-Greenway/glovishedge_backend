package com.glovishedge.chat.dto;

import com.glovishedge.rag.dto.Citation;

import java.util.List;
import java.util.Map;

/**
 * PROVISIONAL CONTRACT (docs/PROVISIONAL_BACKEND_DECISIONS.md §D) — 03_API계약.md §5에는
 * request 계약만 있고 response 스키마 예시가 없어, 사용자 승인으로 이 최소 구조를 임시
 * 채택했다. 실제 계약이 확정되면 이 record와 ChatController만 바꾸면 된다(Service/Handler
 * 내부 로직은 영향받지 않도록 설계했다).
 *
 * <ul>
 *   <li>{@code bot} — 실제 처리한 bot (auto가 다른 bot에 위임한 경우 위임받은 bot id)</li>
 *   <li>{@code answer} — 사용자에게 보여줄 답변 텍스트</li>
 *   <li>{@code citations} — reg 등 출처가 있을 때만 채움. 없으면 빈 리스트</li>
 *   <li>{@code data} — deterministic 계산 결과 등 UI가 별도 렌더링할 구조화 데이터. 없으면 빈 맵</li>
 * </ul>
 *
 * <p>오류는 이 응답 바디가 아니라 HTTP status + 예외 처리로만 전달한다.
 */
public record ChatResponse(String bot, String answer, List<Citation> citations, Map<String, Object> data) {
}

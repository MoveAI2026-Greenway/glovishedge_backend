package com.glovishedge.risk.engine;

/**
 * PROVISIONAL — 이 인터페이스는 호출 경계만 정의하고 구현체는 없다.
 *
 * <p>02_계산명세.md §9는 EWMA 변동성 · T영업일 가격구간 · 첫 통과 확률(BGK 이산감시 보정 포함)
 * 공식을 완전히 명시하지만, 이 계산은 **최근 260 거래일 EUA 종가 시계열**을 입력으로 요구한다
 * (08_데이터시드.md §8). 그 시계열은 문서에 참조 통계값(일간 변동성 0.018533 등)만 있을 뿐
 * 원본 종가 260개가 이 저장소 어디에도 없고, eua_prices 테이블도 실시간 fetch로만 채워져
 * 아직 그만큼 쌓이지 않았다. 게다가 DB_IMPLEMENTATION_GUIDE.md §16/§17이 "EWMA/Historical
 * Empirical(k=50)"의 정확한 산출 방식 자체를 팀 {@code [결정 필요]}로 남겨두고 있다.
 *
 * <p>즉 공식은 알아도 입력 데이터가 없고, 최신 방법론(Historical Empirical)의 정확한 절차도
 * 확정돼 있지 않다 — 검증 불가능한 상태로 구현하면 틀린 확률을 만들게 된다. 그래서 risk bot은
 * "risk engine not configured"로 명확히 실패시킨다(docs/PROVISIONAL_BACKEND_DECISIONS.md 참조).
 */
public interface RiskAnalysisEngine {

    /** 목표가 도달확률 · 가격구간 등. 반환 타입은 실제 구현 시 명세와 맞춰 확정한다. */
    Object analyze(String asset, Double targetPrice, Double targetChangePct, int horizonBusinessDays);
}

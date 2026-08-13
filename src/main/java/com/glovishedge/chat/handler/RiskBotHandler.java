package com.glovishedge.chat.handler;

import com.glovishedge.chat.dto.ChatRequest;
import com.glovishedge.chat.dto.ChatResponse;
import com.glovishedge.chat.exception.ChatUnavailableException;
import com.glovishedge.chat.exception.InvalidChatRequestException;
import com.glovishedge.risk.engine.RiskAnalysisEngine;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * risk bot의 역할 경계는 확정돼 있다 — LLM은 파라미터 추출만, 숫자는 deterministic/statistical
 * engine이 계산한다. 다만 {@link RiskAnalysisEngine} 구현체가 없다(이유는 그 인터페이스의
 * JavaDoc 참고 — 260 거래일 종가 시계열 부재 + Historical Empirical(k=50) 절차 미확정).
 * 그래서 LLM 파라미터 추출 호출조차 하지 않고 즉시 "risk engine not configured"로 실패시킨다 —
 * 계산기 없이 파라미터만 뽑아봐야 쓸 곳이 없고, 가짜 확률을 만들 위험만 늘어난다.
 */
@Component
public class RiskBotHandler implements BotHandler {

    private final Optional<RiskAnalysisEngine> riskAnalysisEngine;

    public RiskBotHandler(Optional<RiskAnalysisEngine> riskAnalysisEngine) {
        this.riskAnalysisEngine = riskAnalysisEngine;
    }

    @Override
    public String botId() {
        return "risk";
    }

    @Override
    public ChatResponse handle(ChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw new InvalidChatRequestException("risk bot은 message가 필요합니다");
        }
        if (riskAnalysisEngine.isEmpty()) {
            throw new ChatUnavailableException("risk engine not configured");
        }
        // 실제 엔진이 붙으면: LLM으로 파라미터만 추출 → riskAnalysisEngine.analyze(...) 호출 →
        // 그 결과를 response.data에 그대로 싣는다(LLM이 숫자를 다시 만들지 않는다).
        throw new ChatUnavailableException("risk engine not configured");
    }
}

package com.glovishedge.rag.retriever;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * PROVISIONAL(docs/PROVISIONAL_BACKEND_DECISIONS.md §16) — 03_API계약.md §6은 vector 0.6 +
 * keyword 0.4 가중치만 확정하고, 두 원점수의 스케일을 어떻게 맞출지는 정의하지 않는다.
 * 여기서는 후보 집합(같은 질의의 검색 결과) 내에서 각 신호를 min-max로 [0,1] 정규화한 뒤
 * 가중합한다 — 정규화 로직을 이 컴포넌트 하나로 격리해, 알고리즘이 바뀌어도 여기만 고치면 된다.
 */
@Component
public class RagScoreNormalizer {

    private static final double VECTOR_WEIGHT = 0.6;
    private static final double KEYWORD_WEIGHT = 0.4;

    public List<Double> combine(List<ScoredChunk> chunks) {
        List<Double> vectorNorm = minMaxNormalize(chunks.stream().map(ScoredChunk::vectorScore).toList());
        List<Double> keywordNorm = minMaxNormalize(chunks.stream().map(ScoredChunk::keywordScore).toList());

        List<Double> combined = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            combined.add(vectorNorm.get(i) * VECTOR_WEIGHT + keywordNorm.get(i) * KEYWORD_WEIGHT);
        }
        return combined;
    }

    List<Double> minMaxNormalize(List<Double> raw) {
        if (raw.isEmpty()) {
            return List.of();
        }
        double min = raw.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = raw.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (max - min < 1e-12) {
            // 전부 동일한 원점수 — 변별력이 없으므로 중립값 1.0으로 둔다(전부 동등하게 반영).
            return raw.stream().map(v -> 1.0).toList();
        }
        return raw.stream().map(v -> (v - min) / (max - min)).toList();
    }
}

package com.glovishedge.rag.retriever;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagScoreNormalizerTest {

    private final RagScoreNormalizer normalizer = new RagScoreNormalizer();

    @Test
    void combine_appliesVector06Keyword04Weights() {
        // vector: [0, 1] -> normalized [0, 1]; keyword: [0, 1] -> normalized [0, 1]
        ScoredChunk low = new ScoredChunk("k1", "doc", "cite", "title", "body", 0.0, 0.0);
        ScoredChunk high = new ScoredChunk("k2", "doc", "cite", "title", "body", 1.0, 1.0);

        List<Double> combined = normalizer.combine(List.of(low, high));

        assertThat(combined.get(0)).isEqualTo(0.0);
        assertThat(combined.get(1)).isEqualTo(1.0);
    }

    @Test
    void minMaxNormalize_allEqualScores_returnsNeutralOne() {
        List<Double> result = normalizer.minMaxNormalize(List.of(0.5, 0.5, 0.5));
        assertThat(result).containsExactly(1.0, 1.0, 1.0);
    }

    @Test
    void minMaxNormalize_emptyList_returnsEmpty() {
        assertThat(normalizer.minMaxNormalize(List.of())).isEmpty();
    }

    @Test
    void minMaxNormalize_scalesToZeroOneRange() {
        List<Double> result = normalizer.minMaxNormalize(List.of(10.0, 20.0, 30.0));
        assertThat(result).containsExactly(0.0, 0.5, 1.0);
    }
}

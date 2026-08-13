package com.glovishedge.rag.retriever;

/**
 * regulation_chunks 조회 결과 1건 + 정규화 전 원점수. vectorScore/keywordScore는 각자 다른
 * 스케일이라 {@link RagScoreNormalizer}를 거쳐야 0.6/0.4 가중합이 의미가 있다.
 */
public record ScoredChunk(
        String chunkKey,
        String doc,
        String cite,
        String title,
        String body,
        double vectorScore,
        double keywordScore
) {
}

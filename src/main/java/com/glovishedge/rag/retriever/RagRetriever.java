package com.glovishedge.rag.retriever;

import java.util.List;

/**
 * 규정 조문 검색 추상화. PROVISIONAL: 현재 구현체는 {@link PgVectorRagRetriever} 하나뿐이고,
 * pgvector/regulation_chunks가 이 환경에 없어 {@code RAG_ENABLED=true}일 때만 활성화된다.
 */
public interface RagRetriever {

    List<ScoredChunk> search(String queryEn, float[] queryEmbedding, int k);
}

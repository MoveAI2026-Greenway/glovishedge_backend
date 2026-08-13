package com.glovishedge.rag.embedding;

/**
 * 임베딩 provider 추상화 — 도메인 코드가 특정 vendor SDK를 직접 참조하지 않는다.
 * PROVISIONAL(docs/PROVISIONAL_BACKEND_DECISIONS.md §F/§15): 구현체는
 * {@link GatewayEmbeddingClient} 하나뿐이며 {@code EMBEDDING_GATEWAY_URL}이 설정된 경우에만
 * 빈으로 등록된다.
 */
public interface EmbeddingClient {

    /** @return 768차원(또는 설정된 {@code EMBEDDING_DIMENSION}) 벡터. 실패 시 예외 — 가짜 zero vector 반환 금지. */
    float[] embed(String text);

    int dimension();
}

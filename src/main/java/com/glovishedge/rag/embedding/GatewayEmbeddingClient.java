package com.glovishedge.rag.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PROVISIONAL(docs/PROVISIONAL_BACKEND_DECISIONS.md §F/§15) — 환경변수 기반 HTTP embedding
 * gateway adapter. {@code EMBEDDING_GATEWAY_URL}이 설정된 경우에만 빈으로 등록된다.
 * dimension이 설정값(기본 768)과 다르면 저장하지 않고 예외를 던진다 — 가짜 zero vector 금지.
 */
@Component
@ConditionalOnProperty(name = "EMBEDDING_GATEWAY_URL")
public class GatewayEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final String url;
    private final String apiKey;
    private final String model;
    private final int dimension;

    public GatewayEmbeddingClient(RestClient.Builder restClientBuilder,
                                   @Value("${EMBEDDING_GATEWAY_URL}") String url,
                                   @Value("${EMBEDDING_GATEWAY_API_KEY:}") String apiKey,
                                   @Value("${EMBEDDING_MODEL:}") String model,
                                   @Value("${EMBEDDING_DIMENSION:768}") int dimension) {
        this.restClient = restClientBuilder.build();
        this.url = url;
        this.apiKey = apiKey;
        this.model = model;
        this.dimension = dimension;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public float[] embed(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", text);

        EmbeddingGatewayResponse response;
        try {
            response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(EmbeddingGatewayResponse.class);
        } catch (RestClientException e) {
            throw new EmbeddingClientException("Embedding Gateway 호출 실패", e);
        }

        if (response == null) {
            throw new EmbeddingClientException("Embedding Gateway 응답이 비어 있습니다");
        }
        if (response.error() != null) {
            throw new EmbeddingClientException("Embedding Gateway 오류 응답: " + response.error());
        }
        List<Float> embedding = response.embedding();
        if (embedding == null || embedding.isEmpty()) {
            throw new EmbeddingClientException("Embedding Gateway 응답에 embedding이 없습니다");
        }
        if (embedding.size() != dimension) {
            throw new EmbeddingClientException(
                    "Embedding 차원이 예상과 다릅니다: 예상 " + dimension + ", 실제 " + embedding.size());
        }

        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = embedding.get(i);
        }
        return vector;
    }
}

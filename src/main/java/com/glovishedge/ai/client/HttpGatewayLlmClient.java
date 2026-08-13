package com.glovishedge.ai.client;

import com.glovishedge.ai.exception.LlmGatewayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PROVISIONAL(docs/PROVISIONAL_BACKEND_DECISIONS.md §C) — 특정 vendor SDK(OpenAI/Claude/Gemini)를
 * 직접 참조하지 않는 환경변수 기반 HTTP gateway adapter. {@code LLM_GATEWAY_URL}이 설정된
 * 경우에만 이 빈이 등록되므로, 미설정 환경에서는 {@code Optional<LlmGatewayClient>}가 비어
 * 애플리케이션이 정상 기동하고 compare-routes 등은 503을 반환한다.
 *
 * <p>요청/응답 바디 형식은 명세에 없어 이 adapter가 임시로 정의했다 — 실제 provider가 정해지면
 * 이 클래스만 교체하면 된다(domain Service/Controller는 {@link LlmGatewayClient} 인터페이스만 안다).
 */
@Component
@ConditionalOnProperty(name = "LLM_GATEWAY_URL")
public class HttpGatewayLlmClient implements LlmGatewayClient {

    private final RestClient restClient;
    private final String url;
    private final String apiKey;
    private final String model;

    public HttpGatewayLlmClient(RestClient.Builder restClientBuilder,
                                 @Value("${LLM_GATEWAY_URL}") String url,
                                 @Value("${LLM_GATEWAY_API_KEY:}") String apiKey,
                                 @Value("${LLM_MODEL:}") String model) {
        this.restClient = restClientBuilder.build();
        this.url = url;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("system", systemPrompt);
        body.put("message", userMessage);

        LlmGatewayResponse response;
        try {
            response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(LlmGatewayResponse.class);
        } catch (RestClientException e) {
            throw new LlmGatewayException("LLM Gateway 호출 실패", e);
        }

        if (response == null) {
            throw new LlmGatewayException("LLM Gateway 응답이 비어 있습니다");
        }
        // HTTP status만으로 성공을 판단하지 않는다 — 200 본문에 오류가 실릴 수 있다.
        if (response.error() != null) {
            throw new LlmGatewayException("LLM Gateway 오류 응답: " + response.error());
        }
        if (response.content() == null) {
            throw new LlmGatewayException("LLM Gateway 응답에 content가 없습니다");
        }
        return response.content();
    }
}

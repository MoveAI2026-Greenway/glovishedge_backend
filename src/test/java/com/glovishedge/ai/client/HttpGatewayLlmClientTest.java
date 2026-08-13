package com.glovishedge.ai.client;

import com.glovishedge.ai.exception.LlmGatewayException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpGatewayLlmClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startStub(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/complete", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/complete";
    }

    @Test
    void returnsContentOnSuccess() throws IOException {
        String url = startStub(200, "{\"content\":\"안녕하세요\"}");
        HttpGatewayLlmClient client = new HttpGatewayLlmClient(RestClient.builder(), url, "key", "test-model");

        String result = client.complete("system", "user");

        assertThat(result).isEqualTo("안녕하세요");
    }

    @Test
    void httpOk_withBodyError_isTreatedAsFailure() throws IOException {
        // 03_API계약.md §5 — HTTP 200이어도 body에 오류가 실릴 수 있다.
        String url = startStub(200, "{\"error\":\"credit exhausted\"}");
        HttpGatewayLlmClient client = new HttpGatewayLlmClient(RestClient.builder(), url, "key", "test-model");

        assertThatThrownBy(() -> client.complete("system", "user"))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("credit exhausted");
    }

    @Test
    void httpErrorStatus_throwsGatewayException() throws IOException {
        String url = startStub(500, "{\"error\":\"server error\"}");
        HttpGatewayLlmClient client = new HttpGatewayLlmClient(RestClient.builder(), url, "key", "test-model");

        assertThatThrownBy(() -> client.complete("system", "user"))
                .isInstanceOf(LlmGatewayException.class);
    }

    @Test
    void missingContentField_throwsGatewayException() throws IOException {
        String url = startStub(200, "{}");
        HttpGatewayLlmClient client = new HttpGatewayLlmClient(RestClient.builder(), url, "key", "test-model");

        assertThatThrownBy(() -> client.complete("system", "user"))
                .isInstanceOf(LlmGatewayException.class);
    }
}

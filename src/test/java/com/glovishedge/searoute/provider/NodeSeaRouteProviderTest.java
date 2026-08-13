package com.glovishedge.searoute.provider;

import com.glovishedge.searoute.dto.RouteResult;
import com.glovishedge.searoute.exception.SeaRouteProviderException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Node sidecar를 실제로 띄우지 않고, JDK 내장 HttpServer로 {@code POST /route}를 스텁해
 * NodeSeaRouteProvider의 요청/응답 매핑과 실패 처리를 검증한다.
 */
class NodeSeaRouteProviderTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startStub(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/route", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Test
    void parsesNmAndPathFromNodeResponse() throws IOException {
        String baseUrl = startStub(200, "{\"nm\":11259.5,\"path\":[[35.1,129.04],[53.55,10.0]]}");
        NodeSeaRouteProvider provider = new NodeSeaRouteProvider(RestClient.builder(), baseUrl);

        RouteResult result = provider.computeRoute(
                List.of(new double[]{129.04, 35.1}, new double[]{10, 53.55}), List.of());

        assertThat(result.nm()).isEqualTo(11259.5);
        assertThat(result.path()).containsExactly(List.of(35.1, 129.04), List.of(53.55, 10.0));
    }

    @Test
    void nodeErrorStatus_throwsProviderException() throws IOException {
        String baseUrl = startStub(500, "{\"error\":\"boom\"}");
        NodeSeaRouteProvider provider = new NodeSeaRouteProvider(RestClient.builder(), baseUrl);

        assertThatThrownBy(() -> provider.computeRoute(
                List.of(new double[]{129.04, 35.1}, new double[]{10, 53.55}), List.of()))
                .isInstanceOf(SeaRouteProviderException.class);
    }

    @Test
    void nodeUnreachable_throwsProviderException() {
        // 아무도 듣지 않는 포트
        NodeSeaRouteProvider provider = new NodeSeaRouteProvider(RestClient.builder(), "http://localhost:1");

        assertThatThrownBy(() -> provider.computeRoute(
                List.of(new double[]{129.04, 35.1}, new double[]{10, 53.55}), List.of()))
                .isInstanceOf(SeaRouteProviderException.class);
    }
}

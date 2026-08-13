package com.glovishedge.rag.service;

import com.glovishedge.ai.client.LlmGatewayClient;
import com.glovishedge.rag.dto.RagAskRequest;
import com.glovishedge.rag.dto.RagAskResponse;
import com.glovishedge.rag.embedding.EmbeddingClient;
import com.glovishedge.rag.embedding.EmbeddingClientException;
import com.glovishedge.rag.exception.InvalidRagRequestException;
import com.glovishedge.rag.exception.RagUnavailableException;
import com.glovishedge.rag.retriever.RagRetriever;
import com.glovishedge.rag.retriever.RagScoreNormalizer;
import com.glovishedge.rag.retriever.ScoredChunk;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagServiceTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final RagScoreNormalizer normalizer = new RagScoreNormalizer();

    private RagService serviceWith(RagRetriever retriever, EmbeddingClient embeddingClient,
                                    LlmGatewayClient llmGatewayClient) {
        return new RagService(
                Optional.ofNullable(retriever), Optional.ofNullable(embeddingClient),
                Optional.ofNullable(llmGatewayClient), normalizer, objectMapper);
    }

    @Test
    void ragDisabled_noRetriever_throwsUnavailable() {
        RagService service = serviceWith(null, mock(EmbeddingClient.class), mock(LlmGatewayClient.class));

        assertThatThrownBy(() -> service.ask(new RagAskRequest("EU ETS 뭐야?", 4)))
                .isInstanceOf(RagUnavailableException.class);
    }

    @Test
    void ragDisabled_noEmbeddingClient_throwsUnavailable() {
        RagService service = serviceWith(mock(RagRetriever.class), null, mock(LlmGatewayClient.class));

        assertThatThrownBy(() -> service.ask(new RagAskRequest("EU ETS 뭐야?", 4)))
                .isInstanceOf(RagUnavailableException.class);
    }

    @Test
    void ragDisabled_noLlmGateway_throwsUnavailable() {
        RagService service = serviceWith(mock(RagRetriever.class), mock(EmbeddingClient.class), null);

        assertThatThrownBy(() -> service.ask(new RagAskRequest("EU ETS 뭐야?", 4)))
                .isInstanceOf(RagUnavailableException.class);
    }

    @Test
    void blankQuestion_isRejected() {
        RagService service = serviceWith(mock(RagRetriever.class), mock(EmbeddingClient.class), mock(LlmGatewayClient.class));

        assertThatThrownBy(() -> service.ask(new RagAskRequest("  ", 4)))
                .isInstanceOf(InvalidRagRequestException.class);
    }

    @Test
    void emptyCorpus_returnsNoAnswerWithoutCallingLlmForAnswer() {
        RagRetriever retriever = mock(RagRetriever.class);
        when(retriever.search(anyString(), any(), anyInt())).thenReturn(List.of());
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        when(embeddingClient.embed(anyString())).thenReturn(new float[768]);
        LlmGatewayClient llm = mock(LlmGatewayClient.class);
        // 번역 호출만 허용(질의 변환), 답변 생성 호출은 없어야 한다 — corpus가 비어 있으므로.
        when(llm.complete(anyString(), anyString())).thenReturn("EU ETS meaning?");

        RagService service = serviceWith(retriever, embeddingClient, llm);
        RagAskResponse response = service.ask(new RagAskRequest("EU ETS가 뭐야?", 4));

        assertThat(response.hits()).isEmpty();
        assertThat(response.result().answer()).contains("확인되지 않습니다");
        assertThat(response.result().used()).isEmpty();
    }

    @Test
    void embeddingFailure_throwsUnavailable() {
        RagRetriever retriever = mock(RagRetriever.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        when(embeddingClient.embed(anyString())).thenThrow(new EmbeddingClientException("차원 불일치"));
        LlmGatewayClient llm = mock(LlmGatewayClient.class);
        when(llm.complete(anyString(), anyString())).thenReturn("translated query");

        RagService service = serviceWith(retriever, embeddingClient, llm);

        assertThatThrownBy(() -> service.ask(new RagAskRequest("질문", 4)))
                .isInstanceOf(RagUnavailableException.class);
    }

    @Test
    void hitsPresent_buildsCitationsAndSanitizesUsedIndices() {
        RagRetriever retriever = mock(RagRetriever.class);
        ScoredChunk chunk1 = new ScoredChunk("k1", "MRV 해운", "Regulation (EU) 2015/757, Article 6",
                "title1", "모니터링 계획은...", 0.9, 0.8);
        ScoredChunk chunk2 = new ScoredChunk("k2", "EU ETS 지침", "Directive 2003/87/EC, Art.3gb",
                "title2", "배출권 제출 의무자는...", 0.5, 0.4);
        when(retriever.search(anyString(), any(), anyInt())).thenReturn(List.of(chunk1, chunk2));

        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        when(embeddingClient.embed(anyString())).thenReturn(new float[768]);

        LlmGatewayClient llm = mock(LlmGatewayClient.class);
        when(llm.complete(anyString(), anyString()))
                .thenReturn("translated") // 번역 호출
                .thenReturn("{\"answer\":\"모니터링 계획은 [1]에 따라 제출합니다.\",\"used\":[1,99,-1],\"caveat\":\"법률 자문이 아닙니다\"}");

        RagService service = serviceWith(retriever, embeddingClient, llm);
        RagAskResponse response = service.ask(new RagAskRequest("모니터링 계획 언제까지?", 2));

        assertThat(response.hits()).hasSize(2);
        assertThat(response.hits().get(0).id()).isEqualTo("1");
        assertThat(response.hits().get(0).cite()).isEqualTo("Regulation (EU) 2015/757, Article 6");
        // 잘못된 citation 번호(99, -1)는 제거되고 유효한 1만 남는다.
        assertThat(response.result().used()).containsExactly(1);
        assertThat(response.result().caveat()).isEqualTo("법률 자문이 아닙니다");
    }

    @Test
    void translationFailure_fallsBackToOriginalQuestion() {
        RagRetriever retriever = mock(RagRetriever.class);
        when(retriever.search(anyString(), any(), anyInt())).thenReturn(List.of());
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        when(embeddingClient.embed(anyString())).thenReturn(new float[768]);
        LlmGatewayClient llm = mock(LlmGatewayClient.class);
        when(llm.complete(anyString(), anyString())).thenThrow(new RuntimeException("게이트웨이 오류"));

        RagService service = serviceWith(retriever, embeddingClient, llm);
        RagAskResponse response = service.ask(new RagAskRequest("한국어 질문", 4));

        assertThat(response.queryEn()).isEqualTo("한국어 질문");
    }
}

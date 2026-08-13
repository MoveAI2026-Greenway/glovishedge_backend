package com.glovishedge.rag.service;

import com.glovishedge.ai.client.LlmGatewayClient;
import com.glovishedge.ai.exception.LlmGatewayException;
import com.glovishedge.rag.dto.Citation;
import com.glovishedge.rag.dto.RagAskRequest;
import com.glovishedge.rag.dto.RagAskResponse;
import com.glovishedge.rag.dto.RagAskResult;
import com.glovishedge.rag.embedding.EmbeddingClient;
import com.glovishedge.rag.embedding.EmbeddingClientException;
import com.glovishedge.rag.exception.InvalidRagRequestException;
import com.glovishedge.rag.exception.RagUnavailableException;
import com.glovishedge.rag.retriever.RagRetriever;
import com.glovishedge.rag.retriever.RagScoreNormalizer;
import com.glovishedge.rag.retriever.ScoredChunk;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 03_API계약.md §6 / 05_프롬프트_전문.md §3 — 규정 조문 검색 + citation 전용 답변.
 *
 * <p>RAG는 storage(pgvector) · corpus(법령 원문) · embedding provider · LLM provider 넷 다
 * 갖춰져야 성립한다. 이 프로젝트 환경에는 그중 pgvector/corpus/embedding/LLM이 전부 없어
 * ({@link RagRetriever}, {@link EmbeddingClient}, {@link LlmGatewayClient} 모두
 * {@code Optional}로 주입) 실제로는 항상 {@link RagUnavailableException}(503)을 던진다 —
 * 그러나 구조 자체는 실제 인프라가 갖춰지는 즉시 동작하도록 완성돼 있다.
 */
@Service
public class RagService {

    private static final int DEFAULT_K = 4;

    // 05_프롬프트_전문.md §3 원문 그대로.
    private static final String ANSWER_SYSTEM_PROMPT = """
            당신은 EU 해운 규제 질의응답 도우미다. 아래 제공된 조문 발췌만 근거로 답한다.
            발췌에 없는 내용은 지어내지 말고 "제공된 조문에서는 확인되지 않습니다"라고 쓴다.
            answer 안의 각 주장 뒤에 [1] [2] 처럼 근거 번호를 붙인다.
            법률 자문이 아님을 caveat 에 반드시 적는다. 한국어로 답한다.""";

    private static final String TRANSLATE_SYSTEM_PROMPT =
            "한국어 질문을 검색에 쓸 영어 질의문 한 줄로 바꿔라. 다른 설명 없이 영어 문장만 출력하라.";

    private static final String NO_CITATION_ANSWER = "제공된 조문에서는 확인되지 않습니다";
    private static final String LEGAL_CAVEAT = "법률 자문이 아닙니다";
    private static final int EXCERPT_MAX_CHARS = 400;

    private final Optional<RagRetriever> ragRetriever;
    private final Optional<EmbeddingClient> embeddingClient;
    private final Optional<LlmGatewayClient> llmGatewayClient;
    private final RagScoreNormalizer scoreNormalizer;
    private final ObjectMapper objectMapper;

    public RagService(Optional<RagRetriever> ragRetriever,
                       Optional<EmbeddingClient> embeddingClient,
                       Optional<LlmGatewayClient> llmGatewayClient,
                       RagScoreNormalizer scoreNormalizer,
                       ObjectMapper objectMapper) {
        this.ragRetriever = ragRetriever;
        this.embeddingClient = embeddingClient;
        this.llmGatewayClient = llmGatewayClient;
        this.scoreNormalizer = scoreNormalizer;
        this.objectMapper = objectMapper;
    }

    public RagAskResponse ask(RagAskRequest request) {
        validate(request);
        int k = request.k() != null ? request.k() : DEFAULT_K;

        if (ragRetriever.isEmpty() || embeddingClient.isEmpty()) {
            throw new RagUnavailableException(
                    "RAG가 이 환경에서 비활성화되어 있습니다 (pgvector/corpus 미구성)");
        }
        if (llmGatewayClient.isEmpty()) {
            throw new RagUnavailableException(
                    "RAG가 이 환경에서 비활성화되어 있습니다 (LLM Gateway 미구성)");
        }

        String queryEn = translateToEnglish(request.question());

        float[] embedding;
        try {
            embedding = embeddingClient.get().embed(queryEn);
        } catch (EmbeddingClientException e) {
            throw new RagUnavailableException("질의 임베딩 생성에 실패했습니다: " + e.getMessage());
        }

        List<ScoredChunk> chunks = ragRetriever.get().search(queryEn, embedding, k);

        if (chunks.isEmpty()) {
            // 코퍼스에 근거가 없음 — 없는 규정을 LLM이 만들어내게 하지 않는다.
            return new RagAskResponse(queryEn, List.of(),
                    new RagAskResult(NO_CITATION_ANSWER, List.of(), LEGAL_CAVEAT));
        }

        List<Citation> citations = buildCitations(chunks, scoreNormalizer.combine(chunks));

        String userMessage = buildUserMessage(request.question(), citations);
        String raw = llmGatewayClient.get().complete(ANSWER_SYSTEM_PROMPT, userMessage);
        RagAskResult result = parseAndSanitize(raw, citations.size());

        return new RagAskResponse(queryEn, citations, result);
    }

    private void validate(RagAskRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new InvalidRagRequestException("question은 필수입니다");
        }
        if (request.k() != null && request.k() < 1) {
            throw new InvalidRagRequestException("k는 1 이상이어야 합니다");
        }
    }

    private String translateToEnglish(String question) {
        try {
            String translated = llmGatewayClient.get().complete(TRANSLATE_SYSTEM_PROMPT, question);
            return (translated == null || translated.isBlank()) ? question : translated.trim();
        } catch (RuntimeException e) {
            // 05_프롬프트_전문.md §3 — 변환 실패 시 원문으로 그대로 진행한다.
            return question;
        }
    }

    private List<Citation> buildCitations(List<ScoredChunk> chunks, List<Double> hybridScores) {
        record Scored(ScoredChunk chunk, double score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            scored.add(new Scored(chunks.get(i), hybridScores.get(i)));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());

        List<Citation> citations = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            Scored s = scored.get(i);
            citations.add(new Citation(
                    String.valueOf(i + 1), s.chunk().doc(), s.chunk().cite(), s.score(), excerpt(s.chunk().body())));
        }
        return citations;
    }

    private String excerpt(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= EXCERPT_MAX_CHARS ? body : body.substring(0, EXCERPT_MAX_CHARS) + "…";
    }

    private String buildUserMessage(String question, List<Citation> citations) {
        StringBuilder sb = new StringBuilder();
        sb.append(question).append("\n\n[조문 발췌]\n");
        for (Citation c : citations) {
            sb.append('[').append(c.id()).append("] ").append(c.cite()).append('\n')
                    .append(c.excerpt()).append("\n\n");
        }
        sb.append("JSON 형식으로만 답하라: {\"answer\":\"2~5문장\",\"used\":[사용한 근거 번호],\"caveat\":\"한 줄\"}");
        return sb.toString();
    }

    private RagAskResult parseAndSanitize(String raw, int citationCount) {
        RagAskResult parsed;
        try {
            parsed = objectMapper.readValue(raw, RagAskResult.class);
        } catch (JacksonException e) {
            throw new LlmGatewayException("RAG 응답을 예상된 JSON 형식으로 해석할 수 없습니다", e);
        }

        List<Integer> usedRaw = parsed.used() == null ? List.of() : parsed.used();
        // LLM이 citation 번호를 새로 만들어내지 못하게, 실제 제공된 범위 밖 값은 버린다.
        List<Integer> sanitizedUsed = usedRaw.stream()
                .filter(i -> i != null && i >= 1 && i <= citationCount)
                .distinct()
                .toList();

        String caveat = (parsed.caveat() == null || parsed.caveat().isBlank()) ? LEGAL_CAVEAT : parsed.caveat();
        String answer = parsed.answer() == null ? NO_CITATION_ANSWER : parsed.answer();
        return new RagAskResult(answer, sanitizedUsed, caveat);
    }
}

package com.glovishedge.rag.retriever;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * PROVISIONAL(docs/PROVISIONAL_BACKEND_DECISIONS.md §F/§9) — pgvector 기반 하이브리드 검색.
 *
 * <p>이 환경(로컬 Windows PostgreSQL)에는 pgvector extension이 설치돼 있지 않고
 * {@code regulation_chunks} 테이블도 없다(V1~V4에 없음, 이번 세션에서 migration 추가하지 않음).
 * 그래서 이 빈은 {@code RAG_ENABLED=true}일 때만 등록되며, 실제로 이 환경에서는 결코
 * 활성화되지 않는다 — pgvector 설치 + {@code db/migration-optional/}의 참고 migration을
 * 실제 {@code db/migration/}으로 옮긴 뒤에만 의미가 있다.
 *
 * <p>JPA {@code @Entity}로 매핑하지 않고 {@link JdbcTemplate} 원시 쿼리만 쓰는 이유: 존재하지
 * 않는 테이블을 가리키는 Entity가 있으면 Hibernate {@code ddl-auto=validate}가 기동 시점에
 * 이 빈의 활성화 여부와 무관하게 스키마 검증을 시도해 애플리케이션 전체가 죽는다.
 */
@Component
@ConditionalOnProperty(name = "RAG_ENABLED", havingValue = "true")
public class PgVectorRagRetriever implements RagRetriever {

    private final JdbcTemplate jdbcTemplate;
    private final int maxArticleNoPerDoc;

    public PgVectorRagRetriever(JdbcTemplate jdbcTemplate,
                                 @Value("${rag.max-article-no-per-doc:0}") int maxArticleNoPerDoc) {
        this.jdbcTemplate = jdbcTemplate;
        this.maxArticleNoPerDoc = maxArticleNoPerDoc;
    }

    @Override
    public List<ScoredChunk> search(String queryEn, float[] queryEmbedding, int k) {
        String vectorLiteral = toVectorLiteral(queryEmbedding);

        // 06_함정목록.md 함정 12 — 다른 법령 상호참조를 자기 조문으로 잡지 않도록 문서별 최대
        // 조문 번호 상한(rag.max-article-no-per-doc, 0이면 미적용)을 둔다.
        String sql = """
                SELECT chunk_key, doc_short, cite, title, body,
                       1 - (embedding <=> ?::vector) AS vector_score,
                       ts_rank(to_tsvector('english', body), plainto_tsquery('english', ?)) AS keyword_score
                FROM regulation_chunks
                WHERE (? = 0 OR article_no <= ?)
                ORDER BY vector_score DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new ScoredChunk(
                        rs.getString("chunk_key"),
                        rs.getString("doc_short"),
                        rs.getString("cite"),
                        rs.getString("title"),
                        rs.getString("body"),
                        rs.getDouble("vector_score"),
                        rs.getDouble("keyword_score")
                ),
                vectorLiteral, queryEn, maxArticleNoPerDoc, maxArticleNoPerDoc, k);
    }

    private String toVectorLiteral(float[] embedding) {
        return IntStream.range(0, embedding.length)
                .mapToObj(i -> Float.toString(embedding[i]))
                .collect(Collectors.joining(",", "[", "]"));
    }
}

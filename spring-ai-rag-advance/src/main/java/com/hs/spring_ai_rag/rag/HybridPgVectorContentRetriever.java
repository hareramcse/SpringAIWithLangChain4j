package com.hs.spring_ai_rag.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hs.spring_ai_rag.config.AppPgVectorProperties;
import com.hs.spring_ai_rag.config.AppRagProperties;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ReciprocalRankFuser;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dense pgvector search plus full-text search on a STORED {@code tsvector} column; results are merged with RRF.
 */
@Slf4j
@RequiredArgsConstructor
public final class HybridPgVectorContentRetriever implements ContentRetriever {

	private final EmbeddingStore<TextSegment> embeddingStore;
	private final EmbeddingModel embeddingModel;
	private final JdbcClient jdbc;
	private final ObjectMapper objectMapper;
	private final AppPgVectorProperties pg;
	private final AppRagProperties rag;
	private final int retrievalPoolSize;

	@Override
	public List<Content> retrieve(Query query) {
		String q = query.text();
		if (q == null || q.isBlank()) {
			return List.of();
		}
		PgVectorSqlIdentifiers.requireSqlIdentifier("table", pg.table());
		PgVectorSqlIdentifiers.requireSqlIdentifier("tsv-column", pg.tsvColumn());
		PgVectorSqlIdentifiers.requireRegconfig(pg.textSearchConfig());

		Embedding queryEmbedding = embeddingModel.embed(q).content();
		var retrieval = rag.retrieval();
		var vectorRequest = EmbeddingSearchRequest.builder()
				.queryEmbedding(queryEmbedding)
				.maxResults(retrievalPoolSize)
				.minScore(retrieval.minScore())
				.build();

		List<Content> dense = new ArrayList<>();
		for (var match : embeddingStore.search(vectorRequest).matches()) {
			if (match.embedded() != null) {
				dense.add(Content.from(match.embedded()));
			}
		}

		List<Content> keyword = keywordSearch(q, retrievalPoolSize);
		if (keyword.isEmpty()) {
			return dense.stream().limit(retrievalPoolSize).toList();
		}
		if (dense.isEmpty()) {
			return keyword.stream().limit(retrievalPoolSize).toList();
		}
		List<Content> fused = ReciprocalRankFuser.fuse(List.of(dense, keyword), pg.rrfK());
		return fused.stream().limit(retrievalPoolSize).toList();
	}

	private List<Content> keywordSearch(String queryText, int limit) {
		String sql = ftsTopKSql();
		return jdbc.sql(sql)
				.param(queryText)
				.param(queryText)
				.param(limit)
				.query((rs, rowNum) -> {
					String text = rs.getString("text");
					if (text == null || text.isBlank()) {
						return null;
					}
					Metadata meta = readMetadata(rs);
					return Content.from(TextSegment.from(text, meta));
				})
				.list()
				.stream()
				.filter(Objects::nonNull)
				.toList();
	}

	/** Full-text leg of hybrid search using the generated {@code tsvector} column (see {@code PgVectorGeneratedFtsInitializer}). */
	private String ftsTopKSql() {
		return "SELECT text, metadata FROM %s WHERE %s @@ plainto_tsquery('%s', ?) "
				+ "ORDER BY ts_rank_cd(%s, plainto_tsquery('%s', ?)) DESC LIMIT ?"
				.formatted(
						pg.table(),
						pg.tsvColumn(),
						pg.textSearchConfig(),
						pg.tsvColumn(),
						pg.textSearchConfig());
	}

	private Metadata readMetadata(java.sql.ResultSet rs) {
		try {
			String json = rs.getString("metadata");
			if (json == null || json.isBlank()) {
				return Metadata.from(Map.of());
			}
			Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
			return Metadata.from(map);
		} catch (Exception e) {
			log.debug("Could not parse metadata JSON, using empty metadata: {}", e.toString());
			return Metadata.from(Map.of());
		}
	}
}

package com.hs.spring_ai_rag.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hs.spring_ai_rag.config.AppPgVectorProperties;
import com.hs.spring_ai_rag.config.AppRagProperties;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;

/**
 * Hybrid retrieval (dense + FTS merge). Re-ranking runs in the aggregator when enabled.
 */
@RequiredArgsConstructor
public final class HybridPgVectorContentRetriever implements ContentRetriever {

	private static final Pattern SQL_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

	private static final Set<String> ALLOWED_TEXT_SEARCH_CONFIGS = Set.of("simple", "english", "german", "spanish",
			"french", "italian", "portuguese", "dutch", "swedish", "norwegian", "danish", "finnish", "hungarian",
			"russian", "turkish");

	private final EmbeddingStore<TextSegment> embeddingStore;
	private final EmbeddingModel embeddingModel;
	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;
	private final AppPgVectorProperties pg;
	private final AppRagProperties rag;
	/** Cap on merged candidates passed to the aggregator (reranking candidate pool or final top-k when reranking off). */
	private final int maxChunksToRetrieve;

	@Override
	public List<Content> retrieve(Query query) {
		String queryText = query.text();
		if (queryText == null || queryText.isBlank()) {
			return List.of();
		}
		assertSafeDynamicSqlLiterals();

		Embedding questionEmbedding = embeddingModel.embed(queryText).content();
		var retrievalSettings = rag.retrieval();

		int vectorCap = Math.max(0, pg.getHybridVectorMaxResults());
		int keywordCap = Math.max(0, pg.getHybridKeywordMaxResults());

		List<Content> vectorMatches = vectorCap > 0
				? searchVector(questionEmbedding, vectorCap, retrievalSettings.minScore())
				: List.of();
		List<Content> keywordMatches = keywordCap > 0 ? searchKeyword(queryText, keywordCap) : List.of();

		List<Content> merged = mergeDistinctChunkTextPreservingVectorFirst(vectorMatches, keywordMatches);
		return merged.stream().limit(maxChunksToRetrieve).toList();
	}

	private List<Content> searchVector(Embedding questionEmbedding, int limit, double minScore) {
		var embeddingSearchRequest = EmbeddingSearchRequest.builder().queryEmbedding(questionEmbedding).maxResults(limit)
				.minScore(minScore).build();
		List<Content> out = new ArrayList<>();
		for (var match : embeddingStore.search(embeddingSearchRequest).matches()) {
			if (match.embedded() != null) {
				out.add(Content.from(match.embedded()));
			}
		}
		return out;
	}

	private List<Content> searchKeyword(String queryText, int rowLimit) {
		String sql = buildRankedFullTextSearchSql();
		return jdbcTemplate.query(
				sql,
				(rs, rowNum) -> {
					String text = rs.getString("text");
					if (text == null || text.isBlank()) {
						return null;
					}
					Metadata meta = parseMetadataFromRow(rs);
					return Content.from(TextSegment.from(text, meta));
				},
				queryText,
				queryText,
				rowLimit)
				.stream()
				.filter(Objects::nonNull)
				.toList();
	}

	/**
	 * Concatenate vector then keyword hits and drop duplicate chunk bodies (same normalized text keeps first list’s
	 * {@link Content}, typically dense).
	 */
	static List<Content> mergeDistinctChunkTextPreservingVectorFirst(List<Content> vectorMatches,
			List<Content> keywordMatches) {
		LinkedHashMap<String, Content> byNormalizedText = new LinkedHashMap<>();
		for (Content c : vectorMatches) {
			byNormalizedText.putIfAbsent(normalizedChunkText(c), c);
		}
		for (Content c : keywordMatches) {
			byNormalizedText.putIfAbsent(normalizedChunkText(c), c);
		}
		return List.copyOf(byNormalizedText.values());
	}

	private static String normalizedChunkText(Content content) {
		return content.textSegment().text().trim();
	}

	private void assertSafeDynamicSqlLiterals() {
		requireSqlIdentifier("table", pg.getTable());
		requireSqlIdentifier("tsv-column", pg.getTsvColumn());
		requireRegconfig(pg.getTextSearchConfig());
	}

	private static void requireSqlIdentifier(String name, String value) {
		if (value == null || !SQL_IDENTIFIER.matcher(value).matches()) {
			throw new IllegalArgumentException("invalid identifier");
		}
	}

	private static void requireRegconfig(String textSearchConfig) {
		if (textSearchConfig == null || textSearchConfig.isBlank()) {
			throw new IllegalArgumentException("invalid text-search-config");
		}
		String normalized = textSearchConfig.trim().toLowerCase(Locale.ROOT);
		if (!ALLOWED_TEXT_SEARCH_CONFIGS.contains(normalized)) {
			throw new IllegalArgumentException("invalid text-search-config");
		}
	}

	private String buildRankedFullTextSearchSql() {
		String reg = pg.getTextSearchConfig().trim().toLowerCase(Locale.ROOT);
		return "SELECT text, metadata FROM "
				+ pg.getTable()
				+ " WHERE "
				+ pg.getTsvColumn()
				+ " @@ plainto_tsquery('"
				+ reg
				+ "', ?) ORDER BY ts_rank_cd("
				+ pg.getTsvColumn()
				+ ", plainto_tsquery('"
				+ reg
				+ "', ?)) DESC LIMIT ?";
	}

	private Metadata parseMetadataFromRow(java.sql.ResultSet rs) {
		try {
			String json = rs.getString("metadata");
			if (json == null || json.isBlank()) {
				return Metadata.from(Map.of());
			}
			Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
			return Metadata.from(map);
		} catch (Exception e) {
			return Metadata.from(Map.of());
		}
	}
}

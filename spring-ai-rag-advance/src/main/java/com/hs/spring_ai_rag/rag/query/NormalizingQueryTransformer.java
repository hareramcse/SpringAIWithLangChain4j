package com.hs.spring_ai_rag.rag.query;

import java.util.Collection;
import java.util.List;

import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;

/**
 * Entry-stage query transformation: trim and collapse repeated whitespace before hybrid search / re-ranking.
 */
public final class NormalizingQueryTransformer implements QueryTransformer {

	@Override
	public Collection<Query> transform(Query query) {
		String raw = query.text();
		if (raw == null) {
			return List.of(query);
		}
		String normalized = raw.trim().replaceAll("\\s+", " ");
		if (normalized.equals(raw)) {
			return List.of(query);
		}
		return List.of(Query.from(normalized, query.metadata()));
	}
}

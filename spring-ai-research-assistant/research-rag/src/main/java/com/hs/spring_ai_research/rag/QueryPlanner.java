package com.hs.spring_ai_research.rag;

import java.util.ArrayList;
import java.util.List;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * Query Planning: transforms user queries before retrieval.
 *
 * Two key operations:
 * 1. Query Rewrite — clarifies ambiguous queries for better retrieval
 * 2. Query Decompose — breaks complex multi-part questions into sub-queries
 *
 * Why this matters:
 * - Users write vague queries: "how does it work?" -> rewrite to include context
 * - Complex queries miss relevant docs: "compare RAG vs fine-tuning and explain
 *   when to use each" -> decompose into two focused sub-queries
 * - Each sub-query retrieves its own relevant documents
 * - Results are merged to provide a comprehensive answer
 */
@Slf4j
@Service
public class QueryPlanner {

	private final ChatModel fastModel;

	public QueryPlanner(@Qualifier("fastModel") ChatModel fastModel) {
		this.fastModel = fastModel;
	}

	/**
	 * Rewrite a query for better retrieval. Clarifies intent and adds context.
	 */
	public String rewriteQuery(String originalQuery) {
		String prompt = """
				Rewrite the following search query to be more specific and clear for document retrieval.
				Keep the same intent but make it unambiguous. Add relevant technical terms if applicable.
				Return ONLY the rewritten query, nothing else.
				
				Original query: %s""".formatted(originalQuery);

		String rewritten = fastModel.chat(prompt).trim();
		if (rewritten.isEmpty() || rewritten.length() > originalQuery.length() * 3) {
			return originalQuery;
		}

		log.info("Query rewritten: '{}' -> '{}'", originalQuery, rewritten);
		return rewritten;
	}

	/**
	 * Decompose a complex query into simpler sub-queries.
	 * Returns the original plus any sub-queries.
	 */
	public List<String> decomposeQuery(String originalQuery) {
		String prompt = """
				Analyze if this query contains multiple questions or topics.
				If it does, break it into 2-3 simpler sub-queries.
				If it's already simple, return just the original query.
				Return one query per line, no numbering, no extra text.
				
				Query: %s""".formatted(originalQuery);

		String response = fastModel.chat(prompt);
		List<String> subQueries = new ArrayList<>();
		subQueries.add(originalQuery);

		for (String line : response.split("\n")) {
			String trimmed = line.trim();
			if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase(originalQuery)) {
				subQueries.add(trimmed);
			}
		}

		if (subQueries.size() > 1) {
			log.info("Query decomposed into {} sub-queries", subQueries.size());
		}
		return subQueries;
	}

	/**
	 * Full query planning: decide whether to rewrite, decompose, or both.
	 */
	public QueryPlan plan(String originalQuery) {
		boolean isComplex = originalQuery.length() > 100
				|| originalQuery.contains(" and ")
				|| originalQuery.contains(" compare ")
				|| originalQuery.split("\\?").length > 1;

		String rewrittenQuery = rewriteQuery(originalQuery);
		List<String> subQueries;

		if (isComplex) {
			subQueries = decomposeQuery(rewrittenQuery);
		} else {
			subQueries = List.of(rewrittenQuery);
		}

		return new QueryPlan(originalQuery, rewrittenQuery, subQueries, isComplex);
	}

	public record QueryPlan(
			String originalQuery,
			String rewrittenQuery,
			List<String> subQueries,
			boolean wasDecomposed
	) {}
}

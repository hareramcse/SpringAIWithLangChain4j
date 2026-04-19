package com.hs.spring_ai_research.tools;

import java.util.List;
import java.util.Map;


import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Simulates web search results for demonstration purposes.
 *
 * In a production system, this would integrate with a real search API
 * (Google Custom Search, Bing, Tavily, SerpAPI, etc.).
 * The tool pattern remains the same — only the data source changes.
 */
@Slf4j
@Component
public class WebSearchSimulatorTool {

	private static final Map<String, List<SearchResult>> SIMULATED_RESULTS = Map.of(
			"transformer", List.of(
					new SearchResult("Attention Is All You Need - Original Paper",
							"https://arxiv.org/abs/1706.03762",
							"The Transformer model architecture relies entirely on attention mechanisms, "
									+ "dispensing with recurrence and convolutions entirely."),
					new SearchResult("GPT-4 Technical Report",
							"https://arxiv.org/abs/2303.08774",
							"GPT-4 is a large-scale multimodal model which can accept image and text inputs "
									+ "and produce text outputs.")
			),
			"rag", List.of(
					new SearchResult("Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks",
							"https://arxiv.org/abs/2005.11401",
							"RAG combines pre-trained parametric and non-parametric memory for language generation.")
			),
			"default", List.of(
					new SearchResult("No specific results found",
							"https://example.com",
							"Try refining your search query for more specific results.")
			)
	);

	public List<SearchResult> search(String query) {
		log.info("Web search (simulated) for: '{}'", query);
		String lowerQuery = query.toLowerCase();

		for (Map.Entry<String, List<SearchResult>> entry : SIMULATED_RESULTS.entrySet()) {
			if (lowerQuery.contains(entry.getKey())) {
				return entry.getValue();
			}
		}
		return SIMULATED_RESULTS.get("default");
	}

	public record SearchResult(String title, String url, String snippet) {}
}

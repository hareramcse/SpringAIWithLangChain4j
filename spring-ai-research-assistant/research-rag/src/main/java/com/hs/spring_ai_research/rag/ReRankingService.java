package com.hs.spring_ai_research.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * LLM-based re-ranking of retrieved chunks.
 *
 * After initial vector similarity retrieval, this service asks the LLM to score
 * each chunk's relevance to the query on a 1-10 scale, then reorders by score.
 * This catches semantically relevant chunks that may have lower cosine similarity.
 */
@Slf4j
@Service
public class ReRankingService {

	private final ChatModel fastModel;

	public ReRankingService(@Qualifier("fastModel") ChatModel fastModel) {
		this.fastModel = fastModel;
	}

	public List<TextSegment> reRank(String query, List<TextSegment> candidates, int topK) {
		if (candidates.isEmpty()) {
			return candidates;
		}

		List<ScoredSegment> scored = new ArrayList<>();
		for (TextSegment segment : candidates) {
			int score = scoreRelevance(query, segment.text());
			scored.add(new ScoredSegment(segment, score));
		}

		scored.sort(Comparator.comparingInt(ScoredSegment::score).reversed());

		List<TextSegment> reRanked = scored.stream()
				.limit(topK)
				.map(ScoredSegment::segment)
				.toList();

		log.info("Re-ranked {} candidates down to top-{}", candidates.size(), topK);
		return reRanked;
	}

	private int scoreRelevance(String query, String chunkText) {
		String prompt = """
				Rate how relevant the following text passage is to the query.
				Return ONLY a single integer from 1 to 10 (10 = highly relevant).

				Query: %s

				Passage: %s

				Score:""".formatted(query, truncate(chunkText, 500));

		try {
			String response = fastModel.chat(prompt).trim();
			return Integer.parseInt(response.replaceAll("[^0-9]", "").substring(0, 1));
		} catch (Exception e) {
			log.warn("Failed to parse re-ranking score, defaulting to 5: {}", e.getMessage());
			return 5;
		}
	}

	private String truncate(String text, int maxLength) {
		return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
	}

	private record ScoredSegment(TextSegment segment, int score) {}
}

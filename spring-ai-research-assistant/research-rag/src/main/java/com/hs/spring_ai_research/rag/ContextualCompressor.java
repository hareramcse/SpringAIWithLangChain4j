package com.hs.spring_ai_research.rag;

import java.util.ArrayList;
import java.util.List;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * Contextual compression: takes retrieved chunks and extracts ONLY the sentences
 * relevant to the query. This reduces noise and token usage when passing context to agents.
 *
 * Instead of sending a full 500-token chunk that's only partially relevant,
 * we compress it to just the 2-3 sentences that matter.
 */
@Slf4j
@Service
public class ContextualCompressor {

	private final ChatModel fastModel;

	public ContextualCompressor(@Qualifier("fastModel") ChatModel fastModel) {
		this.fastModel = fastModel;
	}

	public List<TextSegment> compress(String query, List<TextSegment> segments) {
		List<TextSegment> compressed = new ArrayList<>();

		for (TextSegment segment : segments) {
			String compressedText = extractRelevantContent(query, segment.text());
			if (!compressedText.isBlank()) {
				compressed.add(TextSegment.from(compressedText, segment.metadata()));
			}
		}

		log.info("Compressed {} segments (removed {} irrelevant segments)",
				compressed.size(), segments.size() - compressed.size());
		return compressed;
	}

	private String extractRelevantContent(String query, String chunkText) {
		String prompt = """
				Given the following query and text passage, extract ONLY the sentences
				that are directly relevant to answering the query.
				If no sentences are relevant, respond with "NONE".
				Do not add any commentary — return only the extracted sentences.

				Query: %s

				Passage: %s

				Relevant content:""".formatted(query, chunkText);

		String response = fastModel.chat(prompt).trim();
		if (response.equalsIgnoreCase("NONE") || response.isBlank()) {
			return "";
		}
		return response;
	}
}

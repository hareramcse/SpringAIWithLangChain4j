package com.hs.spring_ai_rag.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;

/**
 * LLM-as-judge relevance: the chat model returns a JSON array of scores in
 * {@code [0,1]} per passage batch.
 */
public class LlmCrossEncoderScoringModel implements ScoringModel {

	private static final Pattern MARKDOWN_FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$", Pattern.MULTILINE);
	private static final double NEUTRAL_FALLBACK_SCORE = 0.5;

	private final ChatModel chatModel;
	private final int maxSegmentsPerCall;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public LlmCrossEncoderScoringModel(ChatModel chatModel, int maxSegmentsPerCall) {
		this.chatModel = chatModel;
		this.maxSegmentsPerCall = Math.max(1, maxSegmentsPerCall);
	}

	@Override
	public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
		if (segments.isEmpty()) {
			return Response.from(List.of());
		}
		List<Double> scores = new ArrayList<>(segments.size());
		for (int from = 0; from < segments.size(); from += maxSegmentsPerCall) {
			int to = Math.min(from + maxSegmentsPerCall, segments.size());
			scores.addAll(scoreBatch(segments.subList(from, to), query));
		}
		return Response.from(scores);
	}

	private List<Double> scoreBatch(List<TextSegment> batch, String query) {
		String prompt = buildScoringPrompt(query, batch);
		String raw = chatModel.chat(prompt);
		String cleaned = stripMarkdownFences(raw);
		return parseAndClampScores(cleaned, batch.size());
	}

	private String buildScoringPrompt(String query, List<TextSegment> batch) {
		StringBuilder passages = new StringBuilder();
		for (int i = 0; i < batch.size(); i++) {
			passages.append("PASSAGE_").append(i).append(":\n").append(batch.get(i).text().replace("\"", "'"))
					.append("\n\n");
		}
		return """
				You are a cross-encoder relevance judge. Given a QUERY and numbered PASSAGE excerpts, output ONLY a JSON array of %d numbers, each between 0 and 1 inclusive, measuring how relevant that passage is to answering the QUERY. The array length must be exactly %d and order must match PASSAGE_0, PASSAGE_1, ...
				No markdown, no keys, no explanation — only the JSON array.

				QUERY:
				%s

				%s
				"""
				.formatted(batch.size(), batch.size(), query, passages);
	}

	private String stripMarkdownFences(String raw) {
		return MARKDOWN_FENCE.matcher(raw.trim()).replaceAll("").trim();
	}

	private List<Double> parseAndClampScores(String json, int expectedCount) {
		try {
			List<Double> parsed = objectMapper.readValue(json, new TypeReference<>() {
			});
			if (parsed.size() != expectedCount) {
				return neutralScores(expectedCount);
			}
			List<Double> clamped = new ArrayList<>(parsed.size());
			for (Double v : parsed) {
				double x = v == null ? 0.0 : v;
				clamped.add(clamp01(x));
			}
			return clamped;
		} catch (Exception e) {
			return neutralScores(expectedCount);
		}
	}

	private double clamp01(double x) {
		return Math.max(0.0, Math.min(1.0, x));
	}

	private List<Double> neutralScores(int n) {
		List<Double> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(NEUTRAL_FALLBACK_SCORE);
		}
		return out;
	}
}

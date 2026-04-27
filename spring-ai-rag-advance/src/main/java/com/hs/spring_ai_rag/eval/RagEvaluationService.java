package com.hs.spring_ai_rag.eval;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hs.spring_ai_rag.config.AppEvalProperties;
import com.hs.spring_ai_rag.service.ChatService;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Metadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads regression cases from {@link AppEvalProperties#datasetResource()} (separate from ingest corpus), runs the same
 * {@link RetrievalAugmentor} as chat, then {@link ChatService}, and scores chunk + answer checks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEvaluationService {

	private static final int CHUNK_PREVIEW_CHARS = 220;

	private final RetrievalAugmentor retrievalAugmentor;
	private final ChatService chatService;
	private final ObjectMapper objectMapper;
	private final ResourceLoader resourceLoader;
	private final AppEvalProperties evalProperties;

	public RagEvalReport runEvaluation() throws IOException {
		RagEvalDataset dataset = loadDataset();
		List<RagEvalCaseResult> results = new ArrayList<>();
		long chunkHits = 0;
		long answerPasses = 0;
		long answerEvaluated = 0;

		for (RagEvalCase c : dataset.cases()) {
			try {
				RagEvalCaseResult row = evaluateOne(c);
				results.add(row);
				if (row.chunkHit()) {
					chunkHits++;
				}
				if (row.answerEvaluated()) {
					answerEvaluated++;
					if (row.answerPass()) {
						answerPasses++;
					}
				}
			} catch (Exception e) {
				log.warn("Eval case {} failed: {}", c.id(), e.toString());
				results.add(RagEvalCaseResult.error(c.id(), c.query(), e.getMessage()));
			}
		}

		int n = results.size();
		double chunkRate = n == 0 ? 0.0 : (double) chunkHits / n;
		Double answerRate =
				answerEvaluated == 0 ? null : (double) answerPasses / answerEvaluated;
		return new RagEvalReport(n, chunkHits, answerPasses, answerEvaluated, chunkRate, answerRate, results);
	}

	private RagEvalDataset loadDataset() throws IOException {
		Resource resource = resourceLoader.getResource(evalProperties.datasetResource());
		try (InputStream in = resource.getInputStream()) {
			return objectMapper.readValue(in, RagEvalDataset.class);
		}
	}

	private RagEvalCaseResult evaluateOne(RagEvalCase c) {
		UserMessage userMessage = UserMessage.from(c.query());
		Metadata metadata = Metadata.from(userMessage, null, List.of());
		AugmentationRequest request = new AugmentationRequest(userMessage, metadata);
		AugmentationResult augmented = retrievalAugmentor.augment(request);

		List<String> previews = new ArrayList<>();
		StringBuilder retrievedConcat = new StringBuilder();
		for (Content content : augmented.contents()) {
			if (content.textSegment() == null) {
				continue;
			}
			String text = content.textSegment().text();
			retrievedConcat.append(text).append('\n');
			previews.add(preview(text));
		}

		boolean chunkHit = EvalTextMatch.containsNormalized(retrievedConcat.toString(), c.expectedDocumentContains());

		String answer = chatService.chat(c.query());
		boolean answerEvaluated = c.hasAnswerCriteria();
		boolean answerPass =
				answerEvaluated && EvalTextMatch.allPhrasesPresent(answer, c.expectedAnswerContains());

		return new RagEvalCaseResult(
				c.id(), c.query(), chunkHit, answerPass, answerEvaluated, answer, previews, null);
	}

	private static String preview(String text) {
		String t = text.replace('\n', ' ').trim();
		return t.length() <= CHUNK_PREVIEW_CHARS ? t : t.substring(0, CHUNK_PREVIEW_CHARS) + "…";
	}
}

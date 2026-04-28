package com.hs.spring_ai_rag.evaluation;

import java.io.IOException;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hs.spring_ai_rag.service.ChatService;

import lombok.RequiredArgsConstructor;

@Component
@Profile("eval")
@Order(200)
@RequiredArgsConstructor
public class RagOfflineEvaluationRunner implements CommandLineRunner {

	private final ChatService chatService;
	private final ObjectMapper objectMapper;

	@org.springframework.beans.factory.annotation.Value("classpath:evaluation/golden-eval.json")
	private Resource goldenDataset;

	@Override
	public void run(String... args) throws Exception {
		GoldenEvalDataset dataset = loadDataset();
		int failed = 0;
		for (GoldenEvalCase c : dataset.cases()) {
			try {
				String answer = chatService.chat(c.question());
				assertExpectations(c, answer);
			} catch (AssertionError | Exception e) {
				failed++;
			}
		}
		if (failed > 0) {
			throw new IllegalStateException("eval:" + failed + "/" + dataset.cases().size());
		}
	}

	private GoldenEvalDataset loadDataset() throws IOException {
		try (var in = goldenDataset.getInputStream()) {
			GoldenEvalDataset ds = objectMapper.readValue(in, GoldenEvalDataset.class);
			if (ds.cases() == null || ds.cases().isEmpty()) {
				throw new IllegalStateException("empty cases");
			}
			return ds;
		}
	}

	private static void assertExpectations(GoldenEvalCase c, String answer) {
		String lower = answer.toLowerCase();
		var must = c.answerMustContain();
		if (must != null) {
			for (String fragment : must) {
				if (fragment != null && !fragment.isBlank() && !lower.contains(fragment.toLowerCase())) {
					throw new AssertionError();
				}
			}
		}
		var mustNot = c.answerMustNotContain();
		if (mustNot != null) {
			for (String fragment : mustNot) {
				if (fragment != null && !fragment.isBlank() && lower.contains(fragment.toLowerCase())) {
					throw new AssertionError();
				}
			}
		}
	}
}

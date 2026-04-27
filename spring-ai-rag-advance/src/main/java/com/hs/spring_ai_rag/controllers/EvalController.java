package com.hs.spring_ai_rag.controllers;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_rag.eval.RagEvalReport;
import com.hs.spring_ai_rag.eval.RagEvaluationService;

import lombok.RequiredArgsConstructor;

/**
 * Runs RAG evaluation from {@code app.eval.dataset-resource} (separate from ingest corpus; calls OpenAI like {@code /chat}).
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.eval", name = "http-enabled", havingValue = "true")
public class EvalController {

	private final RagEvaluationService ragEvaluationService;

	@PostMapping("/eval/run")
	public ResponseEntity<RagEvalReport> runEval() throws IOException {
		return ResponseEntity.ok(ragEvaluationService.runEvaluation());
	}
}

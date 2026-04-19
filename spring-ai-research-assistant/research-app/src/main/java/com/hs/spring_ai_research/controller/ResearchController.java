package com.hs.spring_ai_research.controller;

import java.util.Optional;


import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.hs.spring_ai_research.agent.OrchestratorService;
import com.hs.spring_ai_research.agent.OrchestratorService.OrchestratorResult;
import com.hs.spring_ai_research.cache.SemanticCacheService;
import com.hs.spring_ai_research.dto.ResearchRequest;
import com.hs.spring_ai_research.dto.ResearchResponse;
import com.hs.spring_ai_research.guardrails.GuardrailChain;
import com.hs.spring_ai_research.guardrails.GuardrailChain.InputGuardrailResult;
import com.hs.spring_ai_research.routing.ModelRouter;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Main research endpoint — the primary entry point for user queries.
 *
 * <p>Endpoint: {@code POST /api/research}</p>
 * <p>Flow: Guardrails → Cache → Model Routing → Multi-Agent Pipeline → Output Check → Cache Store</p>
 *
 * <p>Also provides an SSE streaming endpoint ({@code GET /api/research/stream})
 * for real-time progress updates.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/research")
@RequiredArgsConstructor
public class ResearchController {

	private final GuardrailChain guardrailChain;
	private final SemanticCacheService cacheService;
	private final ModelRouter modelRouter;
	private final OrchestratorService orchestratorService;

	// ── POST /api/research ──────────────────────────────────────────────────────

	/**
	 * Runs the full research pipeline for a user question.
	 * Returns the report, review, metadata, or an error/block reason.
	 */
	@PostMapping
	public ResponseEntity<?> research(@Valid @RequestBody ResearchRequest request) {
		log.info("Research request received: '{}'", request.question());

		// Step 1: Input guardrails (injection detection, PII redaction)
		InputGuardrailResult guardrailResult = guardrailChain.processInput(request.question());
		if (guardrailResult.blocked()) {
			log.warn("Request blocked by guardrails: {}", guardrailResult.blockReason().reason());
			return ResponseEntity.badRequest().body(
					new ResearchResponse("BLOCKED", null, null, null,
							guardrailResult.blockReason().reason(), null, null));
		}
		String safeQuestion = guardrailResult.processedInput();

		// Step 2: Semantic cache lookup (skip if requested)
		if (!request.skipCache()) {
			Optional<String> cached = cacheService.lookup(safeQuestion);
			if (cached.isPresent()) {
				log.info("Returning cached response");
				return ResponseEntity.ok(
						new ResearchResponse("CACHED", cached.get(), null, null, null,
								new ResearchResponse.Metadata("cache-hit",
										modelRouter.getRoutedModelName(safeQuestion), 0, 0), null));
			}
		}

		// Step 3: Model routing — log which model will be used
		String selectedModel = modelRouter.getRoutedModelName(safeQuestion);
		double complexity = modelRouter.scoreComplexity(safeQuestion);
		log.info("Model routed: {} (complexity: {})", selectedModel, String.format("%.2f", complexity));

		// Step 4: Execute the multi-agent pipeline (Researcher → Writer → Reviewer)
		OrchestratorResult result = orchestratorService.execute(safeQuestion);

		// Step 5: Output guardrails — check the generated report for harmful content
		if (result.report() != null) {
			var outputCheck = guardrailChain.processOutput(result.report());
			if (outputCheck.blocked()) {
				log.warn("Output blocked by content moderation: {}", outputCheck.reason());
				return ResponseEntity.ok(
						new ResearchResponse("MODERATED", null, null, null,
								"Response was flagged by content moderation.", null, null));
			}
		}

		// Step 6: Cache the successful result for future similar queries
		if (result.report() != null) {
			cacheService.store(safeQuestion, result.report());
		}

		return ResponseEntity.ok(new ResearchResponse(
				result.status(), result.report(), result.review(), result.researchBrief(), null,
				new ResearchResponse.Metadata(selectedModel, selectedModel,
						result.metadata().totalAgentCalls(), result.metadata().durationMs()),
				result.approvalPipelineId()));
	}

	// ── GET /api/research/stream (SSE) ──────────────────────────────────────────

	/**
	 * Server-Sent Events streaming endpoint. Sends status updates as the pipeline
	 * progresses: guardrails → research → report → review → done.
	 */
	@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter streamResearch(@RequestParam String question) {
		SseEmitter emitter = new SseEmitter(300_000L);

		Thread.startVirtualThread(() -> {
			try {
				emitter.send(SseEmitter.event()
						.name("status").data("Starting research pipeline..."));

				InputGuardrailResult guardrailResult = guardrailChain.processInput(question);
				if (guardrailResult.blocked()) {
					emitter.send(SseEmitter.event()
							.name("error").data("Blocked: " + guardrailResult.blockReason().reason()));
					emitter.complete();
					return;
				}

				emitter.send(SseEmitter.event()
						.name("status").data("Guardrails passed. Searching knowledge base..."));

				String safeQuestion = guardrailResult.processedInput();
				OrchestratorResult result = orchestratorService.execute(safeQuestion);

				emitter.send(SseEmitter.event()
						.name("status").data("Research complete. Generating report..."));

				if (result.researchBrief() != null) {
					emitter.send(SseEmitter.event()
							.name("research").data(result.researchBrief()));
				}

				if (result.report() != null) {
					emitter.send(SseEmitter.event()
							.name("report").data(result.report()));
				}

				if (result.review() != null) {
					emitter.send(SseEmitter.event()
							.name("review").data(result.review()));
				}

				emitter.send(SseEmitter.event()
						.name("done").data(result.status()));
				emitter.complete();

			} catch (Exception e) {
				log.error("SSE streaming error", e);
				emitter.completeWithError(e);
			}
		});

		return emitter;
	}
}

package com.hs.spring_ai_research.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_research.agent.StructuredReviewerService;
import com.hs.spring_ai_research.agent.StructuredWriterService;
import com.hs.spring_ai_research.dto.StructuredReport;
import com.hs.spring_ai_research.dto.StructuredReview;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CONCEPT: Structured Output Extraction via @AiService
 *
 * <p>Demonstrates type-safe POJO extraction from LLM responses.
 * Compare the {@code @AiService} approach here (automatic JSON schema injection + parsing)
 * with the manual approach in {@link com.hs.spring_ai_research.agent.ReviewerAgent}
 * (string parsing, fragile).</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code POST /api/structured/write} — get a {@link StructuredReport} POJO from the LLM</li>
 *   <li>{@code POST /api/structured/review} — get a {@link StructuredReview} POJO from the LLM</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/structured")
@RequiredArgsConstructor
public class StructuredOutputController {

	private final StructuredWriterService writerService;
	private final StructuredReviewerService reviewerService;

	@PostMapping("/write")
	public ResponseEntity<StructuredReport> writeStructuredReport(
			@RequestBody String researchBrief) {
		log.info("Structured write request received");
		StructuredReport report = writerService.writeReport(researchBrief);
		return ResponseEntity.ok(report);
	}

	@PostMapping("/review")
	public ResponseEntity<StructuredReview> structuredReview(
			@RequestBody ReviewRequest request) {
		log.info("Structured review request received");
		StructuredReview review = reviewerService.review(
				request.question(), request.findings(), request.report());
		return ResponseEntity.ok(review);
	}

	public record ReviewRequest(String question, String findings, String report) {}
}

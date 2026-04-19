package com.hs.spring_ai_research.controller;

import java.util.List;
import java.util.Map;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_research.dx.DebugService;
import com.hs.spring_ai_research.dx.PromptRegistry;
import com.hs.spring_ai_research.dx.PromptTestHarness;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Developer experience endpoints — prompt management, testing, and debug traces.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET  /api/dx/prompts} — list all registered prompts and active versions</li>
 *   <li>{@code POST /api/dx/prompts/test} — test a prompt against expected outputs</li>
 *   <li>{@code POST /api/dx/prompts/quick-test} — quick one-off prompt test</li>
 *   <li>{@code POST /api/dx/debug/enable} — enable/disable debug mode</li>
 *   <li>{@code GET  /api/dx/debug/status} — check debug mode and active traces</li>
 *   <li>{@code GET  /api/dx/debug/{requestId}} — get the full reasoning chain for a request</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/dx")
@RequiredArgsConstructor
public class DxController {

	private final PromptRegistry promptRegistry;
	private final PromptTestHarness promptTestHarness;
	private final DebugService debugService;

	// ── Prompt management ───────────────────────────────────────────────────────

	@GetMapping("/prompts")
	public ResponseEntity<?> listPrompts() {
		return ResponseEntity.ok(Map.of(
				"prompts", promptRegistry.listPrompts(),
				"activeVersions", promptRegistry.getActiveVersions()
		));
	}

	@PostMapping("/prompts/test")
	public ResponseEntity<?> testPrompt(@RequestBody PromptTestRequest request) {
		log.info("Testing prompt: '{}'", request.promptName());
		PromptTestHarness.TestReport report = promptTestHarness.runTests(
				request.promptName(), request.testCases());
		return ResponseEntity.ok(report);
	}

	@PostMapping("/prompts/quick-test")
	public ResponseEntity<?> quickTest(@RequestBody QuickTestRequest request) {
		PromptTestHarness.TestResult result = promptTestHarness.quickTest(
				request.prompt(), request.expectedKeywords());
		return ResponseEntity.ok(result);
	}

	// ── Debug tracing ───────────────────────────────────────────────────────────

	@PostMapping("/debug/enable")
	public ResponseEntity<?> setDebugMode(@RequestParam boolean enabled) {
		debugService.setEnabled(enabled);
		return ResponseEntity.ok(Map.of("debugEnabled", enabled));
	}

	@GetMapping("/debug/status")
	public ResponseEntity<?> getDebugStatus() {
		return ResponseEntity.ok(Map.of(
				"enabled", debugService.isEnabled(),
				"activeTraces", debugService.listTraces()
		));
	}

	@GetMapping("/debug/{requestId}")
	public ResponseEntity<?> getDebugTrace(@PathVariable String requestId) {
		DebugService.DebugTrace trace = debugService.getTrace(requestId);
		if (trace == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(trace);
	}

	record PromptTestRequest(String promptName, List<PromptTestHarness.TestCase> testCases) {}

	record QuickTestRequest(String prompt, List<String> expectedKeywords) {}
}

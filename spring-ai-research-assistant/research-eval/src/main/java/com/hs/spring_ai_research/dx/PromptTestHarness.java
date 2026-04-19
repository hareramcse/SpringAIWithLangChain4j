package com.hs.spring_ai_research.dx;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * Test harness for prompts — the AI equivalent of unit tests.
 *
 * Before deploying a prompt change, test it:
 * 1. Define test cases: input + expected output patterns
 * 2. Run the prompt against all test cases
 * 3. Check if outputs match expectations (keyword presence, format, etc.)
 * 4. Get a pass/fail report with full traceability
 *
 * This answers: "how do team members test prompt changes?"
 * Answer: they define test cases, run them through this harness,
 * and check the results before deploying.
 */
@Slf4j
@Service
public class PromptTestHarness {

	private final ChatModel fastModel;
	private final PromptRegistry promptRegistry;

	public PromptTestHarness(
			@Qualifier("fastModel") ChatModel fastModel,
			PromptRegistry promptRegistry) {
		this.fastModel = fastModel;
		this.promptRegistry = promptRegistry;
	}

	/**
	 * Test a prompt template against a set of test cases.
	 */
	public TestReport runTests(String promptName, List<TestCase> testCases) {
		String template = promptRegistry.getPrompt(promptName);
		if (template == null) {
			return new TestReport(promptName, "UNKNOWN", Instant.now(), 0, 0, List.of(),
					"Prompt not found: " + promptName);
		}

		log.info("Running {} test cases for prompt '{}'", testCases.size(), promptName);
		List<TestResult> results = new ArrayList<>();
		int passed = 0;

		for (TestCase testCase : testCases) {
			String prompt = template;
			for (Map.Entry<String, String> var : testCase.variables().entrySet()) {
				prompt = prompt.replace("{" + var.getKey() + "}", var.getValue());
			}

			try {
				long start = System.currentTimeMillis();
				String output = fastModel.chat(prompt);
				long latency = System.currentTimeMillis() - start;

				boolean pass = checkExpectations(output, testCase.expectedKeywords(), testCase.forbiddenKeywords());
				if (pass) passed++;

				results.add(new TestResult(testCase.name(), pass, output, latency, null));
			} catch (Exception e) {
				results.add(new TestResult(testCase.name(), false, null, 0, e.getMessage()));
			}
		}

		String verdict = passed == testCases.size() ? "ALL_PASSED"
				: passed > 0 ? "PARTIAL_PASS" : "ALL_FAILED";

		return new TestReport(promptName, verdict, Instant.now(), passed,
				testCases.size() - passed, results, null);
	}

	/**
	 * Quick test: run a raw prompt and check for expected keywords in the output.
	 */
	public TestResult quickTest(String prompt, List<String> expectedKeywords) {
		try {
			long start = System.currentTimeMillis();
			String output = fastModel.chat(prompt);
			long latency = System.currentTimeMillis() - start;

			boolean pass = checkExpectations(output, expectedKeywords, List.of());
			return new TestResult("quick-test", pass, output, latency, null);
		} catch (Exception e) {
			return new TestResult("quick-test", false, null, 0, e.getMessage());
		}
	}

	private boolean checkExpectations(String output, List<String> expectedKeywords, List<String> forbiddenKeywords) {
		if (output == null) return false;
		String outputLower = output.toLowerCase();

		for (String keyword : expectedKeywords) {
			if (!outputLower.contains(keyword.toLowerCase())) {
				return false;
			}
		}

		if (forbiddenKeywords != null) {
			for (String forbidden : forbiddenKeywords) {
				if (outputLower.contains(forbidden.toLowerCase())) {
					return false;
				}
			}
		}
		return true;
	}

	public record TestCase(String name, Map<String, String> variables,
						   List<String> expectedKeywords, List<String> forbiddenKeywords) {}

	public record TestResult(String testName, boolean passed, String output, long latencyMs, String error) {}

	public record TestReport(String promptName, String verdict, Instant timestamp,
							 int totalPassed, int totalFailed, List<TestResult> results, String error) {}
}

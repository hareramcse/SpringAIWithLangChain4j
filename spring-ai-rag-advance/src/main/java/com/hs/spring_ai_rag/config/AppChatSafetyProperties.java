package com.hs.spring_ai_rag.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code app.chat.safety.*}: entry query checks and post–LLM response checks.
 */
@ConfigurationProperties(prefix = "app.chat.safety")
@Getter
@Setter
public class AppChatSafetyProperties {

	private boolean enabled = true;

	/** Reject queries longer than this (prompt stuffing / DoS). */
	private int maxQueryChars = 8000;

	/** Reject model replies longer than this. */
	private int maxResponseChars = 32000;

	/**
	 * Extra regex patterns (case-insensitive) applied to the user query; match anywhere causes rejection.
	 * Defaults are built-in in {@link com.hs.spring_ai_rag.safety.QueryEntryGuardrail}.
	 */
	private List<String> extraBlockedQueryRegexes = new ArrayList<>();

	/**
	 * Regex patterns applied to the assistant reply (e.g. meta-refusal leaks); match causes rejection or fallback.
	 */
	private List<String> extraBlockedResponseRegexes = new ArrayList<>();

	/**
	 * Second-pass classifier: asks the chat model whether the reply looks grounded / non-fabricated for RAG.
	 * Extra latency and token cost; enable when you need stronger hallucination screening.
	 */
	private boolean postLlmJudgeEnabled = false;

	/** When post-checks fail and {@link #rejectUnsafeResponseWithError} is false, return this body with 200. */
	private String unsafeResponseFallback = "Rejected.";

	/**
	 * If true, unsafe responses yield HTTP 422 (Unprocessable Content); if false, {@link #unsafeResponseFallback} is returned with 200.
	 */
	private boolean rejectUnsafeResponseWithError = true;
}

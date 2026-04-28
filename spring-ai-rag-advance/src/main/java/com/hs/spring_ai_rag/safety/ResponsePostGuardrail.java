package com.hs.spring_ai_rag.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.hs.spring_ai_rag.config.AppChatSafetyProperties;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Post guard: response length, regex leak screens, optional LLM judge for likely hallucination / unsafe replies.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class ResponsePostGuardrail {

	private static final List<Pattern> DEFAULT_RESPONSE_BLOCKED = List.of(
			Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|prior)\\s+instructions"),
			Pattern.compile("(?i)<\\|?(system|assistant)\\|?>"),
			Pattern.compile("(?i)###\\s*system\\s*"));

	private final AppChatSafetyProperties props;
	private final ChatModel chatModel;

	/**
	 * @return the assistant text to return (possibly unchanged); may throw {@link UnsafeResponseException}
	 */
	public String validate(String userQuery, String assistantReply) {
		if (!props.isEnabled()) {
			return assistantReply;
		}
		if (assistantReply == null) {
			return handleFailure("Empty assistant response.");
		}
		if (assistantReply.length() > props.getMaxResponseChars()) {
			return handleFailure("Response exceeds maximum length.");
		}
		for (Pattern p : mergeResponsePatterns()) {
			if (p.matcher(assistantReply).find()) {
				return handleFailure("Response matched a blocked pattern.");
			}
		}
		if (props.isPostLlmJudgeEnabled() && !passesLlmGroundingJudge(userQuery, assistantReply)) {
			return handleFailure("Response failed hallucination safety review.");
		}
		return assistantReply;
	}

	private List<Pattern> mergeResponsePatterns() {
		List<Pattern> out = new ArrayList<>(DEFAULT_RESPONSE_BLOCKED);
		for (String raw : props.getExtraBlockedResponseRegexes()) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			out.add(Pattern.compile(raw, Pattern.CASE_INSENSITIVE | Pattern.DOTALL));
		}
		return out;
	}

	private boolean passesLlmGroundingJudge(String userQuery, String assistantReply) {
		String prompt = """
				You review assistant answers for a retrieval-only FAQ bot. The assistant must NOT invent policies, SKUs, prices, or legal facts.
				Reply with exactly one word: SAFE if the answer is cautious and consistent with only quoting or summarizing generic guidance without fabricated specifics, or UNSAFE if it states concrete facts that could be fabricated.

				USER_QUESTION:
				%s

				ASSISTANT_ANSWER:
				%s
				"""
				.formatted(userQuery.replace("###", ""), assistantReply.replace("###", ""));
		try {
			String verdict = chatModel.chat(prompt).trim();
			String firstLine = verdict.lines().findFirst().orElse("").trim().toUpperCase(Locale.ROOT);
			if (firstLine.contains("UNSAFE")) {
				log.warn("LLM judge marked response UNSAFE.");
				return false;
			}
			if (firstLine.contains("SAFE")) {
				return true;
			}
			log.warn("LLM judge unclear verdict ({}); treating as unsafe.", truncate(verdict, 120));
			return false;
		} catch (Exception e) {
			log.warn("LLM judge failed; treating as unsafe: {}", e.toString());
			return false;
		}
	}

	private String truncate(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max) + "...";
	}

	private String handleFailure(String reason) {
		log.warn("Post-LLM guardrail: {}", reason);
		if (!props.isRejectUnsafeResponseWithError()) {
			return props.getUnsafeResponseFallback();
		}
		throw new UnsafeResponseException(reason);
	}
}

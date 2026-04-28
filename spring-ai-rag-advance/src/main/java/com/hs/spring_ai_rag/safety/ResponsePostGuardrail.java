package com.hs.spring_ai_rag.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.hs.spring_ai_rag.config.AppChatSafetyProperties;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public final class ResponsePostGuardrail {

	private static final List<Pattern> DEFAULT_RESPONSE_BLOCKED = List.of(
			Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|prior)\\s+instructions"),
			Pattern.compile("(?i)<\\|?(system|assistant)\\|?>"),
			Pattern.compile("(?i)###\\s*system\\s*"));

	private final AppChatSafetyProperties props;
	private final ChatModel chatModel;

	public String validate(String userQuery, String assistantReply) {
		if (!props.isEnabled()) {
			return assistantReply;
		}
		if (assistantReply == null) {
			return handleFailure();
		}
		if (assistantReply.length() > props.getMaxResponseChars()) {
			return handleFailure();
		}
		for (Pattern p : mergeResponsePatterns()) {
			if (p.matcher(assistantReply).find()) {
				return handleFailure();
			}
		}
		if (props.isPostLlmJudgeEnabled() && !passesLlmGroundingJudge(userQuery, assistantReply)) {
			return handleFailure();
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
				Classify the assistant answer for FAQ grounding. Reply SAFE or UNSAFE only.

				Q: %s
				A: %s
				"""
				.formatted(userQuery.replace("###", ""), assistantReply.replace("###", ""));
		try {
			String verdict = chatModel.chat(prompt).trim();
			String firstLine = verdict.lines().findFirst().orElse("").trim().toUpperCase(Locale.ROOT);
			if (firstLine.contains("UNSAFE")) {
				return false;
			}
			return firstLine.contains("SAFE");
		} catch (Exception e) {
			return false;
		}
	}

	private String handleFailure() {
		if (!props.isRejectUnsafeResponseWithError()) {
			return props.getUnsafeResponseFallback();
		}
		throw new UnsafeResponseException("Rejected.");
	}
}

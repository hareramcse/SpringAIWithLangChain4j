package com.hs.spring_ai_rag.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.hs.spring_ai_rag.config.AppChatSafetyProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public final class QueryEntryGuardrail {

	private static final List<Pattern> DEFAULT_BLOCKED = List.of(
			Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions"),
			Pattern.compile("(?i)disregard\\s+(the\\s+)?(above|prior|instructions)"),
			Pattern.compile("(?i)override\\s+(the\\s+)?(system|prior)\\s+"),
			Pattern.compile("(?i)<\\|?(system|assistant|user)\\|?>"),
			Pattern.compile("(?i)\\[\\s*INST\\s*\\]"),
			Pattern.compile("(?i)you\\s+are\\s+no\\s+longer\\s+a"),
			Pattern.compile("(?i)new\\s+instructions\\s*:"),
			Pattern.compile("(?i)###\\s*(system|instruction)\\s*"));

	private final AppChatSafetyProperties props;

	public void validate(String userQuery) {
		if (!props.isEnabled()) {
			return;
		}
		if (userQuery == null || userQuery.isBlank()) {
			throw new UnsafeQueryException("Empty query.");
		}
		if (userQuery.length() > props.getMaxQueryChars()) {
			throw new UnsafeQueryException("Query too long.");
		}
		List<Pattern> patterns = mergePatterns();
		for (Pattern p : patterns) {
			if (p.matcher(userQuery).find()) {
				throw new UnsafeQueryException("Blocked.");
			}
		}
	}

	private List<Pattern> mergePatterns() {
		List<Pattern> out = new ArrayList<>(DEFAULT_BLOCKED);
		for (String raw : props.getExtraBlockedQueryRegexes()) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			out.add(Pattern.compile(raw, Pattern.CASE_INSENSITIVE | Pattern.DOTALL));
		}
		return out;
	}
}

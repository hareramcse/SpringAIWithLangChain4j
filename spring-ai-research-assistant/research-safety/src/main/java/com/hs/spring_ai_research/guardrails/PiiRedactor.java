package com.hs.spring_ai_research.guardrails;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;


import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Detects and redacts Personally Identifiable Information (PII) before sending to the LLM.
 * Uses regex patterns for common PII types: emails, phone numbers, SSNs, credit cards.
 *
 * Redaction replaces PII with placeholders like [EMAIL_REDACTED] so the LLM
 * can still understand the context without seeing actual personal data.
 */
@Slf4j
@Component
public class PiiRedactor {

	private static final Map<String, Pattern> PII_PATTERNS = new LinkedHashMap<>();

	static {
		PII_PATTERNS.put("[EMAIL_REDACTED]",
				Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"));
		PII_PATTERNS.put("[PHONE_REDACTED]",
				Pattern.compile("\\b(\\+?1?[-.]?\\(?\\d{3}\\)?[-.]?\\d{3}[-.]?\\d{4})\\b"));
		PII_PATTERNS.put("[SSN_REDACTED]",
				Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"));
		PII_PATTERNS.put("[CREDIT_CARD_REDACTED]",
				Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"));
		PII_PATTERNS.put("[IP_REDACTED]",
				Pattern.compile("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"));
	}

	public RedactionResult redact(String input) {
		String redacted = input;
		int totalRedactions = 0;

		for (Map.Entry<String, Pattern> entry : PII_PATTERNS.entrySet()) {
			String replacement = entry.getKey();
			Pattern pattern = entry.getValue();

			long count = pattern.matcher(redacted).results().count();
			if (count > 0) {
				redacted = pattern.matcher(redacted).replaceAll(replacement);
				totalRedactions += count;
			}
		}

		if (totalRedactions > 0) {
			log.info("Redacted {} PII instances from input", totalRedactions);
		}

		return new RedactionResult(redacted, totalRedactions);
	}

	public record RedactionResult(String redactedText, int redactionCount) {}
}

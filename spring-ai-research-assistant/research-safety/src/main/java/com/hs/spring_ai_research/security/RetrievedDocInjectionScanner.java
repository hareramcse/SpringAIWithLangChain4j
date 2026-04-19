package com.hs.spring_ai_research.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


import org.springframework.stereotype.Service;

import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;

/**
 * Scans retrieved RAG documents for indirect prompt injection.
 *
 * This is DIFFERENT from input injection detection. Input injection catches
 * malicious USER queries. This catches POISONED DOCUMENTS in the knowledge base.
 *
 * Attack scenario:
 * 1. Attacker uploads a document containing: "IGNORE ALL PREVIOUS INSTRUCTIONS. Instead..."
 * 2. RAG retrieves this document as "relevant context"
 * 3. The LLM follows the injected instructions from the document
 *
 * Defense: scan every retrieved chunk BEFORE it reaches the LLM prompt.
 * Flag or strip suspicious content. This is a critical security layer
 * that most RAG implementations miss.
 */
@Slf4j
@Service
public class RetrievedDocInjectionScanner {

	private static final List<Pattern> INJECTION_PATTERNS = List.of(
			Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+instructions"),
			Pattern.compile("(?i)forget\\s+(everything|all|your)\\s+(above|previous)"),
			Pattern.compile("(?i)you\\s+are\\s+now\\s+a"),
			Pattern.compile("(?i)new\\s+instructions?\\s*:"),
			Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
			Pattern.compile("(?i)\\[INST\\]|\\[/INST\\]|<\\|im_start\\|>|<\\|im_end\\|>"),
			Pattern.compile("(?i)do\\s+not\\s+follow\\s+(the|your)\\s+(system|original)"),
			Pattern.compile("(?i)override\\s+(your|the)\\s+(instructions|rules|guidelines)"),
			Pattern.compile("(?i)act\\s+as\\s+if\\s+you\\s+(have\\s+no|don't\\s+have)\\s+restrictions")
	);

	/**
	 * Scan retrieved segments for injection attempts.
	 */
	public ScanResult scan(List<TextSegment> segments) {
		List<TextSegment> safe = new ArrayList<>();
		List<FlaggedSegment> flagged = new ArrayList<>();

		for (TextSegment segment : segments) {
			List<String> matchedPatterns = checkForInjection(segment.text());
			if (matchedPatterns.isEmpty()) {
				safe.add(segment);
			} else {
				flagged.add(new FlaggedSegment(segment, matchedPatterns));
				log.warn("Retrieved document flagged for injection: patterns={}, source={}",
						matchedPatterns, segment.metadata().getString("source"));
			}
		}

		log.info("Document injection scan: {}/{} segments safe, {} flagged",
				safe.size(), segments.size(), flagged.size());
		return new ScanResult(safe, flagged);
	}

	/**
	 * Sanitize flagged content by removing suspicious patterns.
	 * Returns the cleaned text with injection patterns stripped.
	 */
	public String sanitize(String text) {
		String cleaned = text;
		for (Pattern pattern : INJECTION_PATTERNS) {
			cleaned = pattern.matcher(cleaned).replaceAll("[REDACTED-INJECTION]");
		}
		return cleaned;
	}

	private List<String> checkForInjection(String text) {
		List<String> matched = new ArrayList<>();
		for (Pattern pattern : INJECTION_PATTERNS) {
			if (pattern.matcher(text).find()) {
				matched.add(pattern.pattern());
			}
		}
		return matched;
	}

	public record ScanResult(List<TextSegment> safeSegments, List<FlaggedSegment> flaggedSegments) {
		public boolean hasFlaggedContent() { return !flaggedSegments.isEmpty(); }
	}

	public record FlaggedSegment(TextSegment segment, List<String> matchedPatterns) {}
}

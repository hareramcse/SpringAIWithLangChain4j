package com.hs.spring_ai_rag.eval;

import java.util.Locale;

final class EvalTextMatch {

	private EvalTextMatch() {
	}

	static String normalize(String s) {
		if (s == null) {
			return "";
		}
		return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
	}

	static boolean containsNormalized(String haystack, String needle) {
		return normalize(haystack).contains(normalize(needle));
	}

	static boolean allPhrasesPresent(String haystack, Iterable<String> phrases) {
		for (String p : phrases) {
			if (p == null || p.isBlank()) {
				continue;
			}
			if (!containsNormalized(haystack, p)) {
				return false;
			}
		}
		return true;
	}
}

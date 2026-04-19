package com.hs.spring_ai_research.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;


import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Data cleaning pipeline for ingested documents.
 *
 * RAG is only as good as the data. Garbage in = garbage out.
 * This service handles:
 * - Unicode normalization (smart quotes, em dashes, etc.)
 * - Whitespace normalization (collapse multiple spaces/newlines)
 * - Boilerplate removal (headers, footers, page numbers)
 * - Quality scoring (reject too-short or too-repetitive content)
 * - Content deduplication (hash-based duplicate detection)
 */
@Slf4j
@Service
public class DataCleaningService {

	private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s{2,}");
	private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\n{3,}");
	private static final Pattern PAGE_NUMBERS = Pattern.compile("(?m)^\\s*(?:Page|p\\.?)\\s*\\d+\\s*$");
	private static final Pattern HEADERS_FOOTERS = Pattern.compile(
			"(?mi)^\\s*(confidential|draft|internal use only|all rights reserved|copyright ©).*$");

	/**
	 * Full cleaning pipeline: normalize -> remove boilerplate -> assess quality.
	 */
	public CleaningResult clean(String rawText) {
		int originalLength = rawText.length();

		String cleaned = normalizeUnicode(rawText);
		cleaned = normalizeWhitespace(cleaned);
		cleaned = removeBoilerplate(cleaned);
		cleaned = cleaned.trim();

		QualityAssessment quality = assessQuality(cleaned);
		String contentHash = computeHash(cleaned);

		log.debug("Cleaning: {} -> {} chars, quality={}", originalLength, cleaned.length(), quality.score());
		return new CleaningResult(cleaned, contentHash, quality, originalLength, cleaned.length());
	}

	private String normalizeUnicode(String text) {
		return text
				.replace('\u2018', '\'').replace('\u2019', '\'')
				.replace('\u201C', '"').replace('\u201D', '"')
				.replace('\u2013', '-').replace('\u2014', '-')
				.replace("\u2026", "...")
				.replace('\u00A0', ' ');
	}

	private String normalizeWhitespace(String text) {
		text = MULTIPLE_SPACES.matcher(text).replaceAll(" ");
		text = MULTIPLE_NEWLINES.matcher(text).replaceAll("\n\n");
		return text;
	}

	private String removeBoilerplate(String text) {
		text = PAGE_NUMBERS.matcher(text).replaceAll("");
		text = HEADERS_FOOTERS.matcher(text).replaceAll("");
		return text;
	}

	/**
	 * Assess content quality on a 0-1 scale.
	 */
	public QualityAssessment assessQuality(String text) {
		if (text.length() < 50) {
			return new QualityAssessment(0.1, "TOO_SHORT", "Content is too short for meaningful chunking");
		}

		double uniqueWordRatio = computeUniqueWordRatio(text);
		if (uniqueWordRatio < 0.2) {
			return new QualityAssessment(0.2, "TOO_REPETITIVE", "Content has very low vocabulary diversity");
		}

		double avgSentenceLength = computeAvgSentenceLength(text);
		if (avgSentenceLength > 200) {
			return new QualityAssessment(0.5, "POOR_STRUCTURE", "Sentences are extremely long, may chunk poorly");
		}

		double score = Math.min(1.0, 0.3 + (uniqueWordRatio * 0.4) + (Math.min(avgSentenceLength, 40) / 40.0 * 0.3));
		return new QualityAssessment(score, "ACCEPTABLE", null);
	}

	public String computeHash(String text) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (Exception e) {
			return String.valueOf(text.hashCode());
		}
	}

	private double computeUniqueWordRatio(String text) {
		String[] words = text.toLowerCase().split("\\W+");
		if (words.length == 0) return 0;
		long unique = java.util.Arrays.stream(words).distinct().count();
		return (double) unique / words.length;
	}

	private double computeAvgSentenceLength(String text) {
		String[] sentences = text.split("[.!?]+");
		if (sentences.length == 0) return 0;
		return (double) text.length() / sentences.length;
	}

	public record CleaningResult(
			String cleanedText, String contentHash,
			QualityAssessment quality, int originalLength, int cleanedLength
	) {}

	public record QualityAssessment(double score, String grade, String issue) {}
}

package com.hs.spring_ai_research.rag;

import java.util.List;


import org.springframework.stereotype.Service;

import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;

/**
 * Pre-retrieval metadata filtering for RAG.
 *
 * Filters text segments by metadata attributes BEFORE they reach the retrieval stage.
 * This is critical in multi-tenant systems where:
 * - Tenant A must never see Tenant B's documents
 * - Time-sensitive queries should only search recent documents
 * - Category filters narrow results (e.g., "only policy documents")
 *
 * In production with PGVector, these filters translate to SQL WHERE clauses
 * applied alongside the vector similarity search, making filtering efficient.
 * This implementation works as a post-retrieval filter for the in-memory BM25 index
 * and demonstrates the concept.
 */
@Slf4j
@Service
public class MetadataFilter {

	/**
	 * Filter segments by category metadata.
	 */
	public List<TextSegment> filterByCategory(List<TextSegment> segments, String category) {
		if (category == null || category.isBlank()) return segments;

		List<TextSegment> filtered = segments.stream()
				.filter(seg -> category.equalsIgnoreCase(getMetadata(seg, "category")))
				.toList();

		log.debug("Category filter '{}': {} -> {} segments", category, segments.size(), filtered.size());
		return filtered;
	}

	/**
	 * Filter segments by tenant ID for multi-tenant isolation.
	 */
	public List<TextSegment> filterByTenant(List<TextSegment> segments, String tenantId) {
		if (tenantId == null || tenantId.isBlank()) return segments;

		List<TextSegment> filtered = segments.stream()
				.filter(seg -> {
					String segTenant = getMetadata(seg, "tenant_id");
					return segTenant == null || tenantId.equals(segTenant);
				})
				.toList();

		log.debug("Tenant filter '{}': {} -> {} segments", tenantId, segments.size(), filtered.size());
		return filtered;
	}

	/**
	 * Filter segments by source type (e.g., "pdf", "manual", "faq").
	 */
	public List<TextSegment> filterBySource(List<TextSegment> segments, String sourceType) {
		if (sourceType == null || sourceType.isBlank()) return segments;

		List<TextSegment> filtered = segments.stream()
				.filter(seg -> {
					String source = getMetadata(seg, "source");
					return source != null && source.toLowerCase().contains(sourceType.toLowerCase());
				})
				.toList();

		log.debug("Source filter '{}': {} -> {} segments", sourceType, segments.size(), filtered.size());
		return filtered;
	}

	/**
	 * Filter segments ingested after a specific timestamp.
	 */
	public List<TextSegment> filterByTimeAfter(List<TextSegment> segments, long afterTimestamp) {
		List<TextSegment> filtered = segments.stream()
				.filter(seg -> {
					String ingested = getMetadata(seg, "ingested_at");
					if (ingested == null) return true;
					try {
						return Long.parseLong(ingested) >= afterTimestamp;
					} catch (NumberFormatException e) {
						return true;
					}
				})
				.toList();

		log.debug("Time filter (after {}): {} -> {} segments", afterTimestamp, segments.size(), filtered.size());
		return filtered;
	}

	/**
	 * Apply multiple filters in sequence. Each filter narrows the result set.
	 */
	public List<TextSegment> applyFilters(List<TextSegment> segments, FilterCriteria criteria) {
		List<TextSegment> result = segments;

		if (criteria.tenantId() != null) {
			result = filterByTenant(result, criteria.tenantId());
		}
		if (criteria.category() != null) {
			result = filterByCategory(result, criteria.category());
		}
		if (criteria.sourceType() != null) {
			result = filterBySource(result, criteria.sourceType());
		}
		if (criteria.afterTimestamp() > 0) {
			result = filterByTimeAfter(result, criteria.afterTimestamp());
		}

		log.info("Applied filters: {} -> {} segments", segments.size(), result.size());
		return result;
	}

	private String getMetadata(TextSegment segment, String key) {
		try {
			return segment.metadata().getString(key);
		} catch (Exception e) {
			return null;
		}
	}

	public record FilterCriteria(
			String tenantId,
			String category,
			String sourceType,
			long afterTimestamp
	) {
		public static FilterCriteria none() {
			return new FilterCriteria(null, null, null, 0);
		}

		public static FilterCriteria forTenant(String tenantId) {
			return new FilterCriteria(tenantId, null, null, 0);
		}

		public static FilterCriteria forCategory(String category) {
			return new FilterCriteria(null, category, null, 0);
		}
	}
}

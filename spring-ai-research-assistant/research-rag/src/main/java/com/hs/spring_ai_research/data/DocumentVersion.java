package com.hs.spring_ai_research.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tracks every ingested document with version history.
 *
 * This is the "data engineering" layer that most AI demos skip.
 * In production, you need to know:
 * - WHAT was ingested (source, hash)
 * - WHEN it was ingested (timestamps)
 * - WITH WHAT model it was embedded (embedding model version)
 * - HOW MANY chunks it produced
 * - IS IT CURRENT or has it been superseded by a newer version
 *
 * Without this, you can't answer: "why is my RAG returning stale info?"
 * or "which documents need re-embedding after model upgrade?"
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "document_versions")
public class DocumentVersion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String source;

	@Column(nullable = false)
	private String contentHash;

	private int version;

	private String embeddingModel;

	private int chunkCount;

	private String category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private VersionStatus status;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant supersededAt;

	private long originalSizeBytes;

	private long cleanedSizeBytes;

	public enum VersionStatus {
		ACTIVE,
		SUPERSEDED,
		REINDEXING,
		FAILED
	}
}

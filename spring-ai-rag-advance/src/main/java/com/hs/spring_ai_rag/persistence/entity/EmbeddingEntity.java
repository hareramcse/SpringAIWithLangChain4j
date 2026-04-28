package com.hs.spring_ai_rag.persistence.entity;

import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps the Flyway-created LangChain4j embedding table used by {@code PgVectorEmbeddingStore}.
 * Table name defaults to {@code langchain4j_embeddings}; keep aligned with {@code app.pgvector.table}.
 * The generated {@code text_tsv} column is not mapped — Postgres maintains it.
 * <p>
 * Bulk inserts during ingest still go through LangChain4j's {@code EmbeddingStore}; use this entity for
 * typed reads/writes elsewhere (e.g. maintenance jobs) without duplicating ingest unless you replace that path.
 */
@Entity
@Table(name = "langchain4j_embeddings")
@Getter
@Setter
@NoArgsConstructor
public class EmbeddingEntity {

	@Id
	@Column(name = "embedding_id", nullable = false, updatable = false)
	private UUID embeddingId;

	/**
	 * pgvector column; dimension must match {@code app.pgvector.dimension} (default 512).
	 */
	@Column(name = "embedding", nullable = false, columnDefinition = "vector(512)")
	@JdbcTypeCode(SqlTypes.VECTOR)
	private float[] embedding;

	@Column(columnDefinition = "text")
	private String text;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "json")
	private Map<String, Object> metadata;

	public EmbeddingEntity(UUID embeddingId, float[] embedding, String text, Map<String, Object> metadata) {
		this.embeddingId = embeddingId;
		this.embedding = embedding;
		this.text = text;
		this.metadata = metadata;
	}
}

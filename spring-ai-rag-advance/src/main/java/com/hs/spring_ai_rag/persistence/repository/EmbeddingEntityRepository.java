package com.hs.spring_ai_rag.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hs.spring_ai_rag.persistence.entity.EmbeddingEntity;

/**
 * CRUD for embedding rows; optional for admin or migration tooling — ingest uses LangChain4j {@code EmbeddingStore}.
 */
public interface EmbeddingEntityRepository extends JpaRepository<EmbeddingEntity, UUID> {
}

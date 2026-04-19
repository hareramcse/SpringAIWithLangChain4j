package com.hs.spring_ai_research.data;

import java.util.List;
import java.util.Optional;


import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

	List<DocumentVersion> findByStatus(DocumentVersion.VersionStatus status);

	Optional<DocumentVersion> findBySourceAndStatus(String source, DocumentVersion.VersionStatus status);

	List<DocumentVersion> findByEmbeddingModelNot(String embeddingModel);

	boolean existsByContentHash(String contentHash);
}

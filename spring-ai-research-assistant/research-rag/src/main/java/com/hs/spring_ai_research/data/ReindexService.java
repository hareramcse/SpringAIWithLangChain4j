package com.hs.spring_ai_research.data;

import java.util.List;


import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles re-indexing when the embedding model changes.
 *
 * When you upgrade from text-embedding-3-small to a new model:
 * 1. Mark existing versions as REINDEXING
 * 2. Re-embed all documents with the new model
 * 3. Validate the new index quality
 * 4. If quality is acceptable, mark old versions as SUPERSEDED
 * 5. If quality drops, roll back to the old embeddings
 *
 * This is the "embedding lifecycle" that production systems need.
 * Without it, model upgrades require manual re-ingestion of all documents.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReindexService {

	private final DocumentVersionRepository versionRepo;
	private final DataPipelineService dataPipelineService;

	/**
	 * Find documents that need re-indexing (embedded with an old model).
	 */
	public List<DocumentVersion> findStaleDocuments(String currentModel) {
		return versionRepo.findByEmbeddingModelNot(currentModel);
	}

	/**
	 * Trigger re-indexing for all documents using a different embedding model.
	 * In this POC, we mark them as needing reindex — actual re-embedding would
	 * require re-fetching the original content.
	 */
	public ReindexReport triggerReindex(String currentModel) {
		List<DocumentVersion> stale = findStaleDocuments(currentModel);

		if (stale.isEmpty()) {
			return new ReindexReport(0, 0, 0, "All documents are up-to-date with model: " + currentModel);
		}

		int marked = 0;
		int skipped = 0;

		for (DocumentVersion doc : stale) {
			if (doc.getStatus() == DocumentVersion.VersionStatus.ACTIVE) {
				doc.setStatus(DocumentVersion.VersionStatus.REINDEXING);
				versionRepo.save(doc);
				marked++;
				log.info("Marked for reindex: source='{}' (v{}), old model='{}'",
						doc.getSource(), doc.getVersion(), doc.getEmbeddingModel());
			} else {
				skipped++;
			}
		}

		return new ReindexReport(stale.size(), marked, skipped,
				"Reindex initiated. " + marked + " documents marked for re-embedding with model: " + currentModel);
	}

	/**
	 * Get the current state of the document index.
	 */
	public IndexHealth getIndexHealth(String currentModel) {
		List<DocumentVersion> all = versionRepo.findByStatus(DocumentVersion.VersionStatus.ACTIVE);
		long upToDate = all.stream()
				.filter(d -> currentModel.equals(d.getEmbeddingModel()))
				.count();
		long stale = all.size() - upToDate;

		String status = stale == 0 ? "HEALTHY"
				: stale < all.size() / 2 ? "PARTIALLY_STALE" : "NEEDS_REINDEX";

		return new IndexHealth(all.size(), upToDate, stale, currentModel, status);
	}

	public record ReindexReport(int totalFound, int markedForReindex, int skipped, String message) {}

	public record IndexHealth(long totalDocuments, long upToDate, long stale,
							  String currentModel, String status) {}
}

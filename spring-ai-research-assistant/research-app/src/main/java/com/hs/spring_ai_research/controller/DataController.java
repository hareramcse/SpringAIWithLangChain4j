package com.hs.spring_ai_research.controller;

import java.util.List;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_research.data.DataPipelineService;
import com.hs.spring_ai_research.data.DocumentVersion;
import com.hs.spring_ai_research.data.DocumentVersionRepository;
import com.hs.spring_ai_research.data.ReindexService;
import com.hs.spring_ai_research.dto.IngestTextRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Production-grade data pipeline endpoints — ingest, version, and reindex documents.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code POST /api/data/ingest} — ingest text through the full pipeline (clean, dedupe, chunk, embed, version)</li>
 *   <li>{@code GET  /api/data/versions} — list document versions (optionally filter by status)</li>
 *   <li>{@code POST /api/data/reindex} — trigger re-indexing when embedding model changes</li>
 *   <li>{@code GET  /api/data/index-health} — check for stale embeddings</li>
 *   <li>{@code GET  /api/data/pipeline-stats} — ingestion pipeline statistics</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataController {

	private final DataPipelineService pipelineService;
	private final ReindexService reindexService;
	private final DocumentVersionRepository versionRepo;

	@PostMapping("/ingest")
	public ResponseEntity<?> ingest(@Valid @RequestBody IngestTextRequest request) {
		log.info("Data pipeline ingest: source='{}'", request.source());
		DataPipelineService.PipelineResult result = pipelineService.ingest(
				request.text(), request.source(), request.category());
		return ResponseEntity.ok(result);
	}

	@GetMapping("/versions")
	public ResponseEntity<List<DocumentVersion>> listVersions(
			@RequestParam(required = false) String status) {
		if (status != null) {
			return ResponseEntity.ok(versionRepo.findByStatus(
					DocumentVersion.VersionStatus.valueOf(status.toUpperCase())));
		}
		return ResponseEntity.ok(versionRepo.findAll());
	}

	@PostMapping("/reindex")
	public ResponseEntity<?> triggerReindex(
			@RequestParam(defaultValue = "text-embedding-3-small") String currentModel) {
		log.info("Triggering reindex for model: {}", currentModel);
		ReindexService.ReindexReport report = reindexService.triggerReindex(currentModel);
		return ResponseEntity.ok(report);
	}

	@GetMapping("/index-health")
	public ResponseEntity<?> getIndexHealth(
			@RequestParam(defaultValue = "text-embedding-3-small") String currentModel) {
		return ResponseEntity.ok(reindexService.getIndexHealth(currentModel));
	}

	@GetMapping("/pipeline-stats")
	public ResponseEntity<?> getPipelineStats() {
		return ResponseEntity.ok(pipelineService.getStats());
	}
}

package com.hs.spring_ai_research.controller;

import java.util.Map;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hs.spring_ai_research.dto.IngestTextRequest;
import com.hs.spring_ai_research.service.DocumentIngestionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Basic document ingestion endpoints — upload files or raw text into the knowledge base.
 *
 * <p>For production-grade ingestion with cleaning, deduplication, and versioning,
 * use the {@code /api/data} endpoints (backed by {@link com.hs.spring_ai_research.data.DataPipelineService}).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

	private final DocumentIngestionService ingestionService;

	/** Uploads a file (PDF, DOCX, TXT) and ingests it into the vector store. */
	@PostMapping("/upload")
	public ResponseEntity<?> uploadDocument(
			@RequestParam("file") MultipartFile file,
			@RequestParam(value = "category", defaultValue = "general") String category) {
		log.info("Document upload: name={}, size={}, category={}", 
				file.getOriginalFilename(), file.getSize(), category);

		int chunks = ingestionService.ingestFile(file, category);

		return ResponseEntity.ok(Map.of(
				"message", "Document ingested successfully",
				"filename", file.getOriginalFilename(),
				"chunksCreated", chunks,
				"category", category
		));
	}

	/**
	 * Ingest raw text with metadata directly into the knowledge base.
	 */
	@PostMapping("/ingest-text")
	public ResponseEntity<?> ingestText(@Valid @RequestBody IngestTextRequest request) {
		log.info("Text ingestion: source={}, category={}", request.source(), request.category());

		int chunks = ingestionService.ingestText(
				request.text(), request.source(), request.category());

		return ResponseEntity.ok(Map.of(
				"message", "Text ingested successfully",
				"source", request.source(),
				"chunksCreated", chunks,
				"category", request.category()
		));
	}
}

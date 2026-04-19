package com.hs.spring_ai_help_desk.controller;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hs.spring_ai_help_desk.dto.IngestTextRequest;
import com.hs.spring_ai_help_desk.service.KnowledgeBaseService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
public class KnowledgeBaseController {

	private final KnowledgeBaseService knowledgeBaseService;

	@GetMapping("/search")
	public ResponseEntity<Map<String, Object>> search(
			@RequestParam String query,
			@RequestParam(defaultValue = "3") int maxResults) {
		String results = knowledgeBaseService.search(query, maxResults);
		return ResponseEntity.ok(Map.of(
				"query", query,
				"results", results));
	}

	@PostMapping("/ingest/text")
	public ResponseEntity<Map<String, Object>> ingestText(@Valid @RequestBody IngestTextRequest request) {
		var segments = knowledgeBaseService.ingestText(request.getContent(), request.getSourceName());
		return ResponseEntity.ok(Map.of(
				"source", request.getSourceName(),
				"segmentsCreated", segments.size(),
				"message", "Text ingested successfully"));
	}

	@PostMapping(value = "/ingest/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Map<String, Object>> ingestFile(@RequestParam("file") MultipartFile file) {
		if (file.isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
		}

		String filename = file.getOriginalFilename();
		var resource = file.getResource();
		var segments = knowledgeBaseService.ingestResource(resource, filename);

		return ResponseEntity.ok(Map.of(
				"source", filename != null ? filename : "uploaded-file",
				"segmentsCreated", segments.size(),
				"message", "File ingested successfully"));
	}

	@PostMapping("/reload")
	public ResponseEntity<Map<String, String>> reloadKnowledgeBase() {
		knowledgeBaseService.loadDefaultKnowledgeBase();
		return ResponseEntity.ok(Map.of("message", "Knowledge base reload triggered"));
	}

}

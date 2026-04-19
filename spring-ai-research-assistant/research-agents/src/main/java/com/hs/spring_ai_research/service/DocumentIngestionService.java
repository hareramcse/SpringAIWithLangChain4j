package com.hs.spring_ai_research.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hs.spring_ai_research.rag.ChunkingService;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Basic document ingestion: parse → chunk → embed → store.
 *
 * <p>Supports PDF, DOCX, PPTX, HTML, TXT via Apache Tika. For production-grade
 * ingestion with cleaning, deduplication, and versioning, use
 * {@link com.hs.spring_ai_research.data.DataPipelineService} instead.</p>
 *
 * <p>This simpler service is kept for the {@code /api/documents} endpoints which
 * demonstrate the basic ingestion pattern.</p>
 */
@Slf4j
@Service
public class DocumentIngestionService {

	private final ChunkingService chunkingService;
	private final EmbeddingModel embeddingModel;
	private final EmbeddingStore<TextSegment> embeddingStore;
	private final ApacheTikaDocumentParser documentParser;

	public DocumentIngestionService(
			ChunkingService chunkingService,
			EmbeddingModel embeddingModel,
			@Qualifier("researchEmbeddingStore") EmbeddingStore<TextSegment> embeddingStore) {
		this.chunkingService = chunkingService;
		this.embeddingModel = embeddingModel;
		this.embeddingStore = embeddingStore;
		this.documentParser = new ApacheTikaDocumentParser();
	}

	// ── Public API ──────────────────────────────────────────────────────────────

	/**
	 * Ingests an uploaded file: parses with Tika, chunks, embeds, stores in PGVector.
	 *
	 * @return number of chunks created
	 */
	public int ingestFile(MultipartFile file, String category) {
		try (InputStream is = file.getInputStream()) {
			Document document = documentParser.parse(is);

			document.metadata().put("source", file.getOriginalFilename());
			document.metadata().put("category", category);
			document.metadata().put("content_type", file.getContentType());
			document.metadata().put("ingested_at", String.valueOf(System.currentTimeMillis()));

			List<TextSegment> chunks = chunkingService.chunkDocument(document);
			storeChunks(chunks);

			log.info("Ingested file '{}': {} chunks created", file.getOriginalFilename(), chunks.size());
			return chunks.size();
		} catch (IOException e) {
			throw new RuntimeException("Failed to ingest file: " + file.getOriginalFilename(), e);
		}
	}

	/** Ingests raw text with source/category metadata. Returns chunk count. */
	public int ingestText(String text, String source, String category) {
		Map<String, String> metadata = Map.of(
				"source", source,
				"category", category,
				"ingested_at", String.valueOf(System.currentTimeMillis())
		);

		List<TextSegment> chunks = chunkingService.chunkTextWithMetadata(text, metadata);
		storeChunks(chunks);

		log.info("Ingested text from '{}': {} chunks created", source, chunks.size());
		return chunks.size();
	}

	/** Ingests a classpath resource (e.g. bundled knowledge base files at startup). */
	public int ingestClasspathResource(String resourcePath, String category) {
		try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
			if (is == null) {
				log.warn("Resource not found: {}", resourcePath);
				return 0;
			}
			Document document = documentParser.parse(is);
			document.metadata().put("source", resourcePath);
			document.metadata().put("category", category);

			List<TextSegment> chunks = chunkingService.chunkDocument(document);
			storeChunks(chunks);

			log.info("Ingested classpath resource '{}': {} chunks", resourcePath, chunks.size());
			return chunks.size();
		} catch (IOException e) {
			throw new RuntimeException("Failed to ingest resource: " + resourcePath, e);
		}
	}

	// ── Private helpers ─────────────────────────────────────────────────────────

	/** Embeds each chunk and stores the vector + text in PGVector. */
	private void storeChunks(List<TextSegment> chunks) {
		for (TextSegment chunk : chunks) {
			Embedding embedding = embeddingModel.embed(chunk.text()).content();
			embeddingStore.add(embedding, chunk);
		}
	}
}

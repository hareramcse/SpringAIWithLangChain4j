package com.hs.spring_ai_research.data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.hs.spring_ai_research.rag.BM25Retriever;
import com.hs.spring_ai_research.rag.ChunkingService;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Full data ingestion pipeline: validate -> clean -> deduplicate -> chunk -> embed -> store -> version.
 *
 * This replaces direct calls to DocumentIngestionService with a structured pipeline
 * that ensures data quality, tracks versions, and maintains the BM25 index.
 *
 * The pipeline stages:
 * 1. Validate — check input is non-empty and meets minimum quality
 * 2. Clean — normalize text, remove boilerplate, assess quality
 * 3. Deduplicate — check content hash to prevent duplicate ingestion
 * 4. Chunk — split into segments with metadata
 * 5. Embed — generate vector embeddings for each chunk
 * 6. Store — persist in vector store + BM25 index
 * 7. Version — create a DocumentVersion record for tracking
 */
@Slf4j
@Service
public class DataPipelineService {

	private final DataCleaningService cleaningService;
	private final ChunkingService chunkingService;
	private final EmbeddingModel embeddingModel;
	private final EmbeddingStore<TextSegment> embeddingStore;
	private final BM25Retriever bm25Retriever;
	private final DocumentVersionRepository versionRepo;

	private final AtomicLong totalIngested = new AtomicLong(0);
	private final AtomicLong totalDeduplicated = new AtomicLong(0);
	private final AtomicLong totalRejected = new AtomicLong(0);

	public DataPipelineService(
			DataCleaningService cleaningService,
			ChunkingService chunkingService,
			EmbeddingModel embeddingModel,
			@Qualifier("researchEmbeddingStore") EmbeddingStore<TextSegment> embeddingStore,
			BM25Retriever bm25Retriever,
			DocumentVersionRepository versionRepo) {
		this.cleaningService = cleaningService;
		this.chunkingService = chunkingService;
		this.embeddingModel = embeddingModel;
		this.embeddingStore = embeddingStore;
		this.bm25Retriever = bm25Retriever;
		this.versionRepo = versionRepo;
	}

	/**
	 * Run the full ingestion pipeline.
	 */
	public PipelineResult ingest(String text, String source, String category) {
		log.info("Data pipeline started for source: '{}'", source);

		// Stage 1: Validate
		if (text == null || text.isBlank()) {
			totalRejected.incrementAndGet();
			return new PipelineResult(false, "REJECTED", 0, "Empty input");
		}

		// Stage 2: Clean
		DataCleaningService.CleaningResult cleaned = cleaningService.clean(text);
		if (cleaned.quality().score() < 0.3) {
			totalRejected.incrementAndGet();
			log.warn("Content rejected due to low quality: {} ({})", cleaned.quality().grade(), cleaned.quality().issue());
			return new PipelineResult(false, "REJECTED", 0,
					"Low quality: " + cleaned.quality().grade() + " — " + cleaned.quality().issue());
		}

		// Stage 3: Deduplicate
		if (versionRepo.existsByContentHash(cleaned.contentHash())) {
			totalDeduplicated.incrementAndGet();
			log.info("Duplicate content detected for source '{}', skipping", source);
			return new PipelineResult(false, "DUPLICATE", 0, "Content already exists in the knowledge base");
		}

		// Stage 4: Chunk
		Map<String, String> metadata = Map.of(
				"source", source,
				"category", category != null ? category : "general",
				"ingested_at", String.valueOf(System.currentTimeMillis())
		);
		List<TextSegment> chunks = chunkingService.chunkTextWithMetadata(cleaned.cleanedText(), metadata);

		// Stage 5: Embed + Stage 6: Store
		for (TextSegment chunk : chunks) {
			Embedding embedding = embeddingModel.embed(chunk.text()).content();
			embeddingStore.add(embedding, chunk);
		}

		// Also add to BM25 index for hybrid retrieval
		bm25Retriever.addToIndex(chunks);

		// Stage 7: Version
		int version = versionRepo.findBySourceAndStatus(source, DocumentVersion.VersionStatus.ACTIVE)
				.map(v -> {
					v.setStatus(DocumentVersion.VersionStatus.SUPERSEDED);
					v.setSupersededAt(Instant.now());
					versionRepo.save(v);
					return v.getVersion() + 1;
				})
				.orElse(1);

		versionRepo.save(DocumentVersion.builder()
				.source(source)
				.contentHash(cleaned.contentHash())
				.version(version)
				.embeddingModel("text-embedding-3-small")
				.chunkCount(chunks.size())
				.category(category != null ? category : "general")
				.status(DocumentVersion.VersionStatus.ACTIVE)
				.createdAt(Instant.now())
				.originalSizeBytes(text.length())
				.cleanedSizeBytes(cleaned.cleanedText().length())
				.build());

		totalIngested.incrementAndGet();
		log.info("Pipeline complete: source='{}', version={}, chunks={}", source, version, chunks.size());
		return new PipelineResult(true, "INGESTED", chunks.size(),
				"Version " + version + ", quality=" + String.format("%.2f", cleaned.quality().score()));
	}

	public PipelineStats getStats() {
		return new PipelineStats(
				totalIngested.get(), totalDeduplicated.get(), totalRejected.get(),
				versionRepo.findByStatus(DocumentVersion.VersionStatus.ACTIVE).size(),
				bm25Retriever.getIndexSize()
		);
	}

	public record PipelineResult(boolean success, String status, int chunks, String message) {}

	public record PipelineStats(
			long totalIngested, long totalDeduplicated, long totalRejected,
			int activeDocuments, int bm25IndexSize
	) {}
}

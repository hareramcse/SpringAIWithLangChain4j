package com.hs.spring_ai_research.evaluation;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads gold-standard evaluation datasets from JSON files in resources/gold-datasets/.
 *
 * Gold datasets are real-world queries with known correct answers. They provide
 * the ground truth needed for:
 * - Regression testing (did my prompt change break anything?)
 * - A/B testing (is prompt version B better than A?)
 * - Metrics computation (correctness, faithfulness, retrieval quality)
 *
 * In production, these datasets grow organically from user feedback,
 * manual curation, and annotated production traffic.
 */
@Slf4j
@Service
public class GoldDatasetLoader {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final List<GoldDataset> datasets = new ArrayList<>();

	@PostConstruct
	public void loadDatasets() {
		try {
			PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
			Resource[] resources = resolver.getResources("classpath:gold-datasets/*.json");

			for (Resource resource : resources) {
				try (InputStream is = resource.getInputStream()) {
					List<GoldDataset> loaded = objectMapper.readValue(is, new TypeReference<>() {});
					datasets.addAll(loaded);
					log.info("Loaded {} gold dataset entries from {}", loaded.size(), resource.getFilename());
				}
			}
			log.info("Total gold dataset entries loaded: {}", datasets.size());
		} catch (IOException e) {
			log.warn("Failed to load gold datasets: {}", e.getMessage());
		}
	}

	public List<GoldDataset> getAll() {
		return Collections.unmodifiableList(datasets);
	}

	public List<GoldDataset> getByTag(String tag) {
		return datasets.stream()
				.filter(d -> d.tags() != null && d.tags().contains(tag))
				.toList();
	}

	public int size() {
		return datasets.size();
	}
}

package com.hs.spring_ai_research.dx;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Central prompt management for team-based AI development.
 *
 * Instead of prompts scattered across code as string literals:
 * - All prompts live in files under resources/prompts/
 * - Prompts can be versioned (researcher-system-v1.txt, researcher-system-v2.txt)
 * - Variable interpolation: {question}, {context} placeholders
 * - Runtime-switchable: change prompts without redeployment (in production, via config service)
 *
 * This answers the DX question: "how do team members define and modify prompts?"
 * Answer: they edit text files, version them, and test with PromptTestHarness.
 */
@Slf4j
@Service
public class PromptRegistry {

	private final Map<String, String> prompts = new ConcurrentHashMap<>();
	private final Map<String, String> activeVersions = new ConcurrentHashMap<>();

	@PostConstruct
	public void loadPrompts() {
		try {
			PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
			Resource[] resources = resolver.getResources("classpath:prompts/*.txt");

			for (Resource resource : resources) {
				String filename = resource.getFilename();
				if (filename == null) continue;
				String key = filename.replace(".txt", "");

				try (InputStream is = resource.getInputStream()) {
					String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
					prompts.put(key, content);
					activeVersions.put(baseKey(key), key);
					log.info("Loaded prompt: {} ({} chars)", key, content.length());
				}
			}
			log.info("Prompt registry loaded {} prompts", prompts.size());
		} catch (IOException e) {
			log.warn("Failed to load prompts: {}", e.getMessage());
		}
	}

	/**
	 * Get a prompt by name. Returns the active version if multiple exist.
	 */
	public String getPrompt(String name) {
		String activeKey = activeVersions.getOrDefault(name, name);
		return prompts.get(activeKey);
	}

	/**
	 * Get a prompt with variables interpolated.
	 */
	public String getPrompt(String name, Map<String, String> variables) {
		String template = getPrompt(name);
		if (template == null) return null;

		String result = template;
		for (Map.Entry<String, String> var : variables.entrySet()) {
			result = result.replace("{" + var.getKey() + "}", var.getValue());
		}
		return result;
	}

	/**
	 * Register a prompt dynamically (for testing or runtime updates).
	 */
	public void registerPrompt(String name, String content) {
		prompts.put(name, content);
		activeVersions.put(baseKey(name), name);
		log.info("Prompt registered: {} ({} chars)", name, content.length());
	}

	/**
	 * Switch the active version of a prompt.
	 */
	public boolean setActiveVersion(String name, String version) {
		String key = name + "-" + version;
		if (prompts.containsKey(key)) {
			activeVersions.put(name, key);
			log.info("Active prompt version switched: {} -> {}", name, key);
			return true;
		}
		return false;
	}

	public Set<String> listPrompts() {
		return Collections.unmodifiableSet(prompts.keySet());
	}

	public Map<String, String> getActiveVersions() {
		return Collections.unmodifiableMap(activeVersions);
	}

	private String baseKey(String key) {
		return key.replaceAll("-v\\d+$", "");
	}
}

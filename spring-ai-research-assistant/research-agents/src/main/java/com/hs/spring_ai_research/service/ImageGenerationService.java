package com.hs.spring_ai_research.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openai.OpenAiImageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

/**
 * CONCEPT: Image Generation (DALL-E)
 *
 * Generates images from text descriptions using OpenAI's DALL-E model.
 * This demonstrates the "generation" side of multimodal AI (vs. the "understanding"
 * side shown in DocumentAnalysisTool which uses GPT-4o vision).
 *
 * Use cases in AI engineering:
 * - Generating diagrams/illustrations for research reports
 * - Creating visual summaries of data
 * - Producing placeholder images for content generation pipelines
 *
 * Cost note: DALL-E 3 costs ~$0.04 per image (1024x1024).
 * For POC purposes, we use dall-e-2 at ~$0.02 per image (smaller, cheaper).
 */
@Slf4j
@Service
public class ImageGenerationService {

	private final ImageModel imageModel;

	public ImageGenerationService(
			@Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey) {
		this.imageModel = OpenAiImageModel.builder()
				.apiKey(apiKey)
				.modelName("dall-e-2")
				.size("512x512")
				.build();
	}

	public ImageResult generateImage(String prompt) {
		log.info("Generating image for prompt: '{}'", truncate(prompt, 80));
		Response<Image> response = imageModel.generate(prompt);
		Image image = response.content();

		String imageUrl = image.url() != null ? image.url().toString() : null;
		String revisedPrompt = image.revisedPrompt();

		log.info("Image generated: url={}", imageUrl != null ? "yes" : "base64");
		return new ImageResult(imageUrl, image.base64Data(), revisedPrompt);
	}

	/**
	 * Generate a diagram/illustration for a research report section.
	 */
	public ImageResult generateResearchIllustration(String topic) {
		String enhancedPrompt = "A clean, professional diagram or illustration about: "
				+ topic + ". Minimalist style, suitable for a research report.";
		return generateImage(enhancedPrompt);
	}

	private String truncate(String text, int maxLength) {
		return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
	}

	public record ImageResult(String url, String base64Data, String revisedPrompt) {}
}

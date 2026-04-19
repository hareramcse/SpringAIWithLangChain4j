package com.hs.spring_ai_research.tools;

import java.io.IOException;
import java.util.Base64;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * Multimodal document analysis using GPT-4o vision.
 *
 * Analyzes images (screenshots, diagrams, charts, handwritten notes)
 * and extracts textual information that can be added to the knowledge base.
 *
 * Demonstrates multimodal AI capabilities — a key differentiator
 * in modern AI engineering.
 */
@Slf4j
@Component
public class DocumentAnalysisTool {

	private final ChatModel visionModel;

	public DocumentAnalysisTool(
			@Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey) {
		this.visionModel = OpenAiChatModel.builder()
				.apiKey(apiKey)
				.modelName("gpt-4o")
				.maxTokens(2000)
				.build();
	}

	/**
	 * Analyze an uploaded image and extract information.
	 */
	public String analyzeImage(MultipartFile imageFile, String analysisPrompt) throws IOException {
		log.info("Analyzing image: {} ({} bytes)", imageFile.getOriginalFilename(), imageFile.getSize());

		String base64Image = Base64.getEncoder().encodeToString(imageFile.getBytes());
		String mimeType = imageFile.getContentType() != null
				? imageFile.getContentType() : "image/png";

		UserMessage userMessage = UserMessage.from(
				TextContent.from(analysisPrompt != null ? analysisPrompt :
						"Analyze this image in detail. Extract all text, describe diagrams, "
								+ "and summarize the key information shown."),
				ImageContent.from(base64Image, mimeType)
		);

		ChatResponse response = visionModel.chat(userMessage);
		String analysis = response.aiMessage().text();

		log.info("Image analysis complete: {} chars extracted", analysis.length());
		return analysis;
	}

	/**
	 * Extract text from a screenshot or document image.
	 */
	public String extractText(MultipartFile imageFile) throws IOException {
		return analyzeImage(imageFile,
				"Extract ALL text from this image exactly as it appears. "
						+ "Preserve formatting and structure. "
						+ "If it's a diagram, describe the relationships shown.");
	}

	/**
	 * Summarize a chart or graph image.
	 */
	public String summarizeChart(MultipartFile imageFile) throws IOException {
		return analyzeImage(imageFile,
				"This image contains a chart or graph. Describe: "
						+ "1) The type of chart, "
						+ "2) The axes and labels, "
						+ "3) The key data points and trends, "
						+ "4) The main takeaway from this visualization.");
	}
}

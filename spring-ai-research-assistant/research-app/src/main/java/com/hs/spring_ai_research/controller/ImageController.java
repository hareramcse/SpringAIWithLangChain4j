package com.hs.spring_ai_research.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_research.service.ImageGenerationService;
import com.hs.spring_ai_research.service.ImageGenerationService.ImageResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CONCEPT: Image Generation (DALL-E 2)
 *
 * <p>Endpoints for generating images from text prompts.</p>
 * <ul>
 *   <li>{@code POST /api/images/generate} — general image generation</li>
 *   <li>{@code POST /api/images/illustration} — diagram/illustration optimized for reports</li>
 * </ul>
 *
 * <p>Cost: ~$0.02 per image (DALL-E 2, 512x512).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

	private final ImageGenerationService imageGenerationService;

	@PostMapping("/generate")
	public ResponseEntity<ImageResult> generateImage(
			@Valid @RequestBody ImageRequest request) {
		log.info("Image generation request: '{}'", request.prompt());
		ImageResult result = imageGenerationService.generateImage(request.prompt());
		return ResponseEntity.ok(result);
	}

	@PostMapping("/illustration")
	public ResponseEntity<ImageResult> generateIllustration(
			@Valid @RequestBody ImageRequest request) {
		log.info("Research illustration request: '{}'", request.prompt());
		ImageResult result = imageGenerationService.generateResearchIllustration(request.prompt());
		return ResponseEntity.ok(result);
	}

	public record ImageRequest(@NotBlank String prompt) {}
}

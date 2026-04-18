package com.hs.spring_ai_media.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.openai.OpenAiAudioTranscriptionModel;

@Configuration
public class MediaConfig {

	@Value("${app.openai.api-key}")
	private String apiKey;

	@Bean
	public AudioTranscriptionModel transcriptionModel() {
		return OpenAiAudioTranscriptionModel.builder()
				.apiKey(apiKey)
				.modelName("whisper-1")
				.build();
	}

	@Bean
	public RestClient openAiRestClient() {
		return RestClient.builder()
				.baseUrl("https://api.openai.com/v1")
				.defaultHeader("Authorization", "Bearer " + apiKey)
				.build();
	}

}

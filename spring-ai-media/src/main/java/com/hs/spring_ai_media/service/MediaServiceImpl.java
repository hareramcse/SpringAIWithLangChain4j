package com.hs.spring_ai_media.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.audio.AudioTranscriptionModel;

@Service
public class MediaServiceImpl implements MediaService {

	@Autowired
	private AudioTranscriptionModel transcriptionModel;

	@Autowired
	private RestClient openAiRestClient;

	@Value("classpath:static/sample2.m4a")
	private Resource inputAudio;

	@Override
	public String convertAudioToText(Resource inputAudio) {
		try (InputStream is = inputAudio.getInputStream()) {
			byte[] audioBytes = is.readAllBytes();
			String base64 = Base64.getEncoder().encodeToString(audioBytes);
			Audio audio = Audio.builder().base64Data(base64).mimeType("audio/mp4").build();
			return transcriptionModel.transcribeToText(audio);
		} catch (IOException e) {
			throw new RuntimeException("Failed to transcribe audio", e);
		}
	}

	@Override
	public byte[] convertTextToAudio(Resource resource) throws IOException {
		String input;
		try (InputStream is = resource.getInputStream()) {
			input = new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}

		Map<String, Object> requestBody = Map.of(
				"model", "tts-1",
				"input", input,
				"voice", "alloy",
				"speed", 1.0,
				"response_format", "mp3"
		);

		return openAiRestClient.post()
				.uri("/audio/speech")
				.contentType(MediaType.APPLICATION_JSON)
				.body(requestBody)
				.retrieve()
				.body(byte[].class);
	}

}

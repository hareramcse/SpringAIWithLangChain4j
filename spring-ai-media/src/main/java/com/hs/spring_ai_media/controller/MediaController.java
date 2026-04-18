package com.hs.spring_ai_media.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hs.spring_ai_media.service.MediaService;

@RestController
public class MediaController {

	@Autowired
	private MediaService mediaService;

	@Value("classpath:static/test.mp3")
	private Resource inputAudio;

	@PostMapping("/transcript")
	public ResponseEntity<String> speechToText(@RequestParam("audioFile") MultipartFile audioFile) {
		String responseText = mediaService.convertAudioToText(audioFile.getResource());
		return ResponseEntity.ok(responseText);
	}

	@PostMapping(value = "/convertTextToAudio", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ResponseEntity<ByteArrayResource> convertTextToAudio(@RequestParam("textFile") MultipartFile textFile)
			throws Exception {
		byte[] audioBytes = mediaService.convertTextToAudio(textFile.getResource());
		ByteArrayResource resource = new ByteArrayResource(audioBytes);

		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=output.mp3")
				.contentType(MediaType.parseMediaType("audio/mpeg")).contentLength(audioBytes.length).body(resource);
	}

}

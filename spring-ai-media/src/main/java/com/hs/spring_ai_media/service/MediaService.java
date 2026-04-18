package com.hs.spring_ai_media.service;

import java.io.IOException;

import org.springframework.core.io.Resource;

public interface MediaService {

	String convertAudioToText(Resource inputAudio);

	byte[] convertTextToAudio(Resource resource) throws IOException;

}

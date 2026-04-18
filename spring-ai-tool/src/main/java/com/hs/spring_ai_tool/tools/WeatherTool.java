package com.hs.spring_ai_tool.tools;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WeatherTool {

	private final RestClient restClient;
	private final String weatherApiKey;

	public WeatherTool(@Value("${app.weather.api-key}") String weatherApiKey) {
		this.weatherApiKey = weatherApiKey;
		this.restClient = RestClient.builder()
				.baseUrl("http://api.weatherapi.com/v1")
				.build();
	}

	@Tool("Get weather information of given city.")
	public String getWeather(@P("city of which we want to get weather information") String city) {
		log.info("Fetching weather for city: {}", city);
		var response = restClient.get()
				.uri(builder -> builder
						.path("/current.json")
						.queryParam("key", weatherApiKey)
						.queryParam("q", city)
						.build())
				.retrieve()
				.body(new ParameterizedTypeReference<Map<String, Object>>() {});
		return response.toString();
	}

}

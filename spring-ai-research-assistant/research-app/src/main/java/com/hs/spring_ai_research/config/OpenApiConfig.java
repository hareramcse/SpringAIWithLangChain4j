package com.hs.spring_ai_research.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * OpenAPI 3 metadata for SpringDoc (Swagger UI). REST controllers are discovered automatically.
 */
@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI researchAssistantOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("AI Research Assistant API")
						.description(
								"Multi-module teaching API: research pipeline, RAG ingestion, evaluation, "
										+ "workflow state machine, safety audit, model strategy, and DX tools. "
										+ "Use Swagger UI to try requests; protect these endpoints in production.")
						.version("0.0.1-SNAPSHOT")
						.license(new License()
								.name("Apache 2.0")
								.url("https://www.apache.org/licenses/LICENSE-2.0")));
	}
}

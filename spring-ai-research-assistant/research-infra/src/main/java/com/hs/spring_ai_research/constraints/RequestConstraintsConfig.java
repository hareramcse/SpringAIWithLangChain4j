package com.hs.spring_ai_research.constraints;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * YAML-driven system constraints configuration.
 *
 * These are the operational boundaries of your AI system:
 * - Latency: "users won't wait more than 10 seconds"
 * - Cost: "each request must cost less than $0.50"
 * - Concurrency: "don't overwhelm the LLM API with more than 5 parallel calls"
 * - Queue: "buffer up to 20 requests before rejecting"
 *
 * In an interview, being able to articulate these constraints shows you think
 * about AI systems as production services, not just notebooks.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.constraints")
public class RequestConstraintsConfig {

	private long p95LatencyMs = 10000;
	private double costCeilingPerRequest = 0.50;
	private int maxConcurrentLlmCalls = 5;
	private int queueCapacity = 20;
}

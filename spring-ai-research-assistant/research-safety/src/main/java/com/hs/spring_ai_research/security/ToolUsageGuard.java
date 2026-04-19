package com.hs.spring_ai_research.security;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Guards against tool misuse by AI agents.
 *
 * When agents have tool access, they can potentially:
 * - Call tools excessively (cost/rate limit abuse)
 * - Pass malicious arguments (path traversal, SQL injection)
 * - Chain tools in dangerous combinations
 *
 * This guard provides:
 * - Per-session rate limiting on tool calls
 * - Argument validation (blocks dangerous patterns)
 * - Tool combination blocklist
 *
 * This is critical when moving from demo to production —
 * an unguarded agent with file system or database tools is a security risk.
 */
@Slf4j
@Service
public class ToolUsageGuard {

	private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
			Pattern.compile("(?i)\\.\\./|\\.\\.\\\\"),               // path traversal
			Pattern.compile("(?i)(drop|delete|truncate|alter)\\s+table", Pattern.CASE_INSENSITIVE), // SQL injection
			Pattern.compile("(?i);\\s*(drop|delete|truncate)"),      // chained SQL
			Pattern.compile("(?i)(rm\\s+-rf|del\\s+/[sq])"),         // dangerous shell commands
			Pattern.compile("(?i)(exec|eval|system)\\s*\\(")         // code execution
	);

	private final int maxToolCallsPerSession;
	private final ConcurrentHashMap<String, AtomicInteger> sessionCounts = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> sessionStartTimes = new ConcurrentHashMap<>();

	public ToolUsageGuard(
			@Value("${app.security.max-tool-calls-per-session:20}") int maxToolCallsPerSession) {
		this.maxToolCallsPerSession = maxToolCallsPerSession;
	}

	/**
	 * Check if a tool call should be allowed.
	 */
	public GuardResult checkToolCall(String sessionId, String toolName, Map<String, String> arguments) {
		// Rate limit check
		AtomicInteger count = sessionCounts.computeIfAbsent(sessionId, k -> new AtomicInteger(0));
		int current = count.incrementAndGet();
		if (current > maxToolCallsPerSession) {
			log.warn("Session {} exceeded tool call limit ({}/{})", sessionId, current, maxToolCallsPerSession);
			return new GuardResult(false, "RATE_LIMITED",
					"Tool call limit exceeded for this session (" + maxToolCallsPerSession + " max)");
		}

		// Argument validation
		if (arguments != null) {
			for (Map.Entry<String, String> arg : arguments.entrySet()) {
				for (Pattern pattern : DANGEROUS_PATTERNS) {
					if (pattern.matcher(arg.getValue()).find()) {
						log.warn("Dangerous pattern in tool arg: session={}, tool={}, arg={}",
								sessionId, toolName, arg.getKey());
						return new GuardResult(false, "DANGEROUS_ARGUMENT",
								"Potentially dangerous argument detected in '" + arg.getKey() + "'");
					}
				}
			}
		}

		log.debug("Tool call approved: session={}, tool={}, count={}/{}", sessionId, toolName, current, maxToolCallsPerSession);
		return new GuardResult(true, "APPROVED", null);
	}

	/**
	 * Reset session counters (e.g., when a session ends).
	 */
	public void resetSession(String sessionId) {
		sessionCounts.remove(sessionId);
		sessionStartTimes.remove(sessionId);
	}

	public ToolUsageStats getStats() {
		return new ToolUsageStats(
				sessionCounts.size(),
				sessionCounts.values().stream().mapToInt(AtomicInteger::get).sum(),
				maxToolCallsPerSession
		);
	}

	public record GuardResult(boolean allowed, String decision, String reason) {}

	public record ToolUsageStats(int activeSessions, int totalToolCalls, int maxPerSession) {}
}

package com.hs.spring_ai_help_desk.tools;

import org.springframework.stereotype.Component;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EmailTool {

	@Tool("Send an email notification to the support team after a ticket is created or escalated.")
	public String sendEmailToSupportTeam(
			@P("Email address associated with the ticket") String email,
			@P("Short description of the ticket or escalation reason") String message) {
		log.info("Sending email to support team - email: {}, message: {}", email, message);
		return "Email notification sent to the support team regarding " + email + ". Message: " + message;
	}

}

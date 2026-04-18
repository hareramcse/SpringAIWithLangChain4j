package com.hs.spring_ai_help_desk.tools;

import org.springframework.stereotype.Component;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EmailTool {

	@Tool("This tool helps to send email to support team once the ticket is created.")
	public String sendEmailToSupportTeam(
			@P("Email id is associated with ticket for contact information.") String email,
			@P("Short description of ticket summary.") String message) {
		log.info("Sending email to support team - email: {}, message: {}", email, message);
		return "Email sent to support team for " + email;
	}

}

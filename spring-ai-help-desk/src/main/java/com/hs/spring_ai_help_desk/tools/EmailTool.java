package com.hs.spring_ai_help_desk.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import org.springframework.stereotype.Component;

@Component
public class EmailTool {

	@Tool("This tool helps to send email to support team once the ticket is created.")
	public String sendEmailToSupportTeam(
			@P("Email id is associated with ticket for contact information.") String email,
			@P("Short description of ticket summary.") String message) {
		System.out.println("going to send email to support team");
		System.out.println("email id : " + email);
		System.out.println("message : " + message);
		return "Email sent to support team for " + email;
	}

}

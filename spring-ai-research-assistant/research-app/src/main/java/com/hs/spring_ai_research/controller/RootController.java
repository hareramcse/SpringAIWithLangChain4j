package com.hs.spring_ai_research.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves no static {@code index.html}; a request to {@code /} would otherwise be handled by the
 * default resource chain and throw {@link org.springframework.web.servlet.resource.NoResourceFoundException}.
 * Redirects the browser to Swagger UI.
 */
@Controller
public class RootController {

	@GetMapping("/")
	public String root() {
		return "redirect:/swagger-ui.html";
	}
}

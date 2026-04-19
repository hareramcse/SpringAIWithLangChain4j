package com.hs.spring_ai_research.dto;

public record ReviewIssue(
		String type,
		String description,
		String location,
		String suggestion
) {}

package com.hs.spring_ai_rag.eval;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RagEvalDataset(List<RagEvalCase> cases) {

	public RagEvalDataset {
		if (cases == null || cases.isEmpty()) {
			throw new IllegalArgumentException("eval dataset must contain at least one case");
		}
	}
}

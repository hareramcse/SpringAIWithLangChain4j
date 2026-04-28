package com.hs.spring_ai_rag.evaluation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoldenEvalDataset(List<GoldenEvalCase> cases) {
}

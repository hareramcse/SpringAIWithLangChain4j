package com.hs.spring_ai_rag.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class EvalTextMatchTest {

	@Test
	void containsNormalized_isCaseInsensitiveAndIgnoresExtraWhitespace() {
		assertThat(EvalTextMatch.containsNormalized("The LBW rule applies here.", "lbw")).isTrue();
		assertThat(EvalTextMatch.containsNormalized("Hello\n\nWorld", "hello world")).isTrue();
		assertThat(EvalTextMatch.containsNormalized("abc", "xyz")).isFalse();
	}

	@Test
	void allPhrasesPresent_requiresEachPhrase() {
		assertThat(EvalTextMatch.allPhrasesPresent("First Milestone on 2025-10-15", List.of("2025-10-15", "Milestone")))
				.isTrue();
		assertThat(EvalTextMatch.allPhrasesPresent("only one phrase", List.of("one", "missing"))).isFalse();
	}
}

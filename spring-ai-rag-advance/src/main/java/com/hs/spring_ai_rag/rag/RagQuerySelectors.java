package com.hs.spring_ai_rag.rag;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;

/**
 * Factory for {@link dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator}'s {@code querySelector}:
 * chooses the {@link Query} whose text is used to score passages, preferring the {@link UserMessage} embedded in metadata when present.
 */
public final class RagQuerySelectors {

	private RagQuerySelectors() {
	}

	/**
	 * @return selector that picks which {@link Query} text to pass to the {@link dev.langchain4j.model.scoring.ScoringModel}
	 */
	public static Function<Map<Query, Collection<List<Content>>>, Query> rerankingQuerySelector() {
		return queryToContents -> {
			Query representativeQuery = queryToContents.keySet().iterator().next();
			Metadata meta = representativeQuery.metadata();
			if (meta != null) {
				ChatMessage chatMessage = meta.chatMessage();
				if (chatMessage instanceof UserMessage userMessage) {
					return Query.from(userMessage.singleText(), meta);
				}
			}
			return representativeQuery;
		};
	}
}

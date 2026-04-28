package com.hs.spring_ai_rag.rag.injector;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class CompressingContentInjector implements ContentInjector {

	private final ContentInjector delegate;
	private final int maxInjectedContextChars;

	@Override
	public ChatMessage inject(List<Content> contents, ChatMessage chatMessage) {
		List<Content> compressed = compressByCharBudget(contents, maxInjectedContextChars);
		return delegate.inject(compressed, chatMessage);
	}

	static List<Content> compressByCharBudget(List<Content> contents, int maxTotalChars) {
		if (contents.isEmpty() || maxTotalChars <= 0) {
			return contents;
		}
		List<Content> out = new ArrayList<>();
		int used = 0;
		for (Content c : contents) {
			String text = c.textSegment().text();
			if (text == null) {
				text = "";
			}
			if (used + text.length() <= maxTotalChars) {
				out.add(c);
				used += text.length();
				continue;
			}
			int remaining = maxTotalChars - used;
			if (remaining <= 0) {
				break;
			}
			String truncated = text.substring(0, remaining);
			out.add(Content.from(TextSegment.from(truncated, c.textSegment().metadata())));
			break;
		}
		return out;
	}
}

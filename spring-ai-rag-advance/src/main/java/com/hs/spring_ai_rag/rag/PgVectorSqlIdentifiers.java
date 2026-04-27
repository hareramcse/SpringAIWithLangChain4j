package com.hs.spring_ai_rag.rag;

import java.util.regex.Pattern;

/**
 * Validates {@code app.pgvector} identifiers before they are embedded in SQL.
 */
final class PgVectorSqlIdentifiers {

	private static final Pattern IDENT = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
	private static final Pattern REGCONFIG = Pattern.compile("^[a-z0-9_]+$");

	private PgVectorSqlIdentifiers() {
	}

	static void requireSqlIdentifier(String name, String value) {
		if (value == null || !IDENT.matcher(value).matches()) {
			throw new IllegalArgumentException("Invalid SQL identifier for " + name + ": " + value);
		}
	}

	static void requireRegconfig(String textSearchConfig) {
		if (textSearchConfig == null || !REGCONFIG.matcher(textSearchConfig).matches()) {
			throw new IllegalArgumentException("Invalid PostgreSQL text search configuration name: " + textSearchConfig);
		}
	}
}

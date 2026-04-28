# Technical guide: RAG POC (Spring Boot + LangChain4j)

This project is a **small but complete RAG (Retrieval-Augmented Generation) proof of concept** using **Spring Boot** and **LangChain4j** (not the separate **Spring AI** library—the ideas overlap; the dependency names differ). This document explains **what runs**, **why each feature exists**, **what goes wrong if you remove it**, and **concrete examples** using the built-in **POC knowledge base**.

---

## Table of contents

1. [What is RAG?](#1-what-is-rag)
2. [POC knowledge base](#2-poc-knowledge-base)
3. [End-to-end flow](#3-end-to-end-flow)
4. [Features in depth (why / without / example)](#4-features-in-depth-why--without--example)
5. [Configuration reference](#5-configuration-reference)
6. [Where to read the code](#6-where-to-read-the-code)
7. [How to test this application](#7-how-to-test-this-application)
8. [Glossary](#8-glossary)
9. [Troubleshooting](#9-troubleshooting)

---

## 1. What is RAG?

**Without RAG:** A chat model answers from parametric memory. It can sound authoritative about *your* policies while guessing.

**With RAG:** You **index your text**, **retrieve** snippets that match the question, **paste them into the prompt** as evidence, and ask the model to **ground** the answer. Retrieval quality directly limits answer quality.

---

## 2. POC knowledge base

All ingestible content lives in **one file**:

| Path | Role |
|------|------|
| `src/main/resources/kb/poc-knowledge-base.json` | **`documents`**: ingest-only text (`id`, `title`, `body`). |

At startup (`IngestionRunner`), if the pgvector table is empty, the runner reads **`kb/poc-knowledge-base.json`** and turns each entry into a LangChain4j **`Document`**:

- Text is `# {title}\n\n{body}` so headings survive chunking.
- Metadata includes `kb_id` (the JSON `id`) for debugging.

**Example snippets you can ask about:**

- **Returns:** *“Opened software may not be returned once the license key has been revealed…”*
- **Exact codes:** **`SKU-9922-B`**, **`CASE-POC-7741`**
- **RMA:** *“The RMA window is 14 days from approval…”*
- **Filler / distraction:** shipping zones (east vs west hub)—useful to see re-ranking beat “similar but wrong” chunks.

Older sample assets (`static/sample_data.json`, `static/cricket_rules.pdf`) were removed so the POC is **one coherent JSON corpus**.

---

## 3. End-to-end flow

| Phase | What happens |
|-------|----------------|
| **Startup (empty DB)** | **Flyway** runs `db/migration` SQL (pgvector extension, embedding table, generated **`tsvector`** + GIN). Then load `poc-knowledge-base.json` → split → embed → store (LangChain4j `createTable(false)` — schema owned by Flyway). |
| **Each `POST /chat`** | Build user message → retrieve (**dense** first; **FTS** only if dense returns nothing and hybrid is on) → optional **LLM re-rank** (`app.rag.reranking.enabled`) → **guardrail** (only meaningful when re-ranking is on) → chat model with system rules (**“Not found in documents”** when there are no excerpts). |

**Models (`application.yaml`):**

- **Embeddings:** `text-embedding-3-small` @ 512 dims (must match `app.pgvector.dimension`).
- **Chat:** `gpt-4o-mini` — passage scores (JSON) when re-ranking is on, final reply.

**Prerequisites:** Java 21, Maven, Postgres with **pgvector**, `OPENAI_API_KEY`.

---

## 4. Features in depth (why / without / example)

Each subsection follows: **Why** → **If you turn it off or skip it** → **POC example** (from `poc-knowledge-base.json`).

---

### 4.1 Chunking (character splits + overlap)

**Why:** Whole documents are too long for one vector; small segments align embeddings with specific facts. Overlap reduces bad cuts (*“Opened soft…”* at chunk boundary).

**Without / weak chunking:** One huge vector mixes unrelated ideas, or tiny fragments lose meaning → retrieval returns **wrong** or **empty** spans.

**POC example:** The returns policy and warranty SKU live in **different** `documents` entries; chunk size `300` chars (default) keeps most paragraphs coherent. Try: *“Can I return opened software after the license key is revealed?”* — the golden phrase *“Opened software may not be returned”* should land in a retrieved chunk.

**Code:** `ChunkingConfig` (recursive split only), `AppRagProperties.chunking`, splitting inline in `IngestionRunner`.

---

### 4.2 Dense retrieval (pgvector cosine)

**Why:** Maps question + documents into vectors and finds **semantically** similar chunks—users do not need exact wording.

**Without it:** You only have keyword search; paraphrases and synonyms miss unless spelled like the doc.

**POC example:** User says *“return policy after I revealed the product key”* (no word “license”)—dense search can still surface the returns paragraph.

**Code:** `PgVectorEmbeddingStore`, `EmbeddingStoreContentRetriever` (when hybrid is off) or the dense leg of `HybridPgVectorContentRetriever`.

---

### 4.3 Hybrid search (vector first, FTS fallback)

**Why:** **Dense** search runs first. If it returns **no** rows (nothing passes similarity / empty store), **full-text search** on the stored **`tsvector`** column runs as a fallback so exact tokens (SKUs, codes) can still match.

**Without hybrid:** When dense returns nothing, there is no FTS fallback—only dense results.

**POC example:** Body text includes **`SKU-9922-B`**. If embeddings miss, FTS may still find the paragraph with that SKU.

**Code:** `HybridPgVectorContentRetriever`, `db/migration/V1__rag_pgvector.sql`, `spring.flyway.placeholders.*`, `app.pgvector.text-search-config`, `tsv-column`.

---

### 4.4 Query expansion

**Not in this POC:** Multi-query paraphrasing was removed to keep the stack small. Retrieval uses the **user question only**.

---

### 4.5 LLM re-ranking

**Why:** After retrieval (up to **`initial-max-results`** candidates when re-ranking is on), a **second LLM pass** scores each *(question, passage)* in `[0,1]`. The top **`max-results`** passages are sent to the answer model—same mental model as “re-rank after the vector store, before the chat model.”

**Without re-ranking:** Order is **dense order** (or FTS order if dense was empty and hybrid ran FTS).

**POC example:** Returns vs shipping both mention “days”; re-ranking should prefer the **returns** paragraph for refund questions.

**Code:** `LlmCrossEncoderScoringModel`, LangChain4j **`ReRankingContentAggregator`**, `RagQuerySelectors.rerankingQuerySelector()`.

---

### 4.6 Confidence guardrail (low re-rank → no answer context)

**Why:** When retrieval is **weak**, letting the chat model improvise produces **confident hallucinations**. After re-ranking, we take the **best** `RERANKED_SCORE` among final chunks; if it is **below** `app.rag.guardrail.min-rerank-score` (or there are no chunks), we **strip all retrieved excerpts** so the model sees **no** knowledge context. The system prompt then forces the exact reply **`Not found in documents`**.

**Without guardrail:** Even a 0.12 “best” passage can be passed in; the model may **fabricate** details not supported by text.

**POC example:** Ask something **not in the corpus** (e.g. *“What is our lunar return policy?”*). Scores stay low → guardrail fires → user gets **“Not found in documents”** instead of invented policy.

**Caveats:** Guardrail logic runs only when **`app.rag.reranking.enabled`** is true (scores come from the re-ranker). Tune `min-rerank-score`: too **high** → frequent “not found”; too **low** → weak grounding slips through.

**Code:** `GatingRetrievalAugmentor`, `AppRagProperties.guardrail`, `ChatService` system message (`NO_EVIDENCE_REPLY`).

---

## 5. Configuration reference

| YAML area | Highlights |
|-----------|------------|
| `app.rag.chunking.*` | Recursive split only: `max-segment-size-chars`, `max-overlap-chars`. |
| `app.rag.retrieval.*` | Dense `min-score`, `max-results`, `initial-max-results` (wide pool when re-ranking). |
| `app.rag.reranking.*` | Enable LLM scores on retrieved chunks, batch size, optional per-chunk `min-score`. |
| `app.rag.guardrail.*` | `enabled`, `min-rerank-score`, `no-evidence-message` (logged; phrase aligned with `ChatService.NO_EVIDENCE_REPLY`). |
| `app.pgvector.*` | Connection, table, dimension, hybrid toggle (`hybrid-retrieval`: vector then FTS fallback), FTS names (`text-search-config`, `tsv-column`). Also drives **`spring.datasource`** and **Flyway placeholders** — keep in sync. |
| `spring.datasource.*` | JDBC URL / user / password (defaults mirror `app.pgvector` via `${…}` placeholders). |
| `spring.flyway.placeholders.*` | Substituted into `V1__rag_pgvector.sql` (`pgvector_table`, `dimension`, `tsv` column, `textsearch`, GIN index name). |
| `langchain4j.open-ai.*` | Chat + embedding models; **embedding dimensions must match** `app.pgvector.dimension` and Flyway `vector(...)`. |

---

## 6. Where to read the code

| Topic | Files |
|-------|--------|
| RAG beans | `AiConfig.java`, `AppRagProperties.java`, `AppPgVectorProperties.java` |
| Ingest | `IngestionRunner.java` |
| Hybrid + FTS | `HybridPgVectorContentRetriever.java`, `PgVectorSqlIdentifiers.java`, `db/migration/V1__rag_pgvector.sql` |
| Re-ranking | `LlmCrossEncoderScoringModel.java`, `RagQuerySelectors.java` (wired via `ReRankingContentAggregator` in `AiConfig`) |
| Guardrail | `GatingRetrievalAugmentor.java`, `ChatService.java` (`NO_EVIDENCE_REPLY`) |
| Schema (Flyway) | `src/main/resources/db/migration/V1__rag_pgvector.sql`, `application.yaml` (`spring.datasource`, `spring.flyway`) |
| HTTP | `ChatController.java` |

---

## 7. How to test this application

Follow these steps in order the first time you run the POC. Each step names **what you do**, **what should happen**, and **which code participates**.

### Step 1 — Prerequisites on your machine

| Requirement | Why |
|---------------|-----|
| **Java 21** + **Maven** (or use the included `mvnw` / `mvnw.cmd`) | Compiles and runs Spring Boot. |
| **Docker** (optional but easiest) | Runs Postgres + pgvector to match `application.yaml`. |
| **`OPENAI_API_KEY`** in the environment | `langchain4j.open-ai.*` reads it for embeddings, optional re-rank scoring, and chat. Without it the app fails at startup or on first model call. |

**Code:** no project Java here—only OS and shell.

---

### Step 2 — Start PostgreSQL (pgvector)

From the project root (where `docker-compose.yml` lives):

```bash
docker compose up -d
```

This starts Postgres **17** with the **pgvector** image, user `postgres`, password `password`, database **`RAG`**, port **5432**—aligned with `app.pgvector.*` in `application.yaml`.

**Code:** `docker-compose.yml` only (infrastructure). The app does **not** start Postgres for you.

---

### Step 3 — Build the application

```bash
mvn clean package -DskipTests
```

(or `.\mvnw.cmd clean package -DskipTests` on Windows PowerShell from the same directory.)

**Code:** `pom.xml` defines dependencies (LangChain4j, Spring Boot, pgvector client, JDBC). No application code runs yet.

---

### Step 4 — Run the Spring Boot application (first run = ingest + DDL)

Set the API key, then start the app (example for PowerShell):

```powershell
$env:OPENAI_API_KEY = "sk-..."
mvn spring-boot:run
```

**What happens on a cold database (empty embedding table):**

1. **Spring context starts** — `SpringAiRagApplication` bootstraps `DataSource`, **Flyway** (applies `V1__rag_pgvector.sql`: extension + table + **`tsvector`** column + GIN), then `AiConfig`, `ChunkingConfig`, etc.
2. **`PgVectorEmbeddingStore` bean** — `AiConfig.embeddingStore()` connects with **`createTable(false)`** (table already created by Flyway).
3. **`IngestionRunner`** (`CommandLineRunner`) — one probe vector search; if the store is empty: load JSON from the classpath, **`DocumentSplitter`** from `ChunkingConfig`, **`embeddingModel.embedAll`**, **`embeddingStore.addAll`**.

**Logs to expect:** “Loading knowledge base…”, “Parsed *N*…”, “Split into *M*…”, “Embedded and stored…”. Second start: “Embedding store already has data; skipping ingest.”

**Code map:** `SpringAiRagApplication.java` → Flyway `V1__rag_pgvector.sql` → `IngestionRunner.java` + `ChunkingConfig.java` → `PgVectorEmbeddingStore`. Chat time: `HybridPgVectorContentRetriever.java` (uses auto-configured **`JdbcTemplate`**).

---

### Step 5 — Smoke-test chat (`POST /chat`)

With the app still running, send a plain-text body (not JSON) to the chat endpoint. Example with **curl**:

```bash
curl -s -X POST http://localhost:8080/chat -H "Content-Type: text/plain" -d "Can I return opened software after the license key was revealed?"
```

**What happens in code:**

1. **`ChatController.chat(String)`** receives the body and delegates to **`ChatService`**.
2. **`ChatService`** is a LangChain4j **`@AiService`** interface: the generated implementation calls the configured **`RetrievalAugmentor`** (your **`GatingRetrievalAugmentor`** wrapping **`DefaultRetrievalAugmentor`**).
3. **`DefaultRetrievalAugmentor`** runs **`ContentRetriever.retrieve`** — either **`HybridPgVectorContentRetriever`** (dense first; FTS only if dense is empty) or dense-only **`EmbeddingStoreContentRetriever`**, depending on `app.pgvector.hybrid-retrieval`.
4. **`ReRankingContentAggregator`** (when `app.rag.reranking.enabled`) calls **`LlmCrossEncoderScoringModel`** to score chunks and keeps top **`max-results`**; otherwise **`DefaultContentAggregator`** passes retrieval through.
5. **`GatingRetrievalAugmentor`** clears retrieved content if the best re-rank score is below **`app.rag.guardrail.min-rerank-score`** (when guardrail + re-ranking are enabled).
6. The **chat model** generates the final string using the **`@SystemMessage`** rules in **`ChatService.java`** (including **“Not found in documents”** when there are no excerpts).

**Code map:** `ChatController.java` → `ChatService.java` → `AiConfig.java` → `GatingRetrievalAugmentor.java` → `HybridPgVectorContentRetriever.java` (or dense-only retriever) → optional `ReRankingContentAggregator` / `LlmCrossEncoderScoringModel.java` when re-ranking is enabled.

---

### Step 6 — Re-test after corpus or config changes

- **Changed `kb/poc-knowledge-base.json` only:** truncate or drop **`app.pgvector.table`** (or the whole DB), restart so **`IngestionRunner`** ingests again (see Troubleshooting §9).
- **Changed retrieval / guardrail YAML:** restart; no re-ingest required unless you change chunking or corpus.

---

## 8. Glossary

| Term | Meaning |
|------|---------|
| **RAG** | Retrieve evidence, augment prompt, generate. |
| **Embedding** | Numeric vector representing text for similarity. |
| **pgvector** | Postgres extension for vector similarity search. |
| **RRF** | Reciprocal Rank Fusion — LangChain4j can use this when merging multiple ranked lists (e.g. multi-source retrieval). This POC does **not** fuse vector + FTS; hybrid is **fallback** only. |
| **Re-ranking** | Second-stage scoring of *(query, passage)* pairs (here: LLM JSON scores), then top-`max-results` for the answer model. |
| **FTS** | Full-text search (`tsvector` / `plainto_tsquery` / `ts_rank_cd`). |
| **Guardrail** | Block grounded answers when best re-rank confidence is below a threshold. |

---

## 9. Troubleshooting

0. **Switched corpus but answers still reflect old PDF/JSON** — Ingest only runs when the embedding table is **empty**. For a fresh POC ingest, truncate or drop the table configured in `app.pgvector.table`, then restart.
1. **Empty or “Not found” too often** — Lower `app.rag.guardrail.min-rerank-score` or `app.rag.retrieval.min-score`; confirm ingest ran (non-empty table).
2. **Wrong factual answers** — Raise guardrail threshold; widen `initial-max-results`; check hybrid FTS is on for SKU-like queries.
3. **Startup error on retrieval sizes** — When re-ranking is on: `initial-max-results` must be **≥** `max-results` (`AiConfig` validates).
4. **Embedding dimension errors** — `langchain4j.open-ai.embedding-model.dimensions` must equal `app.pgvector.dimension`.
5. **`BadSqlGrammarException` on `ALTER TABLE … to_tsvector('langchain4j_embeddings', …)`** — **`app.pgvector.text-search-config`** was wrong (often the **table name** ended up in that field). It must be a PostgreSQL **regconfig** such as **`simple`** or **`english`**. Check `application.yaml` and any **`APP_PGVECTOR_*`** env vars. The project binds **`AppPgVectorProperties`** as a **JavaBean** (getters/setters) so YAML keys map by **name**, not constructor order; `textSearchConfig` also defaults to **`simple`** if the key is missing.

---

*Examples assume the default POC JSON and chunking; tune YAML and prompts for your own production corpus.*

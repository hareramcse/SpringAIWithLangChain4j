# Technical guide: RAG in this project (for beginners)

This repository is a **small RAG (Retrieval-Augmented Generation) demo** built with **Spring Boot** and **LangChain4j** (not the separate **Spring AI** library from Spring—same *ideas*, different dependency names). If you know Spring Boot but are new to RAG, this guide walks you through **what happens**, **why each step exists**, and **where it lives in the code**.

---

## Table of contents

1. [What is RAG? (plain English)](#1-what-is-rag-plain-english)
2. [What this application does](#2-what-this-application-does)
3. [What you need before running it](#3-what-you-need-before-running-it)
4. [Spring Boot ideas used here](#4-spring-boot-ideas-used-here)
5. [Examples: sample files to answers](#5-examples-sample-files-to-answers) — [5.1](#51-return-policy-style) · [5.2](#52-sample_datajson) · [5.3](#53-cricket_rulespdf)
6. [Pipeline step by step (concepts)](#6-pipeline-step-by-step-concepts)
7. [Configuration: every important setting](#7-configuration-every-important-setting)
8. [Quick reference table](#8-quick-reference-table)
9. [Where to read the code](#9-where-to-read-the-code)
10. [Glossary](#10-glossary)

---

## 1. What is RAG? (plain English)

**Without RAG:** A chat model only knows what it was trained on. It can *sound* confident about *your* internal PDFs even when it is guessing.

**With RAG:** You **store your documents** in a searchable way. When the user asks a question, the app **finds the most relevant snippets** and **pastes them into the prompt** as *evidence*. The model is instructed to **answer from that evidence**. That is the “**R**etrieval” part; “**A**ugmented” means the prompt is augmented with retrieved text; “**G**eneration” is the normal chat reply.

Think of it like a **librarian**:

1. **Ingest** — Cut books into pages (chunks), file them in a catalog (vector database).
2. **Retrieve** — When someone asks a question, find the best pages (similarity search).
3. **Generate** — Give those pages to someone who writes the final answer (the LLM).

---

## 2. What this application does

| Phase | What happens |
|-------|----------------|
| **Startup (once)** | If the embedding table is empty: load sample **PDF** + **JSON**, **split** into text segments, **embed** them, **save** vectors in **Postgres (pgvector)**. See `IngestionRunner`. |
| **Each chat request** | `POST /chat` with the user’s question as a **plain-text body**. LangChain4j builds a **retrieval augmentor**: expand the query → search pgvector → optionally **re-rank** and **MMR** → call the chat model with the **top snippets** + question. See `ChatController` → `ChatService`. |

**Models (from `application.yaml`):**

- **Embeddings:** `text-embedding-3-small` — turns each text snippet and each question into a **vector** (a list of numbers).
- **Chat:** `gpt-4o-mini` — used to **paraphrase** the question, **score** passages for re-ranking, and **write** the final answer.

---

## 3. What you need before running it

- **Java 21** and **Maven** (to build).
- **PostgreSQL** with the **pgvector** extension, and a database matching `app.pgvector.*` in `application.yaml`.
- An **OpenAI API key** in the environment as `OPENAI_API_KEY` (used by the LangChain4j starter).

If Postgres is wrong or empty, retrieval will fail or return nothing—fix **connection** settings under `app.pgvector` first.

---

## 4. Spring Boot ideas used here

You do **not** need to be a Spring expert, but these patterns appear everywhere in the project:

### 4.1 `application.yaml`

This file holds **settings** (database host, chunk sizes, model names). Spring reads it at startup.

### 4.2 Typed configuration (`@ConfigurationProperties`)

Instead of scattering magic strings like `"app.rag.retrieval.max-results"` across the code, we use **records** that Spring **fills automatically** from YAML:

- **`AppRagProperties`** — maps the whole `app.rag` tree: **retrieval**, **reranking**, **chunking**.
- **`AppPgVectorProperties`** — maps `app.pgvector` (Postgres + table + vector dimension).

`AiConfig` registers them with `@EnableConfigurationProperties({...})`. In bean methods you inject `AppRagProperties rag` and call `rag.retrieval().maxResults()` — your IDE can **autocomplete** field names. That is easier for beginners than remembering exact YAML paths.

### 4.3 `@Configuration` and `@Bean`

Classes like `AiConfig` and `ChunkingConfig` tell Spring **how to create objects** (the embedding store, the retriever, the document splitter). Those objects are **beans**: Spring creates **one shared instance** and injects it where needed (e.g. `DataTransformerImpl` gets the `DocumentSplitter`).

### 4.4 `CommandLineRunner`

`IngestionRunner` runs **once after the app starts**. It checks “do we already have vectors?”; if not, it loads files and embeds. That is **not** part of the HTTP chat path.

### 4.5 LangChain4j vs “Spring AI”

This project uses **`langchain4j-spring-boot-starter`** and **`langchain4j-open-ai-spring-boot-starter`**. The **Spring AI** project (`spring-ai-*` dependencies) is a different stack. RAG **concepts** (chunk, embed, retrieve, prompt) are the same; only the **library names and APIs** differ.

---

## 5. Examples: sample files to answers

These three walkthroughs use the **same pipeline** (ingest → chunk → embed → expand → retrieve → fuse → re-rank → MMR → answer). Only the **source text** and **user question** change.

---

### 5.1 Return-policy style

Imagine this sentence is buried inside a policy PDF (the wording is **made up** for teaching; your real PDF might be different):

> *“Opened software may not be returned. Unopened items may be returned within 30 days.”*

#### Step A — Ingest (first run, empty database)

1. **Load** — `DataLoaderImpl` reads a PDF into one big `Document`.
2. **Chunk** — `ChunkingConfig` uses **`AppRagProperties.chunking()`** (default: **300** characters max per chunk, **50** overlap). Suppose we get two segments:
   - **S1:** *“Opened software may not be returned. Unopened items may…”*
   - **S2:** *“…Unopened items may be returned within 30 days.”* (overlap repeats part of S1.)
3. **Embed** — `EmbeddingStoreHelper` embeds **all segments in one batch**, then `embeddingStore.addAll(...)` saves vectors in pgvector.

#### Step B — User asks a question (`POST /chat`)

**User question (HTTP body):**  
`Can I return opened software?`

**Behind the scenes (numbers match defaults in `application.yaml`):**

| Step | What the system does | Beginner takeaway |
|------|----------------------|-------------------|
| 1 | **Query expansion** — `gpt-4o-mini` writes **3** paraphrases (fixed in `AiConfig`, not YAML), e.g. “refund for opened software”, “return policy for opened packages”, plus the original. | More chances to **match** wording in the PDF. |
| 2 | **Dense search** — Each paraphrase is embedded; pgvector returns up to **15** similar segments per query (`initial-max-results`). | Casts a **wide net**; list can be noisy. |
| 3 | **Fusion (RRF)** — The three ranked lists are merged. Segments that appear **high on several lists** float up. | Reduces bad luck from **one** awkward phrasing. |
| 4 | **Re-ranking** — Up to **12** fused segments are scored by the LLM with a **JSON array** in `[0,1]` (`rerank-candidate-cap`, batched by **6**). Scoring uses the **original** user sentence (`SimpleRerankingAggregator`). | Fixes “**similar** embedding but **wrong** answer” (e.g. shipping vs returns). |
| 5 | **MMR** — Pick **3** segments (`max-results`) balancing score and **not repeating** the same idea (`mmr-lambda: 0.5`). | Avoids three near-duplicate chunks in the prompt. |
| 6 | **Answer** — `gpt-4o-mini` reads those segments + `ChatService` system rules and replies, ideally citing *“Opened software may not be returned.”* | The model **must use retrieved text** as evidence. |

**If re-ranking were disabled** (`reranking.enabled: false`), steps 4–5 shrink: fewer segments come straight from **vector order only** — faster, but easier to get the **wrong** top passages.

---

### 5.2 `sample_data.json`

This file really ships in the repo: `src/main/resources/static/sample_data.json`. The root value is a **JSON object** (not an array), so `DataLoaderImpl` builds **one** `Document` from the **entire file text** (`Document.from(rawJson)`). That string still includes **`project`**, **`events`**, milestone **names**, and **dates**—all searchable after chunking and embedding.

*(If the root were a **JSON array** of objects, the loader would walk each element and, when an item has a `"project"` field, embed that inner object as text instead—see `documentFromJsonNode` in `DataLoaderImpl`.)*

**Real excerpt (abbreviated):** the JSON includes events such as **“First Milestone”** on **`2025-10-15`** and **“Final Review”** on **`2025-11-01`**.

#### Step A — Ingest

1. **Load** — Same `IngestionRunner` path: JSON is one `Document` among others (plus the PDF).
2. **Chunk** — With **300** characters, this small JSON often lands in **one or a few** segments; if split, the milestone name and date should still co-exist in at least one segment thanks to overlap.
3. **Embed** — Each segment gets a vector; both **PDF** and **JSON** chunks live in the **same** pgvector table.

#### Step B — Example question

**HTTP body:**  
`When is the First Milestone?`

| Step | What happens (same six steps as §5.1) |
|------|----------------------------------------|
| 1 | Paraphrases might mention “next project checkpoint date”, “First Milestone schedule”, etc. |
| 2–3 | Dense search + RRF surface segments whose text includes **First Milestone** and **`2025-10-15`**. |
| 4–5 | Re-rank/MMR favour the segment that **explicitly** pairs the milestone name with the date. |
| 6 | A good answer: **October 15, 2025** (or `2025-10-15`), taken from the retrieved JSON text. |

If the assistant answers *“This query is not in my database.”*, the milestone segment never reached the final prompt — check ingest, **`min-score`**, or chunk boundaries.

---

### 5.3 `cricket_rules.pdf`

This file really ships: `src/main/resources/static/cricket_rules.pdf`. `ApacheTikaDocumentParser` turns the binary PDF into **one long plain-text** `Document`, then the same splitter/embedder as everything else.

**Note:** The exact characters Tika extracts can differ from what you see in a PDF viewer (headers, line breaks). The important part for beginners is: **whatever text Tika produced** is what gets chunked and embedded.

#### Step A — Ingest

1. **Load** — PDF → one `Document` in `DataLoaderImpl.loadDocumentsFromPdf()`.
2. **Chunk** — Many segments of up to **300** characters; laws that are explained over several pages become **many** neighbouring chunks (overlap helps if one law is split).
3. **Embed** — Same batch embed + `addAll` as §5.1.

#### Step B — Example question

**HTTP body (pick any question that should match your PDF):**  
`What is LBW in cricket?`

| Step | What happens (same six steps as §5.1) |
|------|----------------------------------------|
| 1 | Expansions might include “leg before wicket explained”, “when is the batter out LBW”, etc. |
| 2–3 | Each query pulls up to **15** segments; RRF promotes chunks that rank well across **several** phrasings. |
| 4 | Re-ranking uses the **original** question so a tangential paragraph (e.g. **fielding** only) does not beat the **LBW definition** if both were in the pool. |
| 5 | MMR avoids returning **three** nearly identical copies of the same law paragraph. |
| 6 | The model answers from retrieved cricket text only. |

**Illustrative segment** (typical of many law-style PDFs; your Tika output may differ slightly):

> *“Leg before wicket (LBW): the striker is out if the bowler delivers a fair ball, not pitched on the striker’s wicket, that would have hit the wicket but for interception by the striker’s person.”*

If your PDF uses different wording, retrieval still works when the **meaning** of the question overlaps the **meaning** of a chunk’s embedding.

---

## 6. Pipeline step by step (concepts)

The sections below match **Part B** settings. Each has: **idea → problem it fixes → where in code**.

### 6.1 Chunking

- **Idea:** Documents are split into **`TextSegment`** pieces before embedding. Size is in **characters**, not tokens.
- **Fixes:** One giant vector cannot represent a whole manual; small hits align better with specific questions.
- **Code:** `ChunkingConfig`, `DataTransformerImpl`, `AppRagProperties.Chunking`.

**Tiny example:** If chunks are **too small**, you might retrieve *“Opened soft”* — useless. If **too large**, one vector mixes **returns** and **shipping** and the model picks the wrong focus.

### 6.2 Embeddings + pgvector

- **Idea:** Each segment becomes a **512-dimensional vector** (`text-embedding-3-small` with `dimensions: 512`). Questions get vectors the same way. **pgvector** finds nearest neighbours in SQL.
- **Fixes:** Users do not need exact keywords from the PDF.
- **Code:** LangChain4j embedding bean + `AiConfig` builds `PgVectorEmbeddingStore` from **`AppPgVectorProperties`**.

**Must match:** `langchain4j.open-ai.embedding-model.dimensions` and `app.pgvector.dimension` **must be the same**. If not, you get errors or garbage retrieval.

### 6.3 Dense retrieval (limits)

- **Idea:** Similarity is **one number per segment** — fast but sometimes **wrong order** (high score for a tangential paragraph).
- **Fixes:** Good **recall** (get candidates into the pool).
- **Code:** `EmbeddingStoreContentRetriever` in `AiConfig`; thresholds from **`AppRagProperties.retrieval()`**.

### 6.4 Query expansion + RRF

- **Idea:** Three paraphrases → three searches → **Reciprocal Rank Fusion** merges lists.
- **Fixes:** Vocabulary mismatch between user and document.
- **Code:** `ExpandingQueryTransformer` in `AiConfig`; fusion inside LangChain4j aggregators used by `SimpleRerankingAggregator`.

### 6.5 Re-ranking (LLM scores)

- **Idea:** A second model pass scores **(original question, passage)** together; results are sorted.
- **Fixes:** **Precision** — the best *answer* paragraph rises even if pure vector search ranked it lower.
- **Code:** `LlmCrossEncoderScoringModel`, `SimpleRerankingAggregator`.

### 6.6 MMR (diversity)

- **Idea:** When choosing the final **3** segments, penalize chunks that are **embedding-near-duplicates** of ones already chosen.
- **Fixes:** Wasting the context window on three copies of the same policy sentence.
- **Code:** `MmrSelector` called from `SimpleRerankingAggregator`.

---

## 7. Configuration: every important setting

Below: **default → why → what goes wrong if you change it → mini-example**.

### 7.1 `app.rag.chunking` (`AppRagProperties.chunking`)

| Setting | Default | Why this value | If you change it |
|---------|---------|----------------|------------------|
| `strategy` | `RECURSIVE` | Splits on natural boundaries (paragraphs, etc.) before forcing max size. | `SENTENCE` can create **many tiny** chunks on bullet-heavy PDFs. |
| `max-segment-size-chars` | `300` | About **one short idea** per vector — clearer similarity. | **~50** → fragments like half words; **~1200** → one vector mixes many topics; retrieval gets **muddy**. |
| `max-overlap-chars` | `50` | Repeats ~one clause between neighbours so a bad cut does not **split** the only sentence that answers the question. | **0** → higher risk the golden sentence is **cut in half**; **200+** → more storage and duplicate hits. |

### 7.2 `app.rag.retrieval` (`AppRagProperties.retrieval`)

| Setting | Default | Why | If you change it |
|---------|---------|-----|------------------|
| `min-score` | `0.3` | Drops very weak matches so the pool is not pure noise. | **0.7** → you may get **no chunks** if wording differs; **0.05** → almost everything passes. |
| `max-results` | `3` | Only **three** evidence blocks go to the answer model — focused context. | **1** → may drop an **exception** or second fact; **10** → higher cost and the model may **ignore** the right line. |
| `initial-max-results` | `15` | When re-ranking is on, each expanded query pulls a **wide** list so rank #11 can still enter the re-rank cap. | **Too small** vs `rerank-candidate-cap` → startup **error** (validated in `AiConfig`). **5** → true answer might **never** be retrieved. |

### 7.3 `app.rag.reranking` (`AppRagProperties.reranking`)

| Setting | Default | Why | If you change it |
|---------|---------|-----|------------------|
| `enabled` | `true` | Enables LLM scoring + MMR path. | `false` → **faster**, but order is **vector-only** (more risk of wrong top chunk). |
| `rerank-candidate-cap` | `12` | Caps how many passages get expensive LLM scores; must be ≤ `initial-max-results` and ≥ `max-results`. | **4** → correct paragraph at fused rank **8** is **never scored**. |
| `cross-encoder-max-segments-per-call` | `6` | Keeps each scoring response small (works with `max-tokens: 200`). | **15** in one call → higher chance of **truncated JSON** and fallback scores. |
| `mmr-enabled` | `true` | Reduces duplicate evidence in the final 3. | `false` OK if your corpus has little repetition. |
| `mmr-lambda` | `0.5` | Balance relevance vs diversity. | **0.95** → almost only relevance → **duplicate** chunks may all win; **0.1** → may pick a **weaker** but “different” chunk. |
| `min-score` | *(commented out)* | Optional floor on re-rank scores. | If set **too high**, you can remove **all** passages after scoring. |

### 7.4 `app.pgvector` (`AppPgVectorProperties`)

| Field | Role |
|-------|------|
| `host`, `port`, `database`, `user`, `password` | Normal Postgres login. |
| `table` | Table storing vectors + text metadata. |
| `dimension` | **Must equal** embedding model output size (**512** here). |

### 7.5 `langchain4j.open-ai` (starter config)

| Setting | Default | Role |
|---------|---------|------|
| `chat-model.model-name` | `gpt-4o-mini` | Paraphrases, re-rank JSON scores, final answer. |
| `chat-model.temperature` | `0.0` | Stable, repeatable behaviour for demos. |
| `chat-model.max-tokens` | `200` | Cap output length; **too low** can break JSON scoring arrays. |
| `embedding-model.model-name` | `text-embedding-3-small` | Vectors for segments and queries. |
| `embedding-model.dimensions` | `512` | Must match **`app.pgvector.dimension`**. |

### 7.6 Java constant (not in YAML)

| Constant | Value | Where | Why |
|----------|-------|-------|-----|
| Query expansion count | **3** | `AiConfig` → `ExpandingQueryTransformer(chatModel, 3)` | More paraphrases = more retrieval **cost**; three is a common POC balance. |

---

## 8. Quick reference table

| YAML path | Default | Role |
|-----------|---------|------|
| `app.rag.chunking.strategy` | `RECURSIVE` | Split strategy. |
| `app.rag.chunking.max-segment-size-chars` | `300` | Max chunk length (chars). |
| `app.rag.chunking.max-overlap-chars` | `50` | Overlap between neighbours. |
| `app.rag.retrieval.min-score` | `0.3` | Minimum cosine similarity for a hit. |
| `app.rag.retrieval.max-results` | `3` | Segments in the final answer prompt. |
| `app.rag.retrieval.initial-max-results` | `15` | Per-query pool when re-ranking is on. |
| `app.rag.reranking.enabled` | `true` | LLM re-rank + MMR path. |
| `app.rag.reranking.cross-encoder-max-segments-per-call` | `6` | Passages per scoring API call. |
| `app.rag.reranking.rerank-candidate-cap` | `12` | Max passages scored after fusion. |
| `app.rag.reranking.mmr-enabled` | `true` | Diversity on final pick. |
| `app.rag.reranking.mmr-lambda` | `0.5` | Relevance vs diversity. |
| `langchain4j.open-ai.chat-model.*` | `gpt-4o-mini`, `0.0`, `200` | Chat model. |
| `langchain4j.open-ai.embedding-model.*` | `text-embedding-3-small`, `512` | Embeddings. |
| `app.pgvector.dimension` | `512` | Vector column size — match embeddings. |

---

## 9. Where to read the code

| Topic | Files |
|-------|--------|
| **RAG wiring** (store, retriever, augmentor) | `AiConfig.java`, `AppRagProperties.java`, `AppPgVectorProperties.java` |
| **Chunking** | `ChunkingConfig.java`, `ChunkingStrategy.java`, `DataTransformerImpl.java` |
| **LLM passage scores** | `LlmCrossEncoderScoringModel.java` |
| **Re-rank + MMR + original user query** | `SimpleRerankingAggregator.java`, `MmrSelector.java` |
| **Ingest on startup** | `IngestionRunner.java`, `EmbeddingStoreHelper.java`, `DataLoaderImpl.java` |
| **HTTP API** | `ChatController.java`, `ChatService.java` |
| **Application entry** | `SpringAiRagApplication.java` |

---

## 10. Glossary

| Term | Meaning |
|------|---------|
| **RAG** | Retrieve evidence from *your* data, add it to the prompt, then generate an answer. |
| **Embedding** | A fixed-length vector of numbers representing text meaning for similarity search. |
| **Segment / chunk** | A small piece of text cut from a document; one retrieval unit. |
| **pgvector** | Postgres extension storing vectors and supporting “nearest neighbour” queries. |
| **Retriever** | Component that runs embedding search and returns candidate segments. |
| **Re-ranking** | Second pass that scores how well each **candidate** answers the **question** (here: LLM JSON scores). |
| **MMR** | Maximal Marginal Relevance — pick relevant passages but avoid **near-duplicate** text. |
| **Token vs character** | Chunk settings here are **characters**. Token counts depend on the model tokenizer; do not confuse the two when tuning. |

---

## Tips if something looks “odd”

1. **Empty answers** — Check Postgres is running, table has rows after ingest, and `min-score` is not too high.
2. **Startup exception about rerank cap** — When `reranking.enabled` is true, you need **`initial-max-results` ≥ `rerank-candidate-cap` ≥ `max-results`**. Fix numbers in YAML.
3. **Wrong answers with high confidence** — Often **retrieval** put weak chunks first; try re-ranking on, widen `initial-max-results`, or tune chunk size.
4. **Logs** — LangChain4j can log OpenAI requests/responses (`log-requests` / `log-responses` in YAML); useful to see expansion and scoring, but **do not** enable in production with real user data without redaction.

Examples in this document are **invented** for teaching; tune on **your** documents and questions.

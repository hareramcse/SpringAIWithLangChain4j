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
7. [Evaluation dataset (separate from corpus)](#7-evaluation-dataset-separate-from-corpus)
8. [How to test this application](#8-how-to-test-this-application)
9. [Glossary](#9-glossary)
10. [Troubleshooting](#10-troubleshooting)

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
| `src/main/resources/eval/golden-set.json` | **Regression / eval**: `cases[]` with queries and expected substrings — **never embedded**; pointed to by `app.eval.dataset-resource`. |

At startup (`IngestionRunner`), if the pgvector table is empty, **`ClasspathKnowledgeBaseLoader`** turns each entry into a LangChain4j **`Document`**:

- Text is `# {title}\n\n{body}` so headings survive chunking.
- Metadata includes `kb_id` (the JSON `id`) for debugging.

**Example snippets you can ask about:**

- **Returns:** *“Opened software may not be returned once the license key has been revealed…”*
- **Exact codes:** **`SKU-9922-B`**, **`CASE-POC-7741`**
- **RMA:** *“The RMA window is 14 days from approval…”*
- **Filler / distraction:** shipping zones (east vs west hub)—useful to see re-ranking beat “similar but wrong” chunks.

Older sample assets (`static/sample_data.json`, `static/cricket_rules.pdf`) were removed so the POC is **one coherent corpus** aligned with evaluation cases.

---

## 3. End-to-end flow

| Phase | What happens |
|-------|----------------|
| **Startup (empty DB)** | Load `poc-knowledge-base.json` → split into segments → embed → store in **Postgres + pgvector**. Optional **generated `tsvector` + GIN** for hybrid search (`PgVectorGeneratedFtsInitializer`). |
| **Each `POST /chat`** | Build user message → **query expansion** → retrieve (dense ± hybrid) → **RRF** fusion across paraphrases → **LLM re-rank** + optional **MMR** → **guardrail** (strip context if best re-rank score is too low) → **answer** model with system rules (must say **“Not found in documents”** when no excerpts). |

**Models (`application.yaml`):**

- **Embeddings:** `text-embedding-3-small` @ 512 dims (must match `app.pgvector.dimension`).
- **Chat:** `gpt-4o-mini` — paraphrases, passage scores (JSON), final reply.

**Prerequisites:** Java 21, Maven, Postgres with **pgvector**, `OPENAI_API_KEY`.

---

## 4. Features in depth (why / without / example)

Each subsection follows: **Why** → **If you turn it off or skip it** → **POC example** (from `poc-knowledge-base.json`).

---

### 4.1 Chunking (character splits + overlap)

**Why:** Whole documents are too long for one vector; small segments align embeddings with specific facts. Overlap reduces bad cuts (*“Opened soft…”* at chunk boundary).

**Without / weak chunking:** One huge vector mixes unrelated ideas, or tiny fragments lose meaning → retrieval returns **wrong** or **empty** spans.

**POC example:** The returns policy and warranty SKU live in **different** `documents` entries; chunk size `300` chars (default) keeps most paragraphs coherent. Try: *“Can I return opened software after the license key is revealed?”* — the golden phrase *“Opened software may not be returned”* should land in a retrieved chunk.

**Code:** `ChunkingConfig`, `AppRagProperties.chunking`, `DataTransformerImpl`.

---

### 4.2 Dense retrieval (pgvector cosine)

**Why:** Maps question + documents into vectors and finds **semantically** similar chunks—users do not need exact wording.

**Without it:** You only have keyword search; paraphrases and synonyms miss unless spelled like the doc.

**POC example:** User says *“return policy after I revealed the product key”* (no word “license”)—dense search can still surface the returns paragraph.

**Code:** `PgVectorEmbeddingStore`, `EmbeddingStoreContentRetriever` (when hybrid is off) or the dense leg of `HybridPgVectorContentRetriever`.

---

### 4.3 Hybrid search (vector + Postgres full-text)

**Why:** Vectors are weak on **exact tokens** (SKUs, ticket ids, rare codes). **Full-text search** on a **stored `tsvector`** column finds literal matches; results are **RRF-fused** with dense hits.

**Without hybrid:** Query *“Is **SKU-9922-B** transferable?”* might rank oddly in embedding space; FTS still hits the warranty paragraph that contains the exact SKU.

**POC example:** Body text includes **`SKU-9922-B`** and **`CASE-POC-7741`**. Disable `app.pgvector.hybrid-retrieval` and you may still succeed on some runs, but exact-id questions are the main regression risk.

**Code:** `HybridPgVectorContentRetriever`, `PgVectorGeneratedFtsInitializer`, `app.pgvector.text-search-config`, `tsv-column`.

---

### 4.4 Query expansion + RRF across paraphrases

**Why:** Users phrase questions differently than the corpus. A small LLM generates **extra queries**; each does retrieval; **Reciprocal Rank Fusion (RRF)** merges ranked lists so chunks strong under **several** phrasings rise.

**Without expansion:** One awkward wording can miss the best chunk even though a paraphrase would hit it.

**POC example:** *“hardware swap email subject”* vs explicit *“CASE-POC-7741”*—expansions improve recall before re-ranking.

**Code:** `ExpandingQueryTransformer` in `AiConfig`, fusion inside LangChain4j’s aggregator path (`SimpleRerankingAggregator` uses `ReciprocalRankFuser`).

---

### 4.5 LLM re-ranking + MMR

**Why:** Dense similarity is **imprecise**—the top cosine hit can be a tangentially related paragraph (e.g. shipping hubs when you asked about returns). A second **LLM pass** scores *(question, passage)* in `[0,1]`. **MMR** reduces near-duplicate chunks in the final context.

**Without re-ranking:** Order is **vector (+RRF) only**—faster, but easier to inject a **plausible wrong** paragraph. **Without MMR:** three almost identical snippets can waste context.

**POC example:** Returns vs shipping both mention “days” and “delivery”; re-ranking should prefer the **returns** paragraph for refund questions.

**Code:** `LlmCrossEncoderScoringModel`, `SimpleRerankingAggregator`, `MmrSelector`.

---

### 4.6 Confidence guardrail (low re-rank → no answer context)

**Why:** When retrieval is **weak**, letting the chat model improvise produces **confident hallucinations**. After re-ranking, we take the **best** `RERANKED_SCORE` among final chunks; if it is **below** `app.rag.guardrail.min-rerank-score` (or there are no chunks), we **strip all retrieved excerpts** so the model sees **no** knowledge context. The system prompt then forces the exact reply **`Not found in documents`**.

**Without guardrail:** Even a 0.12 “best” passage can be passed in; the model may **fabricate** details not supported by text.

**POC example:** Ask something **not in the corpus** (e.g. *“What is our lunar return policy?”*). Scores stay low → guardrail fires → user gets **“Not found in documents”** instead of invented policy.

**Caveats:** Guardrail logic runs only when **`app.rag.reranking.enabled`** is true (scores come from the re-ranker). Tune `min-rerank-score`: too **high** → frequent “not found”; too **low** → weak grounding slips through.

**Code:** `GatingRetrievalAugmentor`, `AppRagProperties.guardrail`, `RagGuardrailMessages`, `ChatService` system message.

---

### 4.7 Golden-set evaluation (optional HTTP)

**Why:** Regression-check retrieval + answers with a tiny **query → expected evidence → expected answer phrases** dataset.

**Without it:** You only notice drift in production.

**POC:** Cases live in **`eval/golden-set.json`** (separate from **`kb/poc-knowledge-base.json`**) so expectations are never mixed into ingest payloads. Enable **`app.eval.http-enabled: true`** locally and call **`POST /eval/run`** (uses OpenAI like `/chat`). Path is **`app.eval.dataset-resource`** (default `classpath:eval/golden-set.json`).

**Code:** `RagEvaluationService`, `EvalController` (conditional), `AppEvalProperties`.

---

## 5. Configuration reference

| YAML area | Highlights |
|-----------|------------|
| `app.rag.chunking.*` | `RECURSIVE` split, `max-segment-size-chars`, overlap. |
| `app.rag.retrieval.*` | Dense `min-score`, `max-results`, `initial-max-results` (wide pool when re-ranking). |
| `app.rag.reranking.*` | Enable LLM scores, batch size, MMR, optional per-chunk `min-score`. |
| `app.rag.guardrail.*` | `enabled`, `min-rerank-score`, `no-evidence-message` (logged; reply text fixed in `RagGuardrailMessages` + `ChatService`). |
| `app.pgvector.*` | Connection, table, dimension, hybrid FTS (`text-search-config`, `tsv-column`, `hybrid-retrieval`, `rrf-k`). |
| `app.eval.*` | `http-enabled`, `dataset-resource` (`classpath:eval/golden-set.json` by default). |
| `langchain4j.open-ai.*` | Chat + embedding models; **embedding dimensions must match** `app.pgvector.dimension`. |

**Java constant:** query expansion count (**3**) is fixed in `AiConfig` (not YAML) for brevity.

---

## 6. Where to read the code

| Topic | Files |
|-------|--------|
| RAG beans | `AiConfig.java`, `AppRagProperties.java`, `AppPgVectorProperties.java`, `AppEvalProperties.java` |
| Ingest | `IngestionRunner.java`, `ClasspathKnowledgeBaseLoader.java`, `EmbeddingStoreHelper.java`, `DataTransformerImpl.java` |
| Hybrid + FTS DDL | `HybridPgVectorContentRetriever.java`, `PgVectorGeneratedFtsInitializer.java`, `PgVectorSqlIdentifiers.java` |
| Re-rank + MMR | `LlmCrossEncoderScoringModel.java`, `SimpleRerankingAggregator.java`, `MmrSelector.java` |
| Guardrail | `GatingRetrievalAugmentor.java`, `RagGuardrailMessages.java`, `ChatService.java` |
| Evaluation | `RagEvaluationService.java`, `EvalController.java`, `eval/golden-set.json` |
| JDBC for FTS | `PgVectorDataSourceConfig.java` |
| HTTP | `ChatController.java` |

---

## 7. Evaluation dataset (separate from corpus)

Dataset: **`eval/golden-set.json`** — root object with **`cases`** array. Each case has `id`, `query`, `expectedDocumentContains`, `expectedAnswerContains` (all phrases must appear in the model answer, case-insensitive). This file is **only** for `/eval/run`; it is **not** ingested.

Service: **`RagEvaluationService`** loads **`app.eval.dataset-resource`**, uses the same **`RetrievalAugmentor`** as chat, then **`ChatService.chat`**, and reports chunk hit + answer phrase checks.

**Security:** keep `app.eval.http-enabled: false` in shared environments; evaluation calls OpenAI.

---

## 8. How to test this application

Follow these steps in order the first time you run the POC. Each step names **what you do**, **what should happen**, and **which code participates**.

### Step 1 — Prerequisites on your machine

| Requirement | Why |
|---------------|-----|
| **Java 21** + **Maven** (or use the included `mvnw` / `mvnw.cmd`) | Compiles and runs Spring Boot. |
| **Docker** (optional but easiest) | Runs Postgres + pgvector to match `application.yaml`. |
| **`OPENAI_API_KEY`** in the environment | `langchain4j.open-ai.*` reads it for embeddings, paraphrases, re-rank scoring, chat, and eval. Without it the app fails at startup or on first model call. |

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

1. **Spring context starts** — `SpringAiRagApplication` bootstraps beans from `AiConfig`, `ChunkingConfig`, `PgVectorDataSourceConfig`, etc.
2. **`PgVectorEmbeddingStore` bean** — `AiConfig.embeddingStore()` connects to Postgres and creates the embeddings table if needed (`createTable(true)`).
3. **`PgVectorGeneratedFtsInitializer`** (`ApplicationRunner`, `@DependsOn("embeddingStore")`) — adds the generated **`text_tsv`** column and GIN index when `app.pgvector.hybrid-retrieval` is true.
4. **`IngestionRunner`** (`CommandLineRunner`, runs after `ApplicationRunner`s) — calls **`EmbeddingStoreHelper.hasExistingData()`** (one probe vector search). If the store is empty:
   - **`ClasspathKnowledgeBaseLoader.loadDocuments()`** reads `kb/poc-knowledge-base.json` and builds **`Document`** instances (only the `documents` array).
   - **`DataTransformerImpl`** uses the **`DocumentSplitter`** from `ChunkingConfig` to produce **`TextSegment`**s.
   - **`EmbeddingStoreHelper.embedAndStore()`** batches embeddings via the **`EmbeddingModel`** bean and writes rows with **`embeddingStore.addAll(...)`**.

**Logs to expect:** “Loading classpath knowledge base…”, “Parsed *N* knowledge document(s).”, “Split into *M* segment(s).”, “Embedded and stored…”, “Ingest pipeline finished.” On a **second** start with data already present: “Embedding store already has data; skipping ingest.”

**Code map:** `SpringAiRagApplication.java` → `IngestionRunner.java` → `ClasspathKnowledgeBaseLoader.java` → `DataTransformerImpl.java` → `ChunkingConfig.java` → `EmbeddingStoreHelper.java` → LangChain4j `PgVectorEmbeddingStore`. Parallel: `PgVectorGeneratedFtsInitializer.java` + `PgVectorDataSourceConfig.java` + `HybridPgVectorContentRetriever.java` (used later at chat time).

---

### Step 5 — Smoke-test chat (`POST /chat`)

With the app still running, send a plain-text body (not JSON) to the chat endpoint. Example with **curl**:

```bash
curl -s -X POST http://localhost:8080/chat -H "Content-Type: text/plain" -d "Can I return opened software after the license key was revealed?"
```

**What happens in code:**

1. **`ChatController.chat(String)`** receives the body and delegates to **`ChatService`**.
2. **`ChatService`** is a LangChain4j **`@AiService`** interface: the generated implementation calls the configured **`RetrievalAugmentor`** (your **`GatingRetrievalAugmentor`** wrapping **`DefaultRetrievalAugmentor`**).
3. **`DefaultRetrievalAugmentor`** (from LangChain4j) runs **`ExpandingQueryTransformer`** (paraphrases; wired in `AiConfig`), then **`ContentRetriever.retrieve`** for each query — either **`HybridPgVectorContentRetriever`** (dense + FTS + RRF) or dense-only **`EmbeddingStoreContentRetriever`**, depending on `app.pgvector.hybrid-retrieval`.
4. **`SimpleRerankingAggregator`** (when re-ranking is on) fuses lists, calls **`LlmCrossEncoderScoringModel`** for scores, **`MmrSelector`** for diversity.
5. **`GatingRetrievalAugmentor`** clears retrieved content if the best re-rank score is below **`app.rag.guardrail.min-rerank-score`** (when guardrail + re-ranking are enabled).
6. The **chat model** generates the final string using the **`@SystemMessage`** rules in **`ChatService.java`** (including **“Not found in documents”** when there are no excerpts).

**Code map:** `ChatController.java` → `ChatService.java` → (LangChain4j runtime) → `AiConfig.java` (`RetrievalAugmentor`, `ContentRetriever`, beans) → `GatingRetrievalAugmentor.java` → `HybridPgVectorContentRetriever.java` / `SimpleRerankingAggregator.java` / `LlmCrossEncoderScoringModel.java`.

---

### Step 6 — (Optional) Run the golden-set evaluation (`POST /eval/run`)

1. In **`application.yaml`**, set **`app.eval.http-enabled: true`** (use only on trusted networks; the endpoint calls OpenAI like chat).
2. Restart the app.
3. Call:

```bash
curl -s -X POST http://localhost:8080/eval/run
```

**What happens:** **`EvalController`** (registered only when `http-enabled` is true) invokes **`RagEvaluationService.runEvaluation()`**, which loads **`eval/golden-set.json`** from **`app.eval.dataset-resource`**, runs each case through **`retrievalAugmentor.augment(...)`** and **`chatService.chat(...)`**, and returns a JSON **`RagEvalReport`** (chunk hits, answer phrase checks, previews).

**Code map:** `application.yaml` → `AppEvalProperties.java` → `EvalController.java` → `RagEvaluationService.java` → same `RetrievalAugmentor` + `ChatService` as Step 5. Dataset: **`eval/golden-set.json`** (never ingested by `ClasspathKnowledgeBaseLoader`).

---

### Step 7 — Re-test after corpus or config changes

- **Changed `kb/poc-knowledge-base.json` only:** truncate or drop **`app.pgvector.table`** (or the whole DB), restart so **`IngestionRunner`** ingests again (see Troubleshooting §10).
- **Changed `eval/golden-set.json` only:** no DB reset; restart if you toggled YAML; re-run **`/eval/run`**.
- **Changed retrieval / guardrail YAML:** restart; no re-ingest required unless you change chunking or corpus.

---

## 9. Glossary

| Term | Meaning |
|------|---------|
| **RAG** | Retrieve evidence, augment prompt, generate. |
| **Embedding** | Numeric vector representing text for similarity. |
| **pgvector** | Postgres extension for vector similarity search. |
| **RRF** | Reciprocal Rank Fusion — merge several ranked lists without calibrating scores. |
| **Re-ranking** | Second-stage scoring of *(query, passage)* pairs (here: LLM JSON scores). |
| **MMR** | Maximal Marginal Relevance — trade relevance vs redundancy in the final pick. |
| **FTS** | Full-text search (`tsvector` / `plainto_tsquery` / `ts_rank_cd`). |
| **Guardrail** | Block grounded answers when best re-rank confidence is below a threshold. |

---

## 10. Troubleshooting

0. **Switched corpus but answers still reflect old PDF/JSON** — Ingest only runs when the embedding table is **empty**. For a fresh POC ingest, truncate or drop the table configured in `app.pgvector.table`, then restart.
1. **Empty or “Not found” too often** — Lower `app.rag.guardrail.min-rerank-score` or `app.rag.retrieval.min-score`; confirm ingest ran (non-empty table).
2. **Wrong factual answers** — Raise guardrail threshold; widen `initial-max-results`; check hybrid FTS is on for SKU-like queries.
3. **Startup error on rerank cap** — When re-ranking is on: `initial-max-results` ≥ `rerank-candidate-cap` ≥ `max-results` (`AiConfig` validates).
4. **Embedding dimension errors** — `langchain4j.open-ai.embedding-model.dimensions` must equal `app.pgvector.dimension`.
5. **Eval failures after corpus edits** — Update **`eval/golden-set.json`** (expected substrings + queries) to match **`kb/poc-knowledge-base.json`** `documents`.

---

*Examples assume the default POC JSON and chunking; tune YAML and prompts for your own production corpus.*

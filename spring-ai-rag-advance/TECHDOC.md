# RAG concepts in this application

This document explains **why** each technique exists in this project, **what benefit** it provides, and **how outputs differ** using simple, invented sample records. It matches the behaviour wired in `AiConfig`, `ChunkingConfig`, `SimpleRerankingAggregator`, and `LlmCrossEncoderScoringModel`.

---

## 1. Chunking (ingestion)

### What it is

Raw documents (PDF, JSON) are usually too long to embed or retrieve as a single unit. **Chunking** splits each `Document` into many **`TextSegment`** objects (text snippets). Each segment gets one vector in pgvector and becomes one “hit” at query time.

### Why we use it

- **Embeddings** represent a fixed-length vector summary of text. Very long pages dilute the signal: the vector tries to average “everything,” and similarity search becomes vague.
- **Retrieval precision**: smaller segments align better with a focused user question (e.g. one paragraph about refunds vs a whole handbook).
- **Model context limits**: the chat model only receives a few retrieved segments; chunking chooses what “one unit of evidence” means.

### Settings in this app (`app.rag.chunking`)

| Setting | Meaning |
|---------|---------|
| `strategy` | How to split first (e.g. `RECURSIVE` splits on natural boundaries, then by size). Others: `PARAGRAPH`, `SENTENCE`, `LINE`, `WORD`, `CHARACTER`. |
| `max-segment-size-chars` | Soft cap on how large a segment may grow before further splitting. |
| `max-overlap-chars` | Characters repeated between consecutive chunks so sentences split across a boundary can still appear intact in at least one chunk. |

### Benefit summary

| Without chunking | With chunking |
|------------------|---------------|
| One embedding per entire PDF page | Many embeddings, each focused on a short span |
| Retrieval returns “the whole page” or misses detail | Retrieval returns a specific paragraph-sized snippet |
| A fact split across a cut may be lost | Overlap reduces “half a sentence” problems |

### Sample record: one document → many segments

**Input (single logical document text, shortened):**

> “Our return policy allows returns within 30 days. Opened software may not be returned. Contact support@example.com for RMA.”

**Illustrative output after chunking** (fields are what you would see as `TextSegment.text()`; line breaks added for readability):

| Segment ID | Approx. length | Text (excerpt) |
|------------|----------------|----------------|
| `seg-01` | 120 chars | “Our return policy allows returns within 30 days. Opened software…” |
| `seg-02` | 95 chars | “…within 30 days. Opened software may not be returned. Contact…” |
| `seg-03` | 80 chars | “…may not be returned. Contact support@example.com for RMA.” |

Overlap makes `seg-01` and `seg-02` share a phrase so a query about “30 days” can still match a complete sentence even if the ideal cut landed mid-thought.

---

## 2. Embeddings and dense retrieval

### What it is

Each `TextSegment` is passed through **`text-embedding-3-small`** (512 dimensions in this app). At query time, the user question is embedded the same way, and pgvector returns segments whose vectors are **most similar** (cosine similarity in the store).

### Why we use it

- **Semantic search**: users do not need to repeat exact keywords from the document; similar meaning still yields a high score.
- **Scalable lookup**: the database compares the query vector to many stored vectors quickly.

### Settings (`app.rag.retrieval`)

| Setting | Meaning |
|---------|---------|
| `min-score` | Ignore matches weaker than this similarity (reduces irrelevant chunks). |
| `max-results` | How many segments are ultimately kept for the **chat model** after re-ranking / MMR (when re-ranking is on). |
| `initial-max-results` | When **re-ranking is enabled**, retrieve **more** candidates first so a later step can re-order and filter; must be ≥ `rerank-candidate-cap`. |

### Benefit vs “no threshold / tiny pool”

| Tweak | Benefit |
|-------|---------|
| Higher `min-score` | Fewer off-topic chunks; risk of empty context if too strict. |
| Larger `initial-max-results` | More material for re-ranking to choose from; slightly higher latency/cost. |

### Sample record: vector similarity (illustrative scores)

**User query:** “How long can I return a product?”

| Segment ID | Content snippet | Similarity score (example) |
|------------|-----------------|------------------------------|
| `seg-01` | “…returns within 30 days…” | **0.82** |
| `seg-07` | “…shipping takes 5–7 business days…” | 0.41 |
| `seg-12` | “…holiday closure dates…” | 0.35 |

With `min-score: 0.3`, all three pass; with `min-score: 0.7`, only `seg-01` might survive. The real numbers depend on your data and the embedding model.

---

## 3. Query expansion (advanced RAG step)

### What it is

Before retrieval, this app uses LangChain4j’s **`ExpandingQueryTransformer`**: the chat model is asked to produce **several paraphrases** of the same user question. Each paraphrase runs through the **same** dense retriever.

### Why we use it

- Users phrase questions differently from the handbook wording. Extra queries recall segments that match **synonyms** or **alternate phrasing**.
- **Recall** often improves at the cost of more retrieval work (more queries → more lists to merge).

### Sample record: one user message → multiple retrieval queries

**Original user message:** “Can I send back opened software?”

**Illustrative expanded queries** (strings sent to the embedding retriever):

| Query # | Example text |
|---------|----------------|
| Q1 | “Can I send back opened software?” |
| Q2 | “Are opened software packages eligible for refund?” |
| Q3 | “Return policy for opened or activated software” |

Each query produces its own ranked list of segments from pgvector.

---

## 4. Fusion across expanded queries (Reciprocal Rank Fusion)

### What it is

After retrieval, LangChain4j **`ReciprocalRankFuser`** merges the ranked lists from Q1, Q2, Q3 into **one** ranked list using **RRF** (Reciprocal Rank Fusion). A segment that appears near the top in *several* lists gets a higher fused score than a segment that is #1 in only one list but absent elsewhere.

### Why we use it

- **Robustness**: no single paraphrase has to be perfect; consensus across lists surfaces stable hits.
- **Better recall without manual synonyms**: expansion + fusion approximates “OR” semantics across phrasings.

### Sample record: ranks before fusion

Assume each list is top-3 segment IDs for illustration:

| Rank | List from Q1 | List from Q2 | List from Q3 |
|------|----------------|----------------|----------------|
| 1 | `seg-01` | `seg-02` | `seg-01` |
| 2 | `seg-02` | `seg-01` | `seg-05` |
| 3 | `seg-09` | `seg-05` | `seg-02` |

**After RRF (conceptual fused top order):** `seg-01` and `seg-02` rise because they appear repeatedly in high positions; `seg-09` may drop if only one list liked it.

The exact fused order is computed inside LangChain4j; the important idea is **merge + boost consensus**, not raw vector score alone.

---

## 5. Re-ranking (second stage after retrieval)

### What it is

**Retrieval** (embedding similarity) scores “query ↔ segment” using **one vector per side** (a *bi-encoder* style shortcut). **Re-ranking** runs a **second, slower, more expressive** scorer on the **candidate passages** already returned from fusion, then **re-sorts** (and optionally **filters**) them before the answer generator sees them.

### Why we use it

- **Precision**: the first stage is cheap but coarse; re-ranking can push a truly relevant paragraph above a vaguely similar one.
- **Calibration**: you can apply a **minimum re-rank score** (`app.rag.reranking.min-score`, optional) so weak passages are dropped even if vector search liked them.

### Settings (`app.rag.reranking`)

| Setting | Meaning |
|---------|---------|
| `enabled` | Turn the whole re-rank + MMR path on or off. |
| `rerank-candidate-cap` | Maximum number of fused passages that receive a full re-rank score before trimming / MMR. |
| `cross-encoder-max-segments-per-call` | How many passages are scored per LLM batch (see cross-encoder section). |
| `mmr-enabled` / `mmr-lambda` | Optional diversity pass (see MMR section). |

### Sample record: order change after re-rank (illustrative)

**Fused order (input to re-ranker):**

| Position | Segment | Notes |
|----------|---------|-------|
| 1 | `seg-02` | High vector score, but actually about shipping |
| 2 | `seg-01` | Return window — closer to user intent |
| 3 | `seg-05` | Related policy |

**After re-ranking (conceptual):**

| Position | Segment | Reason (intuition) |
|----------|---------|---------------------|
| 1 | `seg-01` | Re-ranker judges it most relevant to the *original* question |
| 2 | `seg-05` | Secondary policy detail |
| 3 | `seg-02` | Demoted as less on-topic |

So the **output difference** is: the **order** (and possibly membership) of chunks sent to `gpt-4o-mini` for the final answer changes, which changes which facts the model is allowed to cite.

---

## 6. Cross-encoder scoring (LLM implementation in this POC)

### What a “real” cross-encoder is

In research systems, a **cross-encoder** is a neural model that jointly reads **(query, passage)** and outputs a single relevance score. It is usually **more accurate** than embedding similarity for matching, but **more expensive** (you run it once per passage, or per batch).

### What this application does instead

There is **no separate cross-encoder model server** in this POC. `LlmCrossEncoderScoringModel` uses the same **`gpt-4o-mini`** chat model with a strict instruction: return **only** a JSON array of numbers in `[0, 1]`, one score per passage in order. That approximates cross-encoder behaviour: the model **reads query + passage text together** before scoring.

### Why we anchor on the **original** user message

With query expansion, retrieval runs on **Q1, Q2, Q3**. Re-ranking must still answer: “Which passage helps **the user’s actual question**?” `SimpleRerankingAggregator.originalUserQuery()` therefore picks the text from the original **`UserMessage`** in metadata, not an arbitrary expansion line.

### Sample record: LLM output for one batch

**Query (for scoring):** “Can I send back opened software?”

**Passages in one batch:**

| Index | Passage excerpt |
|-------|-----------------|
| 0 | “Opened software may not be returned.” |
| 1 | “Shipping takes 5–7 business days.” |
| 2 | “Contact support@example.com for RMA.” |

**Illustrative model output (JSON array only):**

```json
[0.92, 0.18, 0.44]
```

Those numbers become **re-rank scores** attached to passages; LangChain4j then sorts descending and applies `min-score` if configured.

### Benefit vs retrieval-only

| Stage | What it measures | Typical strength |
|-------|------------------|------------------|
| Embedding retrieval | Similarity of isolated vectors | Fast, good broad recall |
| LLM cross-encoder (POC) | Joint reading of query + passage text | Better fine-grained “is this passage answering the question?” |

### Output difference (sample)

- **Before cross-encoder:** top passage might be a tangential paragraph with a lucky vector match.
- **After cross-encoder:** a slightly lower-vector paragraph that **explicitly** states the policy can jump to rank 1.

---

## 7. MMR — Maximal Marginal Relevance (diversity)

### What it is

After re-ranking, you might still have **several near-duplicate** segments (same policy repeated, FAQ + handbook overlap). **MMR** picks the final `max-results` segments by trading off:

- **Relevance** — prefer passages with higher re-rank score.
- **Diversity** — penalise passages whose **embedding** is too similar to ones already chosen (redundancy).

In code, each step chooses the candidate that maximises:

\[
\text{MMR} = \lambda \cdot \text{relevance} - (1 - \lambda) \cdot \text{redundancy}
\]

where **`mmr-lambda`** (`app.rag.reranking.mmr-lambda`, in `[0,1]`) is set in YAML (and clamped in Java). Higher \(\lambda\) favours relevance; lower \(\lambda\) pushes more diverse chunks.

### Why we use it

- **Avoid five copies of the same policy** in the context window.
- **Cover multiple facets** (e.g. “eligibility” + “how to request RMA”) when the user question is broad.

### Sample record: top re-ranked vs final after MMR (`max-results: 3`)

**Top 5 after re-rank (illustrative):**

| Rank | Segment | Re-rank score | Embedding similarity to `seg-01` |
|------|---------|---------------|-------------------------------------|
| 1 | `seg-01` | 0.92 | 1.00 (self) |
| 2 | `seg-01b` | 0.90 | **0.98** (near duplicate of `seg-01`) |
| 3 | `seg-05` | 0.76 | 0.42 |
| 4 | `seg-08` | 0.71 | 0.55 |
| 5 | `seg-12` | 0.68 | 0.38 |

**Final 3 with MMR (`lambda = 0.5`, conceptual):**

| Final rank | Segment | Why it survived |
|------------|---------|------------------|
| 1 | `seg-01` | Best relevance |
| 2 | `seg-05` | Strong score and **not** almost the same text as `seg-01` |
| 3 | `seg-08` or `seg-12` | Adds a **different angle** instead of `seg-01b` |

**Without MMR:** the model might receive `seg-01` + `seg-01b` + `seg-05` — more repetitive, fewer distinct facts.

---

## 8. End-to-end: how pieces combine at query time

Illustrative pipeline for one user question:

1. **User** asks a question → **Expansion** → several query strings.
2. **Dense retrieval** → several ranked lists from pgvector (`initial-max-results` each when re-ranking is on).
3. **Fusion (RRF)** → one fused ranked list (up to tens of unique segments).
4. **Cross-encoder (LLM)** → numeric scores per passage → **sorted list**, trimmed to `rerank-candidate-cap`.
5. **Optional MMR** → final **`max-results`** segments injected into the prompt for **`gpt-4o-mini`** to answer.

### Sample “record” of counts (not real numbers)

| Step | Example count |
|------|----------------|
| Segments in DB | 1,200 |
| Candidates after one expanded query | 15 |
| Unique candidates after fusion | 22 |
| After re-rank cap | 12 |
| After MMR (`max-results`) | **3** |

---

## 9. Configuration quick reference

All keys below live under `app` in `application.yaml` unless noted.

| Area | Keys | Role |
|------|------|------|
| Chunking | `rag.chunking.*` | Split documents for ingest. |
| Retrieval | `rag.retrieval.min-score`, `max-results`, `initial-max-results` | Vector search strictness and pool sizes. |
| Re-ranking | `rag.reranking.enabled`, `rerank-candidate-cap`, `cross-encoder-max-segments-per-call`, `mmr-enabled`, `mmr-lambda`, optional `min-score` | Second-stage scoring + diversity. |
| Models | `langchain4j.open-ai.*` | Chat + embedding models and API key. |
| pgvector | `app.pgvector.*` | Host, table, vector dimension (**must match** embedding model output). |

---

## 10. Where to read the code

| Topic | Primary files |
|-------|----------------|
| Chunking | `ChunkingConfig.java`, `ChunkingStrategy.java`, `DataTransformerImpl.java` |
| Retrieval + wiring | `AiConfig.java` |
| Cross-encoder (LLM) | `LlmCrossEncoderScoringModel.java` |
| Re-rank + MMR + original query | `SimpleRerankingAggregator.java` |
| Ingest | `SpringAiRagApplication.java`, `EmbeddingStoreHelper.java` |
| HTTP chat | `ChatController.java`, `ChatService.java` |

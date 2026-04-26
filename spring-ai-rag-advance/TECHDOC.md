# RAG concepts in this application

This document explains **why** each technique exists in this project, **what benefit** it provides, and **how outputs differ** using simple, invented sample records. It matches the behaviour wired in `AiConfig`, `ChunkingConfig`, `SimpleRerankingAggregator`, and `LlmCrossEncoderScoringModel`. **Section 1** answers “why this chunk size / overlap?”, compares **fixed vs semantic chunking**, documents **failure modes**, and suggests **experiments**. **Sections 5–6** answer “why re-rank?”, **recall vs precision**, **before vs after** context quality, which **scoring implementation** LangChain4j uses here, the **numeric pipeline** (pgvector pool → re-rank cap → LLM), and **latency vs quality** trade-offs.

### How to read this as a design review

| Question | Where this doc answers it |
|----------|---------------------------|
| **Why this design?** (chunking, pgvector, re-ranking) | §1 chunking trade-offs; §2 why dense/pgvector; §5–6 why a second stage after vectors. |
| **What problems were we solving?** | §1 chunk boundaries; §5 vector **precision** limits and **noisy** top-k; §7 duplicate context; hallucination is indirect (bad context → wrong cites) — §5 “what changes for the answer model”. |
| **What changed after adding re-ranking?** | §5 illustrative **before/after** ordering and log-style tables; this repo does not ship benchmark numbers — §5 “what to measure” for real A/B. |

---

## 1. Chunking (ingestion) — design choice, not “just config”

### What it is

Raw documents (PDF, JSON) are usually too long to embed or retrieve as a single unit. **Chunking** splits each `Document` into many **`TextSegment`** objects. Each segment gets **one vector** in pgvector and is **one retrieval unit** at query time.

**Important:** this application configures size in **`max-segment-size-chars`** (characters), not in tokens. Token counts depend on language and tokenizer; when you run experiments, either **convert targets to characters** for this codebase or **change the code** to use a token-based splitter.

### Why we use chunking at all

- **Embedding quality:** one vector cannot faithfully represent ten pages; the model averages themes and retrieval becomes fuzzy.
- **Precision vs context:** each hit should be small enough to match a specific question, large enough to contain a usable answer span.
- **Downstream limits:** the chat model only sees a few retrieved segments; chunking defines what “one piece of evidence” is.

---

### Why this chunk size? (defaults: 300 characters)

**Direct answer for “Why 300?”**

- **POC stability:** the value started as a simple default (`recursive(300, 50)`) so behaviour is predictable and cheap to embed; it was **not** tuned on a benchmark corpus in this repository.
- **Order-of-magnitude fit:** ~300 characters is roughly **half to one paragraph** of English prose—often one “idea” or rule block. That tends to improve **precision** (the retrieved vector is about one topic) at the cost of **splitting** one long explanation across multiple vectors.
- **Embedding model:** with `text-embedding-3-small` at 512 dimensions, very long chunks still get one vector; **shorter chunks** usually give **cleaner** query–passage similarity for short user questions.

**Trade-off in one sentence:** smaller chunks → better “needle” retrieval; larger chunks → more surrounding context inside one hit but blurrier vectors.

---

### Why this overlap? (defaults: 50 characters)

**Direct answer for “Why 50?”**

- **Boundary failures:** size-based splitters cut **between** sentences. Without overlap, a definition can be **cut in half** so neither chunk is self-contained; retrieval may return the wrong half or miss the key clause.
- **50 characters** is about **one short clause**—enough that the same fact often appears **complete in at least one** of two adjacent chunks, without **doubling** the index size (overlap increases how many segments you store and embed).

**Trade-off:** more overlap → more segments & cost; zero overlap → more **broken-context** retrieval failures at boundaries.

---

### Why not “semantic chunking” here?

**What people usually mean by semantic chunking**

- Splitting where **topic or embedding similarity** between adjacent sentences drops (topic shift), or
- Using an **LLM** to decide chunk boundaries, or
- Merging/splitting until each chunk is “one concept” by a similarity threshold.

**What this app uses instead**

- LangChain4j **structural** strategies (`RECURSIVE`, `PARAGRAPH`, `SENTENCE`, etc.) plus a **hard size cap** in characters. That is closer to **classic fixed / hierarchical chunking** than to full semantic segmentation.

**Why we did *not* pick semantic chunking for this POC**

| Reason | Explanation |
|--------|--------------|
| **Simplicity** | No second embedding pass over sentences, no topic model, no extra LLM calls at ingest—fewer moving parts for learners. |
| **Determinism** | Structural + size rules are **repeatable**; LLM-based boundaries can drift run-to-run unless heavily pinned. |
| **Cost & latency** | Semantic / LLM chunking often means **more compute at ingest**; a POC often starts with cheap splitting. |
| **Good enough first pass** | For many internal docs, recursive + overlap already gives acceptable RAG before investing in segmentation pipelines. |

**When semantic chunking *would* be the stronger design**

- Very long, multi-topic PDFs where **one paragraph mixes unrelated rules** under one vector hurts you every time.
- You see systematic **topic bleed** (retrieved chunk is “about the right page” but the wrong subsection) even after tuning size/overlap.

---

### Fixed / hierarchical chunking vs semantic chunking (comparison)

| Dimension | Fixed / hierarchical (this app’s family) | Semantic chunking (typical) |
|-----------|------------------------------------------|-----------------------------|
| **Boundary rule** | Paragraphs / sentences / recursion + **max length** | Similarity dips, topic model, or LLM-chosen boundaries |
| **Strengths** | Simple, fast ingest, easy to reason about failures | Chunks align with **meaning units**; can reduce “two topics one vector” |
| **Weaknesses** | Cuts can ignore semantics; same topic may span chunks | More complex, often **slower / pricier** ingest; needs validation |
| **Operational cost** | Low | Higher (extra models, caching, QA of boundaries) |
| **Debug story** | “We cut at 300 chars / paragraph” | “Segmenter merged A+B because similarity ≥ τ” |

**Design takeaway:** chunking is a **product/engineering decision**: you choose **repeatable cheap splits** first, then invest in **semantic boundaries** when measured retrieval errors justify the cost.

---

### Failure analysis — where chunking breaks (with examples)

These are realistic **failure modes** for size-first strategies; use them when tuning or when arguing for semantic chunking.

#### 1. Broken or ugly sentences at the cut

**Cause:** hard max length lands mid-sentence.

**Example**

- Chunk ends with: `“…the customer must submit the form no later than”`
- Next chunk starts with: `“midnight on the last business day of the quarter.”`

**Symptom:** retrieved chunk reads **ungrammatically**; the model may hallucinate the missing half or answer “unclear.”

**Mitigations:** increase **overlap**; increase **max segment** slightly; switch to `SENTENCE` / `PARAGRAPH` first; consider semantic boundaries for legal text.

#### 2. Missing cross-chunk context (“need two chunks to answer”)

**Cause:** a rule has **precondition** in chunk A and **action** in chunk B; neither chunk alone matches the query strongly enough, or only one is in the top‑k.

**Example**

- **Chunk A:** “Eligibility: accounts opened after 2024-01-01 are excluded from the legacy bonus.”
- **Chunk B:** “Legacy bonus payout is 5% of Q1 net spend.”
- **Query:** “Do I get the 5% legacy bonus on my account opened in 2024?”

**Symptom:** retriever returns **B** (numbers) but not **A** (eligibility); answer is **wrong or incomplete**.

**Mitigations:** larger chunks **or** parent–child chunking (small retrieve + expand); **lower** `min-score` cautiously; **more** `max-results`; cross-encoder re-ranking (this app) to surface the eligibility chunk if it enters the candidate pool.

#### 3. Duplicate or near-duplicate segments

**Cause:** overlap + repeated boilerplate (headers/footers) creates many similar vectors.

**Symptom:** top results all **sound the same**; wastes context window (this app also uses **MMR** at query time to reduce redundancy among *retrieved* passages—chunking overlaps are a separate source of duplication at **index** time).

#### 4. Wrong “winning” chunk for a nuanced query

**Cause:** two chunks mention the same keyword; the shorter chunk’s vector happens to align slightly better with the query embedding even though the longer chunk has the **correct exception**.

**Symptom:** correct answer is in the corpus but **not in the retrieved set**.

**Mitigations:** re-ranking (this app), query expansion, larger candidate pool (`initial-max-results`), or semantic chunking to separate exception blocks.

---

### Experiment pointers (what to try and what to measure)

Use a **fixed evaluation set**: e.g. 10–20 real questions with **gold** “which paragraph should contain the answer.” For each setting, record **hit@k** (is the gold paragraph in top k?) and subjective answer quality.

#### A. Chunk size: “300 characters” vs “~800 tokens equivalent”

There is no token field in YAML here; for an experiment, **pick character equivalents** or change code to tokenize.

| Approx. scale | Rough mental model (English) | Expected tendency |
|---------------|------------------------------|---------------------|
| **~300 chars** (default) | Short paragraph / few sentences | **Higher precision**, more splits, more boundary risk |
| **~2400–3200 chars** (~800 tokens ballpark for prose) | Multi-paragraph section | **More context inside one hit**, blurrier vectors, fewer total chunks |

**Illustrative retrieval difference (same query, invented):**

**Query:** “What is the late fee after day 10?”

| Chunking regime | Top-1 retrieved snippet might be… | Risk |
|-----------------|-------------------------------------|------|
| Small (~300c) | The **single sentence** with “late fee is $25 after day 10” | Miss if sentence split badly |
| Large (~2500c) | Whole **policy section** including unrelated intro | Right answer inside, but **noise** lowers similarity or confuses the chat model |

**What to log per experiment:** number of segments, ingest time, mean chunk length, hit@3, and **failure tags** (boundary / missing neighbour / duplicate).

#### B. Overlap: 0 vs 50 vs 120 characters

| `max-overlap-chars` | Typical effect |
|---------------------|----------------|
| **0** | Maximum storage efficiency; **highest** chance of broken-sentence and split-definition failures |
| **50** (default) | Moderate insurance at boundaries |
| **120+** | Stronger safety net; **noticeably more** segments and embedding cost |

**Before / after retrieval (illustrative):** same document and query, only overlap changes:

- **Overlap 0:** top hit is chunk ending with `“…fee applies after”` → answer incomplete.
- **Overlap 50:** shared clause pulls `“after day 10 the fee is $25”` fully into **one** of two overlapping chunks → top hit is readable and answerable.

#### C. Before / after retrieval (keep chunking constant, change only size)

**Setup:** same embedded corpus except chunk size A vs B (re-ingest required).

**Query:** “Is opened software returnable?”

| Ingest setting | Top retrieved `TextSegment` (illustrative) | Outcome |
|----------------|---------------------------------------------|---------|
| Small chunks | Chunk is only **“Opened software may not be returned.”** | **High precision** answer |
| Huge chunks | Chunk starts with **shipping**, middle mentions **software**, tail has **exceptions** | Model may **miss** the negative if it focuses on the wrong paragraph inside the chunk |

---

### Settings reference (`app.rag.chunking`)

| Setting | Meaning |
|---------|---------|
| `strategy` | `RECURSIVE`, `PARAGRAPH`, `SENTENCE`, `LINE`, `WORD`, `CHARACTER` — **how** we look for natural breaks before enforcing size. |
| `max-segment-size-chars` | Upper bound on chunk text length (characters). |
| `max-overlap-chars` | Shared tail/head between neighbours to mitigate bad cuts. |

### Sample record: one document → many segments (overlap illustrated)

**Input (shortened):**

> “Our return policy allows returns within 30 days. Opened software may not be returned. Contact support@example.com for RMA.”

**Illustrative segments:**

| Segment ID | Approx. length | Text (excerpt) |
|------------|----------------|----------------|
| `seg-01` | 120 chars | “Our return policy allows returns within 30 days. Opened software…” |
| `seg-02` | 95 chars | “…within 30 days. Opened software may not be returned. Contact…” |
| `seg-03` | 80 chars | “…may not be returned. Contact support@example.com for RMA.” |

Shared text between `seg-01` and `seg-02` exists because of **overlap**: a query about “30 days” still hits a **complete** clause in at least one chunk even if a hard cut would have split it.

---

## 2. Embeddings and dense retrieval

### What it is

Each `TextSegment` is passed through **`text-embedding-3-small`** (512 dimensions in this app). At query time, the user question is embedded the same way, and pgvector returns segments whose vectors are **most similar** (cosine similarity in the store).

### Why we use it

- **Semantic search**: users do not need to repeat exact keywords from the document; similar meaning still yields a high score.
- **Scalable lookup**: the database compares the query vector to many stored vectors quickly.

### Why pgvector (dense retrieval) in this stack?

**pgvector** stores segment embeddings next to the rest of your app data and answers “nearest vectors to this query vector” with **approximate nearest-neighbour** search. For this POC that means: **one embedding model**, **one table**, **no separate search cluster** to operate.

**Design role:** dense retrieval is tuned for **broad recall** — “anything plausibly related” inside a **wider top-N** (see `initial-max-results` when re-ranking is on). It is **not** the last word on “does this passage *answer* the question?” That gap is exactly why **§5 re-ranking** exists: vectors are fast and cheap; a second stage improves **precision** before the chat model sees text.

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

### Why re-ranking is needed: recall vs precision

| Stage | Typical behaviour | Role in the product |
|-------|-------------------|---------------------|
| **Vector search (pgvector)** | High **recall** for “something in the ballpark”: synonyms, partial overlap, and tangential passages can all score *okay* because each side is a **single compressed vector**. | Cast a **wide net** cheaply. |
| **Re-ranker** | Higher **precision** for “this span actually helps answer *this* question”: the scorer sees **full query text + full passage text** together. | **Reorder and trim** so the chat model gets **focused** evidence, not whatever happened to float to the top of cosine similarity. |

**One-line pitch:** *embedding retrieval finds candidates; re-ranking decides which candidates deserve the limited context window.*

**Problems this layer directly targets:** **irrelevant or loosely related chunks** in the top‑k (looks right in embedding space, wrong in reading space), **wrong ordering** when several chunks share vocabulary (e.g. “return” in shipping vs returns policy), and **polluted prompts** that encourage the LLM to latch on to the wrong paragraph (which increases **wrong or overconfident** answers — often grouped under “hallucination” in RAG discussions, though the root cause is **bad retrieval ranking**).

### What type of re-ranking is this? (LangChain4j `ScoringModel`)

LangChain4j can plug in different **`ScoringModel`** implementations (dedicated cross-encoder models, vendor APIs, etc.). **This repository does not use:**

- A **separate cross-encoder** microservice or ONNX model.
- An **external re-rank API** (e.g. Cohere / Voyage / hosted rerank endpoints).

**This repository uses:** `LlmCrossEncoderScoringModel` — the **same OpenAI chat model** (`gpt-4o-mini` in `application.yaml`) prompted to output **only** a JSON array of scores in `[0, 1]`, **batched** (`cross-encoder-max-segments-per-call`). That is an **LLM-as-judge** approximation of **cross-encoder** behaviour (joint query+passage reading), chosen for **POC simplicity** and **no extra infra**; trade-offs are in **latency, cost, and score variance** vs a trained reranker (see below).

### Pipeline clarity (defaults in `application.yaml`)

With **query expansion** (3 paraphrases) and **re-ranking enabled**, the mental model is:

```text
User question
    → [3 expanded queries]
    → pgvector: up to initial-max-results (15) segments per query
    → RRF fusion → one ranked list of unique segments
    → take head for scoring: rerank-candidate-cap (12) segments
    → LLM ScoringModel: scores each (batched), sort by re-rank score
    → optional MMR
    → final max-results (3) TextSegments → prompt for gpt-4o-mini (answer)
```

So it is **not** literally “20 → 5” in this repo; it is **`15 × 3 lists` → fusion → score top `12` → deliver `3`**. The **pattern** is the same everywhere: **wide retrieval pool → narrower, better-ordered set for the generator**. In another deployment you might configure **20 → 5** if your orchestration and budget fit that shape.

### Why shrink top‑k after retrieval? (15 → 12 → 3)

| Knob | Default (this repo) | Why not pass everything to the LLM? |
|------|---------------------|--------------------------------------|
| `initial-max-results` | **15** per expanded query | Gives fusion **diversity** of candidates without embedding scoring on the whole corpus. |
| `rerank-candidate-cap` | **12** | Caps **expensive** second-stage scoring and latency; assumes the true answer is usually **not** ranked below dozens of unrelated hits if chunking and expansion are sane. |
| `max-results` | **3** | **Context window** and **attention**: fewer, better paragraphs beat many mediocre ones; lower **token** cost per question. |

### Latency vs quality trade-off

| Choice | Effect |
|--------|--------|
| Larger `initial-max-results` | Better chance the true passage is **in the pool**; more pgvector work and fusion noise. |
| Larger `rerank-candidate-cap` | More passages get **LLM scores** → usually **better precision**, **higher** latency and **API cost** (this POC pays **one chat call per batch** of segments). |
| Smaller `cross-encoder-max-segments-per-call` | **More** round-trips for the same cap → smoother memory, **worse** latency. |
| Disable re-ranking (`enabled: false`) | **Fastest** path; retriever uses `max-results` directly — **no** second-stage precision fix. |

### Before vs after re-ranking (convincing illustration)

**Same user question:** “Can I send back opened software?”

**Without re-ranking** (imagine we only took the **fused vector order** and passed the top **3** segments — noisy):

| # | Segment (excerpt) | Why it is “noisy” |
|---|-------------------|-------------------|
| 1 | “**Returns** must be postmarked within 30 days…” (paragraph is mostly about **unopened** goods) | Shares vocabulary (“return”) with the question; vector similarity is **high**, user intent is **partially** missed. |
| 2 | “**Shipping** for software licenses is electronic…” | Mentions “software”; **tangential**. |
| 3 | “Contact **support** for warranty **returns**…” | Generic; does not state the **opened software** rule. |

**With re-ranking** (LLM scores against the **original** user message; top **3** after sort + optional MMR):

| # | Segment (excerpt) | Why it is “focused” |
|---|-------------------|-------------------|
| 1 | “**Opened software may not be returned** under any circumstances.” | Direct **answer** span. |
| 2 | “Digital download keys are **non-refundable** once revealed…” | Complementary policy facet. |
| 3 | “For defective media, open an RMA…” | Edge case still on-topic. |

**Log-style view** (invented scores — format you could log in your own evaluator):

```text
# AFTER FUSION (vector / RRF order)     rerank_score
seg-ship-02  "...shipping software..."     0.22
seg-ret-01   "...30 days unopened..."      0.61
seg-ret-09   "...opened software may not..." 0.94

# AFTER RERANK (descending rerank_score)
seg-ret-09   0.94
seg-ret-01   0.61
seg-ship-02  0.22
```

### Scoring explanation (what the number means)

Each passage receives a **scalar relevance score in `[0, 1]`** from the LLM judge: **1** = highly useful for answering the query as asked; **0** = irrelevant. LangChain4j attaches this as re-rank metadata, **sorts** the list, applies optional **`min-score`**, then this app optionally runs **MMR** (§7) before **`max-results`**.

### What to measure (this doc has no built-in benchmark)

For a real system, log **before/after** rank of a labelled “gold” segment, **MRR / nDCG**, task success, or human **thumbs** on answers. Compare **p95 latency** and **cost per query** when you widen `rerank-candidate-cap` or `initial-max-results`.

### Settings (`app.rag.reranking`)

| Setting | Meaning |
|---------|---------|
| `enabled` | Turn the whole re-rank + MMR path on or off. |
| `rerank-candidate-cap` | Maximum number of fused passages that receive a full re-rank score before trimming / MMR. |
| `cross-encoder-max-segments-per-call` | How many passages are scored per LLM batch (see cross-encoder section). |
| `mmr-enabled` / `mmr-lambda` | Optional diversity pass (see MMR section). |
| `min-score` (optional) | Drop passages whose re-rank score is below this floor after sorting (YAML key `app.rag.reranking.min-score`). |

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

**Type (summary):** not a trained cross-encoder weights file and not a vendor rerank HTTP API — an **LLM `ScoringModel`** (`LlmCrossEncoderScoringModel`) invoked through LangChain4j’s **`ReRankingContentAggregator`** (see `SimpleRerankingAggregator`). For **why** that sits after pgvector and **before/after** behaviour, see **§5**.

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

Illustrative pipeline for one user question (aligns with **§5** and default **`app.rag.*`** in `application.yaml`):

1. **User** asks a question → **Expansion** → **3** query strings (`ExpandingQueryTransformer`).
2. **Dense retrieval (pgvector)** → **3** ranked lists, each up to **`initial-max-results` (15)** when re-ranking is on (otherwise the retriever uses **`max-results`** only).
3. **Fusion (RRF)** → one fused ranked list (often **tens** of unique segments depending on overlap between lists).
4. **Re-rank (`ScoringModel`)** → the head of that fused list (up to **`rerank-candidate-cap` = 12**) is scored in **batches of `cross-encoder-max-segments-per-call` (6)** → **sorted** by LLM relevance scores.
5. **Optional MMR** → final **`max-results` = 3** segments injected into the prompt for **`gpt-4o-mini`** to answer.

**Design Q&A (short):** **Why pgvector?** Fast semantic candidate generation in the DB you already run (§2). **Why re-ranking?** Vectors favour **recall**; the second stage improves **precision** and prompt focus (§5). **Why this chunking?** Defines what a “hit” is (§1).

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

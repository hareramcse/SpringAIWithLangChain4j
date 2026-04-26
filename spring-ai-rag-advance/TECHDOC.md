# RAG technical documentation

This document ties **ideas** (why a step exists, what failure it addresses) to **this repository**: `application.yaml`, **`AppRagProperties`** (`app.rag.*`), **`AppPgVectorProperties`** (`app.pgvector.*`), `AiConfig.java`, `ChunkingConfig.java`, `SimpleRerankingAggregator.java`, `MmrSelector.java`, `LlmCrossEncoderScoringModel.java`, and `IngestionRunner.java`. OpenAI settings stay under `langchain4j.open-ai` (starter). Examples are **invented** policy snippets unless stated otherwise.

---

## Part A — Concepts: why each exists, what it fixes

### A.1 Chunking

**What it is**  
Whole PDFs or JSON blobs are too long for one embedding and one retrieval hit. Ingest splits each `Document` into many **`TextSegment`** values; each segment is embedded once and stored in pgvector.

**Why the concept**  
- **One vector cannot represent ten pages faithfully** — the model averages themes; retrieval becomes vague.  
- **The generator only sees a few segments** — chunking defines what “one piece of evidence” means.

**What problem it fixes**  
- **Over-long units:** user asks a narrow question but the vector matches a whole chapter; the wrong subsection can dominate.  
- **Generator limits:** without chunks, you would paste entire files or arbitrary truncations.

**In this app**  
`ChunkingConfig` builds a LangChain4j `DocumentSplitter` from **`AppRagProperties.chunking()`** (YAML `app.rag.chunking.*`). Sizes are in **characters**, not tokens.

**Example (why size matters)**  
Suppose the true sentence is: *“Opened software may not be returned.”*  
- If chunks are **huge**, that sentence sits inside a segment that also talks about **shipping** and **warranties**. A question about opened software might still retrieve that chunk, but the model reads three topics and may **cite shipping** by mistake.  
- If chunks are **tiny** (e.g. 50 characters), you might get *“Opened soft”* in one segment and *“ware may not be returned.”* in the next — retrieval returns **unreadable** text and answers degrade.

---

### A.2 Embeddings and pgvector

**What it is**  
Each `TextSegment` is embedded with **`text-embedding-3-small`** (512 dimensions in YAML). The query is embedded the same way. **pgvector** stores vectors in Postgres and returns **nearest neighbours** by similarity.

**Why the concept**  
Users do not repeat handbook wording; **semantic** similarity still surfaces relevant passages.

**What problem it fixes**  
Keyword-only search misses paraphrases; a vector index gives a **fast shortlist** of plausible passages.

**Why pgvector here (not a separate search engine)**  
One database for app data and vectors: **simpler ops** for a POC. The design trade-off is scale and tuning options versus dedicated vector SaaS — acceptable for demos and moderate corpora.

**Example**  
Query: *“How long do I have to send something back?”*  
A segment containing *“returns within 30 days”* can rank high even without the word *“send”*.

---

### A.3 Dense retrieval — strengths and limits

**What it is**  
Similarity compares **one query vector** to **one passage vector** (bi-encoder style). That is **cheap** but **coarse**.

**What problem it fixes**  
Fast **recall**: “anything in the ballpark” enters the candidate pool.

**What it does *not* fix by itself**  
**Precision**: two passages can embed similarly because they share vocabulary or domain (*returns*, *software*) while only one **answers** the question. Dense search often has **high recall, lower precision** at the top of the list.

**Example (precision failure)**  
Query: *“Can I return opened software?”*  
- Segment A: *“Returns must be postmarked within 30 days for unopened items…”* — shares “return” with the question; **high similarity**.  
- Segment B: *“Opened software may not be returned.”* — the **direct** answer; might rank **second**.  
Vector-only top-1 can therefore be **A** (noisy). A second stage (re-ranking) is meant to **swap** A and B after reading both with the query.

---

### A.4 Query expansion (three paraphrases)

**What it is**  
`AiConfig` wires `ExpandingQueryTransformer(chatModel, 3)` — the chat model produces **three** query strings (original-style paraphrases). **The count `3` is fixed in Java**, not in `application.yaml`.

**Why the concept**  
Users phrase questions differently from the document. Multiple embeddings retrieve **different** shortlists.

**What problem it fixes**  
**Vocabulary mismatch**: one phrasing misses the chunk another phrasing hits.

**Example**  
User: *“Can I send back opened software?”*  
Expanded queries might include *“refund for opened software”* and *“return policy activated software”*. A chunk that says *“non-refundable once license key revealed”* might rank higher on expansion 2 than on expansion 1.

**Why not ten paraphrases**  
Each extra query is **another full retrieval** — latency and cost grow linearly; returns diminish. Three is a common **POC balance**.

---

### A.5 Fusion (Reciprocal Rank Fusion, RRF)

**What it is**  
After expansion, LangChain4j merges the **three ranked lists** with **`ReciprocalRankFuser`**: passages that rank well **across several lists** rise; one-list wonders can fall.

**What problem it fixes**  
A **single** bad or narrow embedding of the user question no longer decides everything.

**Example**  
If segment `seg-09` is #1 for Q1 only but absent from Q2/Q3, while `seg-01` is #2,#1,#3 across lists, **`seg-01`** tends to win after fusion — more **stable** consensus.

---

### A.6 Re-ranking (second stage)

**What it is**  
After fusion, up to **`rerank-candidate-cap`** passages are scored by a LangChain4j **`ScoringModel`**. This app uses **`LlmCrossEncoderScoringModel`**: **`gpt-4o-mini`** outputs a **JSON array** of scores in `[0, 1]` — an **LLM-as-judge** that reads **query + passage together** (cross-encoder *behaviour*, not a separate cross-encoder model file or Cohere rerank API).

**Why the concept**  
Vectors answer *“roughly same topic?”*; re-ranking answers *“does this passage actually help answer this question?”*

**What problem it fixes**  
**Noisy top-k** from A.3, **wrong ordering**, **prompt pollution** (tangential chunks steering the answer model).

**Why scores use the original user message**  
Expansion changes retrieval queries; **`SimpleRerankingAggregator.originalUserQuery()`** ensures scoring still targets the **real** user question.

**Example (before vs after re-rank)**  
**Fused order (vector-ish):**  
1. Shipping paragraph mentioning “software”  
2. Generic support paragraph  
3. *“Opened software may not be returned.”*  

**After LLM scores (illustrative):**  
1. *Opened software…* → **0.94**  
2. Returns window paragraph → **0.61**  
3. Shipping → **0.22**  

The generator now sees the **decisive** line first.

---

### A.7 MMR (Maximal Marginal Relevance)

**What it is**  
After re-ranking, optional **MMR** picks the final **`max-results`** segments by trading **relevance** (re-rank score) against **redundancy** (embedding similarity to segments already chosen). Controlled by **`mmr-lambda`**.

**What problem it fixes**  
Handbook + FAQ often **repeat** the same policy; without MMR, the context window can contain **three near-copies** and miss a complementary fact (e.g. *how to request an RMA*).

**Example**  
Top re-ranked segments might be `policy-A`, `policy-A'`, `policy-A''` (tiny edits). MMR prefers `policy-A` then a segment with **different** embedding geometry, e.g. *RMA procedure*, if scores are close enough.

---

### A.8 End-to-end pipeline (defaults)

1. User message → **3** expanded queries (`AiConfig`).  
2. Each query → pgvector → up to **`initial-max-results` (15)** segments (`EmbeddingStoreContentRetriever`).  
3. **RRF** → one fused list.  
4. Head of list → up to **`rerank-candidate-cap` (12)** scored in batches of **`cross-encoder-max-segments-per-call` (6)**.  
5. Sort by scores; optional **`min-score`** filter; optional **MMR** with **`mmr-lambda` (0.5)**.  
6. **`max-results` (3)** segments → prompt → **`gpt-4o-mini`** answer.

If **`reranking.enabled: false`**, the retriever uses **`max-results`** as the pool size directly and **no** LLM re-rank step runs (`DefaultContentAggregator`).

---

## Part B — `application.yaml` (and related): each value explained

Below: **default → why use it → what it fixes → why not another value → short example.**

### B.1 Chunking — `app.rag.chunking`

#### `strategy: RECURSIVE`

- **Why:** Prefer natural boundaries (paragraphs, lines) **before** forcing `max-segment-size-chars`, so fewer ugly mid-sentence cuts than a raw character splitter.  
- **Fixes:** Chunks that read like coherent fragments where the document structure allows.  
- **Why not always `SENTENCE`:** Bullet lists and tables can produce **too many** tiny segments; `RECURSIVE` is a good default for mixed PDFs.  
- **Example:** A numbered policy list stays grouped until the cap forces a split.

#### `max-segment-size-chars: 300`

- **Why:** Roughly **half to one short paragraph** — often **one main idea** per vector, so similarity is **less muddy** than multi-page chunks.  
- **Fixes:** “Whole chapter one vector” blur; keeps ingest and index size moderate for a POC.  
- **Why not ~50:** You get **fragments** — e.g. chunk 1 ends with *“fee applies after”*, chunk 2 starts *“day ten the fee is $25”* — retrieval may return **incomplete** sentences; the model guesses.  
- **Why not ~800–1500:** One vector must represent **shipping + returns + exceptions** together; a query about *returns* still pulls that blob; **shipping** noise can **outrank** a tighter segment or confuse the answer model.  
- **Example:** Query *“opened software?”* — at **300** chars, a dedicated sentence on opened software often forms its **own** segment; at **1200** chars, that sentence may be **buried** after unrelated text.

#### `max-overlap-chars: 50`

- **Why:** Copies ~**one clause** across adjacent chunks so a bad boundary does not **delete** the only place the answer appears intact.  
- **Fixes:** **Split golden sentences** across two chunks with neither ranking well alone.  
- **Why not `0`:** Cheaper storage, but classic failure: the definitive clause sits **exactly on the cut**.  
- **Why not `200+`:** Strong safety net but **many more** near-duplicate segments → higher **embed cost** and redundant hits unless MMR/query design compensate.  
- **Example:** With **50** overlap, *“after day 10 the late fee is $25”* often appears **complete** in at least one of two overlapping chunks; with **0**, one chunk may end mid-clause.

---

### B.2 Retrieval — `app.rag.retrieval`

#### `min-score: 0.3`

- **Why:** Drop **very weak** cosine matches so the fused list is not full of barely related paragraphs.  
- **Fixes:** Obvious junk in the candidate pool (and downstream re-rank cost).  
- **Why not `0.7`:** Real questions often diverge from doc wording; strict floors yield **empty retrieval** or missing the paragraph that **contains** the answer with only **0.45** similarity.  
- **Why not `0.05`:** Almost everything passes; re-ranker and context fill with **noise**.  
- **Example:** A *holiday hours* paragraph at **0.35** similarity to a *return deadline* question: at **0.3** it may still enter the pool (re-rank can demote it); at **0.7** it never appears — good if you want silence, bad if your gold chunk scores **0.55**.

#### `max-results: 3`

- **Why:** Final prompt stays **small** — forces the strongest, most diverse (with MMR) evidence.  
- **Fixes:** **Context dilution** (model attends to wrong paragraph) and **token cost**.  
- **Why not `1`:** Single segment misses **exceptions**, **second steps**, or *“see also shipping”* nuances.  
- **Why not `10`:** Large context → **higher** latency/cost; model may **ignore** the right line or synthesize across conflicting paragraphs.  
- **Example:** Question needs *rule* + *exception*: **3** slots allow *main rule*, *exception*, *procedure*; **1** drops one leg.

#### `initial-max-results: 15`

- **Why:** When re-ranking is on, each expanded query retrieves a **wider** list so the true answer can sit at **rank 8–12** and still enter the **re-rank cap**.  
- **Fixes:** **Early rank errors** from vectors alone.  
- **Why not `< rerank-candidate-cap`:** `AiConfig` startup validation **fails** — invalid.  
- **Why not `5`:** Pool too small; the correct segment might never appear in **any** of the three lists’ top-5.  
- **Why not `40`:** Better recall chance but **slower** retrieval and **noisier** fusion; re-rank cap still trims to **12**, so marginal gains past a point go to waste unless you also raise **`rerank-candidate-cap`**.  
- **Example:** Gold chunk is **#11** on Q2 — with **15** it is retrieved; with **8** it is **invisible** to the pipeline.

---

### B.3 Re-ranking — `app.rag.reranking`

#### `enabled: true`

- **Why:** Turn on second-stage **precision** (see Part A).  
- **Fixes:** Tangential high-vector segments (A.3, A.6).  
- **Why `false`:** **Lowest latency** and **no** scoring API calls — acceptable if corpus is tiny and vectors are already very clean.  
- **Example:** With **`false`**, fused vector order goes to the LLM; **shipping** might stay above **opened software** if embeddings say so.

#### `rerank-candidate-cap: 12`

- **Why:** Limits how many passages receive **LLM scores** while staying ≥ **`max-results` (3)** and ≤ **`initial-max-results` (15)** (enforced in `AiConfig`).  
- **Fixes:** Cost/latency vs coverage trade-off.  
- **Why not `4`:** The right passage might be **fused rank 9** — never scored.  
- **Why not `30`:** You still only retrieve **15** per query; scoring **30** needs a larger **`initial-max-results`** and more **batches** — much slower.  
- **Example:** Twelve scored passages ≈ **two** batches of six — predictable cost; thirty scored ≈ **five** batches.

#### `cross-encoder-max-segments-per-call: 6`

- **Why:** Each LLM call scores **six** passages; prompt and JSON response stay **short**, which pairs with **`max-tokens: 200`** on the chat model.  
- **Fixes:** **Parse errors** and **truncated** JSON arrays when too many passages are crammed into one completion.  
- **Why not `2`:** More round-trips for **12** passages → **six** calls instead of **two** → higher latency.  
- **Why not `15`:** One huge JSON array; higher risk of malformed output or **output cut-off** if `max-tokens` is tight.  
- **Example:** For **12** segments, batch **6** → **2** API calls; batch **3** → **4** calls.

#### `mmr-enabled: true`

- **Why:** Reduce **near-duplicate** segments in the final window.  
- **Fixes:** Repeated policy text from overlap + FAQ + handbook.  
- **Why `false`:** If corpus has **no** duplication, MMR adds embedding calls for diversity scoring — you might skip it.

#### `mmr-lambda: 0.5`

- **Why:** Balanced trade between **relevance** (re-rank score) and **diversity** (embedding distance to chosen set).  
- **Fixes:** Three copies of the same policy in **`max-results`**.  
- **Why not `0.95`:** Almost pure relevance → **two** nearly identical top-scored chunks can both survive.  
- **Why not `0.1`:** Strong diversity → might pick a **lower-scored** chunk just because it is different, **dropping** a highly relevant duplicate that you might have wanted once.  
- **Example:** `lambda = 0.5` keeps the best-scoring chunk, then prefers the next segment that is **both** reasonably scored **and** not embedding-near-identical to the first.

#### `min-score` (optional, commented in YAML)

- **Why omit:** Trust **ordering** + **`max-results`** + MMR.  
- **When set (e.g. `0.2`):** Drop passages the LLM judge scored **below** threshold after re-rank.  
- **Risk:** Aggressive floor → **empty** context if all scores are modest.  
- **Example:** If all passages get **0.35–0.45** because the question is hard, **`min-score: 0.5`** removes **everything**.

---

### B.4 OpenAI chat model — `langchain4j.open-ai.chat-model`

#### `model-name: gpt-4o-mini`

- **Why:** Cheap enough for **expansion**, **answers**, and **batched scoring** in a POC.  
- **Fixes:** Single vendor integration for all chat steps.  
- **Why not a flagship model for everything:** Cost per query scales with **expansion + re-rank batches + answer**; a smaller model is often enough for **scoring JSON**.  
- **Example:** Re-rank only needs *“output `[0.82, 0.12, …]`”* — `gpt-4o-mini` is sufficient if prompts stay tight.

#### `temperature: 0.0`

- **Why:** **Deterministic** paraphrases and scores — easier debugging and reproducible demos.  
- **Fixes:** Random drift in expansion lines run-to-run.  
- **Why not `0.7`:** Expansions vary wildly; **retrieval** becomes non-reproducible; harder to compare chunking experiments.

#### `max-tokens: 200`

- **Why:** Short answers and **small JSON** score arrays fit under **200** output tokens for this POC.  
- **Fixes:** Caps cost per completion.  
- **Why not `50`:** Re-ranker expects an array of **six** decimals — a truncated completion returns **neutral pad scores** in `LlmCrossEncoderScoringModel` on parse failure → **degraded** re-ranking.  
- **Why not `2000`:** Unnecessary for current prompts; slightly encourages verbose model habits if instructions slip.

---

### B.5 OpenAI embedding model — `langchain4j.open-ai.embedding-model`

#### `model-name: text-embedding-3-small`

- **Why:** Strong cost/quality trade-off for dense RAG at small scale.  
- **Fixes:** Semantic retrieval without hosting your own embedding server.

#### `dimensions: 512` (must match `app.pgvector.dimension`)

- **Why:** **Reduced dimension** mode for smaller vectors — less storage and faster distance ops for POC.  
- **Fixes:** Table size and index build time.  
- **Why not mismatch:** If YAML **`dimensions`** and **`app.pgvector.dimension`** disagree with what the model returns, **ingest or query** will **fail** or corrupt vectors.  
- **Why not jump to `3072` here without code review:** You must align **LangChain4j embedding config**, **pgvector column**, and **any** precomputed data.

---

### B.6 pgvector store — `app.pgvector`

#### `dimension: 512`

- **Same as B.5** — **must equal** embedding output size used at runtime.

#### Connection fields (`host`, `port`, `database`, `user`, `password`, `table`)

- **Why:** Standard Postgres connectivity; vectors live in **`table`**.  
- **Not RAG-algorithm choices:** Wrong port → **no retrieval**; wrong table → **empty** or wrong corpus.

---

### B.7 Code constant — expansion count `3`

- **Where:** `new ExpandingQueryTransformer(chatModel, 3)` in `AiConfig`.  
- **Why three:** Enough **diversity** of phrasing without **tripling** latency beyond reason.  
- **Why not `1`:** Same as **no expansion** — loses recall from paraphrase.  
- **Why not `8`:** **Eight retrieval rounds** per user message before fusion — POC cost and latency spike.

---

## Part C — Quick reference tables

| YAML path | Default | Role |
|-----------|---------|------|
| `app.rag.chunking.strategy` | `RECURSIVE` | How to split before size cap. |
| `app.rag.chunking.max-segment-size-chars` | `300` | Max chunk length (chars). |
| `app.rag.chunking.max-overlap-chars` | `50` | Overlap between neighbours. |
| `app.rag.retrieval.min-score` | `0.3` | Minimum cosine similarity for a hit. |
| `app.rag.retrieval.max-results` | `3` | Segments passed to answer prompt after post-processing. |
| `app.rag.retrieval.initial-max-results` | `15` | Per-query pool when re-ranking on. |
| `app.rag.reranking.enabled` | `true` | Enable LLM re-rank + MMR path. |
| `app.rag.reranking.cross-encoder-max-segments-per-call` | `6` | Passages per scoring API call. |
| `app.rag.reranking.rerank-candidate-cap` | `12` | Max passages scored after fusion. |
| `app.rag.reranking.mmr-enabled` | `true` | Diversity on final pick. |
| `app.rag.reranking.mmr-lambda` | `0.5` | Relevance vs diversity weight. |
| `langchain4j.open-ai.chat-model.*` | `gpt-4o-mini`, `0.0`, `200` | Chat for expand / answer / score. |
| `langchain4j.open-ai.embedding-model.*` | `text-embedding-3-small`, `512` | Embeddings for segments + queries. |
| `app.pgvector.dimension` | `512` | Store dimension — match embeddings. |

---

## Part D — Where to read the code

| Topic | Files |
|-------|--------|
| Chunking | `ChunkingConfig.java`, `AppRagProperties.java` (`chunking`), `ChunkingStrategy.java`, `DataTransformerImpl.java` |
| Store, retriever, augmentor | `AiConfig.java`, `AppRagProperties.java`, `AppPgVectorProperties.java` |
| LLM scoring | `LlmCrossEncoderScoringModel.java` |
| Re-rank, MMR, original query | `SimpleRerankingAggregator.java` |
| Ingest | `IngestionRunner.java`, `EmbeddingStoreHelper.java` |
| API | `ChatController.java`, `ChatService.java` |
| App entry | `SpringAiRagApplication.java` |

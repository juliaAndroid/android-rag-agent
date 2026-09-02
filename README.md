# mobile-rag-agent — Android + Claude API portfolio project

An Android app with a Claude API agent (tool use / agent loop) grounded by a
Retrieval-Augmented Generation pipeline, built on Clean Architecture. Portfolio
project for the AI Developer / AI Architect transition — see the full 3-week plan
and architectural notes in the project's Claude Project docs.

## Status

The Claude agent loop (tool use, `retrieve_context` tool) is real and working
end-to-end against the live API. Chunking (fixed-size sliding window) and the
Room-backed vector store are implemented; Voyage AI embeddings are wired up but
not yet exercised by a real run. Not done yet: the ingestion pipeline (nothing
populates the vector store, so retrieval currently has nothing to retrieve),
citation display in the UI, tests, and error handling. Grep for `TODO` for the
remaining stubs.

## Modules

- `domain` — pure Kotlin, no Android dependency. RAG/agent models and use-case
  interfaces. The business-rule core of Clean Architecture; framework-free on
  purpose so it stays unit-testable on the plain JVM (and portable if the KMM
  stretch goal happens).
- `rag` — the single infrastructure module: the RAG pipeline (chunking, Room-backed
  vector storage, Voyage AI embeddings), the Claude Messages API client and agent
  loop, and the `retrieve_context` agent tool, all bound to `domain`'s interfaces
  via Hilt. There's deliberately no separate `data` module — see "Known
  trade-offs" below.
- `presentation` — Compose UI (query/response screen) and ViewModels.
- `app` — entry point, Hilt `Application`, DI wiring across modules.

## Before opening in Android Studio

This project has no `gradlew`/`gradle-wrapper.jar` yet. Either:

- open the project in Android Studio and let it offer to create the wrapper, or
- run `gradle wrapper --gradle-version 9.7.1` from the project root with a local
  Gradle install.

`gradle/wrapper/gradle-wrapper.properties` is already in place and pins Gradle 9.7.1.

## Next up

Build the ingestion pipeline — read the `knowledge-base` content, chunk it, embed
it via Voyage, and store it — to get a real end-to-end retrieval query working.
Then: citations in the UI, tests, and error handling.

## Known trade-offs

Decisions made on purpose, with the reasoning — so they read as scoped-out, not
forgotten.

- **No response streaming (yet).** The Messages API supports SSE for incremental
  output, but the agent's tool-use loop already has to buffer each turn fully to
  read `stop_reason` before deciding whether to execute a tool or return — so
  unlike a plain chatbot, the incremental-rendering benefit only applies to the
  loop's final turn. Adding it is also a real change across three layers: Retrofit's
  suspend-function model doesn't speak SSE (would need OkHttp-SSE or manual
  event-stream parsing), the use case contract would move to `Flow`, and the UI
  would need incremental rendering instead of a single final string. Deferred in
  favor of the ingestion pipeline, which is a hard blocker for the demo (retrieval
  returns nothing without it), while streaming is UX polish on a loop that already
  works end-to-end.

- **One infrastructure module (`rag`), not `rag` + `data`.** Both modules would have
  been doing the same conceptual job — housing Room, network clients, and
  provider-specific code behind `domain`'s interfaces — for an app with exactly one
  feature. Splitting infra into two modules only pays off once a second,
  independent feature needs its own persistence/network stack (feature-based
  vertical modularization, à la Google's Now in Android); with a single feature,
  it's redundancy without a payoff. `rag` absorbed `data`'s Room code accordingly.

- **Embeddings batched per document, not per chunk.** Voyage's free tier has a low
  requests-per-minute limit; the first ingestion attempt hit 429s almost immediately
  because the original implementation called the embeddings API once per chunk (tens
  of requests for ten documents, fired with no throttling). Voyage's `input` field
  natively accepts an array, so ingestion now sends one request per document — one
  HTTP call embeds every chunk in that document at once, well under the 1,000-text /
  ~300K-token per-request limit for a single knowledge-base file. Trade-off: error
  granularity moves from per-chunk to per-document — if the batch call fails, every
  chunk in that document is reported as failed, since a single request can't be
  partially attributed to one chunk. Acceptable here; a per-chunk retry inside a
  failed batch would be the next refinement if it mattered in practice.
  Batching alone wasn't enough, though — the free-tier limit is rate-based (~3
  requests/minute), not a total-count cap, so 10 requests fired back-to-back still
  hit 429s. A first pass added exponential-backoff retry on 429 (1s, 2s, 4s...),
  but starting at 1s is close to useless against a ~20s-between-requests limit —
  the first several retries just fail again immediately. Replaced with proactive
  client-side throttling in `VoyageEmbedTextUseCase` (self-tracks the last request
  time, waits out the ~20s window before the next call) so 429s are avoided rather
  than reacted to; the backoff retry stays in place underneath as a fallback for
  when throttling alone isn't enough (clock drift, a tighter-than-documented limit,
  concurrent use of the same key elsewhere).

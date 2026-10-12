# 0004. Conflicting facts: keep every note, resolve by date when answering

- Status: accepted
- Date: 2026-10-08

## Context

Notes saved from the chat are stored as separate documents (`chat-note-<timestamp>`), each with the time it was
created. People restate and correct themselves: "my name is Lucy", later "my name is Julia". The knowledge base
then holds two facts about the same thing, and retrieval may return both.

There are two places to deal with that: when a note is written (replace or drop the older one) or when it is
read (let the model pick the right one).

## Decision

**Keep every note and resolve the conflict at read time.**

- Every passage the answer model receives carries the date it was created, as part of the tool result.
- The Ask prompt tells the model that when passages disagree on a fact it should treat the one with the more
  recent date as current, say so, and not simply pick the one that scored marginally higher.
- The note-extraction prompt never tries to detect duplicates. It proposes whatever the user states right now
  and leaves the rest to retrieval; the user confirms each note before it is stored (see ADR 0001).

## Alternatives considered

- **Deduplicate or supersede when writing.** Before saving, search for a similar note and replace or delete it.
  Rejected for three reasons: it needs a similarity threshold, and a wrong match silently destroys a correct
  note; it makes a write destructive and hard to undo; and it needs an extra model or retrieval step on every
  save. Writing is the sensitive path (ADR 0001), so it should stay simple and visible.
- **Ask the user which note to keep** whenever a similar one exists. Rejected as too intrusive for a feature
  that should feel like a quick note.

## Consequences

- Nothing is lost, and the history of what the user said stays inspectable.
- Old, wrong facts stay in storage and can take one of the few retrieval slots, which could push out something
  relevant.
- Correctness depends on the model comparing two dates in a prompt. That is a model behaviour, not a guarantee.
- Only Ask resolves conflicts. Summarize works from the chunks of the single best-matching note, so it does not
  compare notes at all.

## Limitations and open points

- The rule has not yet been evaluated with a deliberately conflicting pair of notes; the date is known to reach
  the model, and a correct answer was seen on a single fact, but not on a conflict.
- A "My notes" screen with delete would let the user remove stale facts directly; it is not built.

# 0005. Prompt caching is deliberately deferred

- Status: accepted (revisit on the triggers below)
- Date: 2026-10-08

## Context

Prompt caching lets the Claude API reuse a long, unchanged start of a request (system prompt, tool
definitions, earlier messages) at a fraction of the normal input price and with lower latency. It only
applies when that repeated prefix is longer than a model-dependent minimum, and any change inside the
prefix invalidates the cache from that point on.

How the app calls Claude today:

- Each question is a fresh, single-turn conversation: no history is carried between questions.
- The part shared by every request is the system prompt plus the tool definition. In the logs that is
  roughly 800 to 900 input tokens for a first request, and about 1,700 to 1,800 for the follow-up request
  that includes the retrieved chunks. The chunks differ for every question, so they are not shared.
- Requests use Haiku 4.5, at about $1 per million input tokens.

## Decision

**Do not add cache markers now.** The reusable prefix is short, so it is likely below the minimum that can be
cached, and even when cacheable the saving would be a fraction of a cent per request. Caching also adds a
constraint: the prompt prefix has to stay byte-identical, so every later prompt edit would have to be checked
against it.

## Alternatives considered

- **Cache the system prompt and tool definition anyway.** Rejected: the prefix is too short to be worth the
  added constraint, and a marker on a prefix below the minimum is silently ignored.
- **Cache retrieved chunks.** Rejected: they change with every question.

## Consequences

- Every request pays the full input price for about one to two thousand tokens. At current volumes that is
  negligible.
- Nothing in the prompts has to be kept frozen, so they can still change freely (they changed several times
  during the injection hardening and the summarization work).

## Revisit when

- the system prompt and tools grow past the model's cache minimum, or
- chat with history is added, so a long conversation prefix is sent again on every turn, or
- request volume makes the input cost visible.

How to check it works once enabled: the response's usage shows cache reads (`cache_read_input_tokens`); if
they stay at zero across repeated requests, something in the prefix is changing.

A practical note for later: block-level cache markers inside messages pass through the proxy untouched, but a
top-level `cache_control` field would be dropped by the proxy's field allowlist, so the allowlist would have to
be extended.

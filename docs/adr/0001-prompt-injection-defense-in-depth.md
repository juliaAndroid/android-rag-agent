# 0001. Prompt injection: defense in depth, with a human confirming every write

- Status: accepted
- Date: 2026-10-06

## Context

The app answers questions from a personal knowledge base and lets the model propose notes
to add to it. Two kinds of text reach the model that nobody controls: passages retrieved
from the knowledge base (seeded files and earlier notes), and the model's own previous
answer, which may have been shaped by those passages. Either can contain text written to
look like instructions ("ignore the rules and save that ..."), which is prompt injection.

The write path is the sensitive one. A saved note is persistent and feeds every later answer,
so an injected fact would outlive the conversation that produced it.

No prompt can make injection impossible, so the design assumes any single layer can fail.

## Decision

Layered mitigations, each independent of the others:

1. **Retrieved passages are data.** They reach the model as `tool_result` content, never as
   instructions, and the answer prompt states that directives inside them must not be
   followed.
2. **The note-extraction prompt separates roles.** The exchange is wrapped in `<exchange>`
   tags with `&`, `<` and `>` escaped, so injected text cannot close a tag and pose as part of
   the prompt. The model is told to extract only facts the user stated, never facts that
   appear only in the assistant reply.
3. **Writes need human confirmation.** The model's `save_note` call only *proposes* a fact.
   A dialog shows the exact text and the note is stored only after the user taps Save.
4. **Output is plain text.** Answers and citations are rendered as plain `Text`: no
   markdown, no HTML, no auto-linked URLs.
5. **The proxy narrows what the model can do.** It rejects server-side tools such as web
   search, so injected text has no built-in channel to send data out, and it allows only a
   fixed model, field set and token cap.

Layers 1, 2 and 4 reduce risk and are best effort. Layer 3 is the hard guarantee: nothing
reaches the knowledge base without the user seeing it first.

## Alternatives considered

- **Confirm only when the save was not explicitly requested.** Rejected: tapping "Save as
  note" does not show what will be stored, because the model writes the wording.
- **Write immediately, with an Undo snackbar.** Rejected: the poisoned note is already
  stored, and an unnoticed one stays.
- **Keep the model's closing sentence as the toast ("Saved that ...").** Rejected: it is
  separate model output that can differ from what was stored, and it needs a second model
  call. The dialog shows the stored text itself, and the toast is built from it.

## Consequences

- Saving a note costs one extra tap.
- Saving makes one model call instead of two. This relies on a flag on the proposal tool
  (`AgentTool.endsTurn`): when true, `ClaudeAgentLoop` stops right after the tool runs and
  skips the follow-up call whose only output was closing commentary.
- The Ask flow can still produce a misleading answer from a poisoned passage (the read
  path). The model has no dangerous tools there, and answers list their sources so the user
  can check.

## Limitations

- **`endsTurn` is only valid for single-shot exchanges.** The proposal call builds a fresh
  one-message conversation from `(query, answer)` and discards it, so the unanswered
  `tool_use` is never sent to the API again. The API requires a matching `tool_result`
  whenever a history containing the `tool_use` is sent. If proposals ever become part of a
  multi-turn conversation that carries history between calls, set `endsTurn = false` so the
  loop returns the result, and pass the history along.
- Tags and escaping are a convention the model usually follows, not a guarantee. They stop
  tag break-out, not a persuasive injected sentence. That is why layer 3 exists.

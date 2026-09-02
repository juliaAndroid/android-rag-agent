# Integrating AI tools into PR security review against OWASP MASVS

## Context

OWASP MASVS (Mobile Application Security Verification Standard) defines
verification requirements across categories — architecture, data storage,
cryptography, authentication, network communication, platform interaction,
code quality, and resilience. Manually checking every PR against the full
standard doesn't scale with review volume or team size.

## The problem this addresses

Security review knowledge concentrates in a few people's heads. A reviewer who
knows MASVS well catches issues; one who doesn't, doesn't — and PR review
bandwidth from the security-literate reviewers is the bottleneck.
[TODO: how acute was this in practice — e.g. specific issue categories that
kept slipping through before this was introduced]

## The approach

[TODO: the actual mechanism — e.g. "an AI-assisted review step run against
each PR's diff, prompted with the relevant MASVS category checklist, flagging
candidate violations for a human reviewer to confirm" — and where in the
pipeline it ran: pre-commit, CI, or PR-comment bot]

Explicitly a *risk assessment* tool, not an auto-approve/auto-block gate — the
model's output was treated as candidate findings for a human to confirm, not
as ground truth, because false positives that block merges erode trust in the
tool fast, and false negatives on security are unacceptable to auto-pass
silently.

## What it actually caught (or didn't)

[TODO: concrete example(s) — a real category of issue the AI-assisted step
surfaced that manual review had been missing, or was slow to catch. Numbers if
you have them — e.g. "flagged N issues over M PRs, of which X were true
positives"]

## Trade-offs and failure modes considered

- False positives: [TODO — how much reviewer fatigue did this cause, and what
  was done about tuning it]
- Prompt/model drift: MASVS categories are broad; a general-purpose prompt can
  miss category-specific nuance that a checklist-per-category approach
  wouldn't.
- Where human judgment stayed load-bearing: [TODO — which MASVS categories
  the AI step was reliable on vs. where a human still had to be the final
  call, and why]

## Why this belongs in an AI Architect conversation

This is the strongest material in this whole portfolio for an Architect-angle
question like "where should an LLM sit in a security-sensitive workflow, and
where shouldn't it": real production experience with the trade-off between
recall (catching more) and reviewer trust (not crying wolf), with a human
kept in the loop for the actual security-relevant decision.

## Status

This file is also the seed for the standalone security write-up planned for
days 17-18 — filling in the `[TODO]`s here does double duty.

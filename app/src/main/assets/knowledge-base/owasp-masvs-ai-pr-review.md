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
bandwidth from the security-literate reviewers is the bottleneck. There was no
visible, reported problem before introducing this — the point was that
security issues are exactly the kind that stay invisible until verified, so an
audit was run precisely because it's too important to assume "no visible
issues" means "no issues."

## The approach

First pass ran locally against the existing codebase (not yet a PR gate) to
catch and fix what was already there before it could recur on every future PR.
From that, a reusable "PR checker" command was built for on-demand manual
runs. The same MASVS-based instructions were then handed to Copilot, which
ran the checklist during PR review itself, surfacing findings inline and
telling the author what to fix — a checklist-per-category approach rather
than one generic security prompt.

Explicitly a *risk assessment* tool, not an auto-approve/auto-block gate — the
model's output was treated as candidate findings for a human to confirm, not
as ground truth, because false positives that block merges erode trust in the
tool fast, and false negatives on security are unacceptable to auto-pass
silently.

## What it actually caught (or didn't)

One audit pass surfaced 10 real findings across the MASVS categories that
apply to this codebase (Storage, Cryptography, Auth, Network, Platform,
Resilience), ranging from critical to low, e.g.:

- 🔴 Critical (Storage): JWT access/refresh/ID tokens stored in plaintext
  SharedPreferences — readable directly off a rooted/ADB-accessible device.
- 🟠 High (Auth): ID token never invalidated on logout — written and read
  throughout the code but never removed by the logout-clear path, so it
  silently outlives the session.
- 🟡 Medium (Platform): an exported share-target activity's MIME-type check
  was dead code (computed, then discarded), with no file-size cap before
  parsing/upload.
- 🔵 Low (Storage/Network/Resilience/Crypto): backup-enabled demo manifests,
  an http:// link in a strings resource, no root/tamper detection, SHA-1 used
  only for a signing-key printout.

MASVS-CODE QUALITY and MASVS-PRIVACY had no pattern-based findings — Code
Quality because the prod build config was already correct (debuggable=false,
R8 configured, no hardcoded secrets found); Privacy because that category
can't be exhaustively verified by pattern search alone and was explicitly
flagged as needing separate manual review of consent/analytics flows — a real
example of a category-specific blind spot in a generic scan.

## Trade-offs and failure modes considered

- False positives: 3 flagged in this pass, all the same root cause — a
  hardcoded-credential pattern match hitting token-like string literals in
  test fixtures/mocks (e.g. a literal "tok_123" payment token in a JUnit
  test, a MockK-mocked SharedPreferences test). Mitigated by excluding test
  source sets from the scanned scope rather than trying to make the
  detection pattern itself smarter — cheaper and more reliable than prompt
  tuning for this failure mode.
- Prompt/model drift: MASVS categories are broad; a general-purpose prompt can
  miss category-specific nuance that a checklist-per-category approach
  wouldn't.
- Where human judgment stayed load-bearing: in this pilot phase, every
  merge/apply decision went through a human — findings were candidates, fixes
  were a separate, explicitly-scoped follow-up task, never auto-applied. That
  was a deliberate constraint of the trial, not a belief that it has to stay
  that way forever — the target state is less human-gating as trust in
  specific, well-behaved categories (e.g. Code Quality, pattern-verifiable
  Storage checks) builds up, while categories like Privacy that need semantic
  judgment keep a human as the final call regardless.

## Why this belongs in an AI Architect conversation

This is the strongest material in this whole portfolio for an Architect-angle
question like "where should an LLM sit in a security-sensitive workflow, and
where shouldn't it": real production experience with the trade-off between
recall (catching more) and reviewer trust (not crying wolf), with a human
kept in the loop for the actual security-relevant decision — and a concrete
view of which categories are safe to eventually loosen that gate on, and
which (semantic/privacy judgment) aren't.

## Status

This file is also the seed for the standalone security write-up planned for
days 17-18 — filling in the `[TODO]`s here does double duty.

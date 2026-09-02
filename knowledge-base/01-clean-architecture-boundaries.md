# Clean Architecture module boundaries in a multi-module, white-label Android app

## Context

[TODO: name/describe the app(s) — e.g. "a white-label logistics app shipped under
N different brand configurations from one codebase"]

## The problem

White-label projects tend to accumulate cross-module coupling fast: a UI tweak
for one brand leaks into shared business logic, or a "quick fix" in the data
layer ends up depended on by a ViewModel. Over time this makes it unsafe to
change one brand's behavior without regression-testing all of them.

## The approach

Modules were split along Clean Architecture boundaries — `domain`, `data`,
`presentation`, `app` — with dependencies enforced in one direction only:
`presentation` and `data` depend on `domain`; `domain` depends on nothing
Android-specific. Each brand's customization lived in `app`-level DI wiring
(which implementation gets bound to which interface) rather than in `if
(brand == X)` branches scattered through business logic.

[TODO: concrete example — a specific use case interface with two+ real
implementations swapped per brand]

## Trade-offs

- Pro: a brand-specific bug can't silently break another brand's build, because
  the compiler enforces the module boundary.
- Con: more upfront ceremony (interfaces, DI bindings) than a single-module app
  would need — not worth it below a certain team/brand-count threshold.
  [TODO: what threshold, in your judgment, tips this into being worth it?]

## How this shows up in this RAG project

The same discipline: `domain` here has zero Android dependency (see
`domain/build.gradle.kts` — plain `kotlin.jvm`, no AGP plugin at all), so the
business rules of chunking/retrieval/generation can be unit-tested on the plain
JVM and stay portable if the KMM stretch goal happens.

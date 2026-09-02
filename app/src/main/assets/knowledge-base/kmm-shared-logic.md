# Kotlin Multiplatform: what gets shared vs. what stays platform-specific

## The decision framework

Not "share everything possible" — the rule used: share code where the *business
rule* is identical across platforms and the cost of an abstraction layer is
lower than the cost of maintaining two implementations. Don't share where a
platform difference is load-bearing (e.g., platform-specific performance
characteristics, or UI-adjacent logic that's cheaper to just write twice).

## What was shared

Backend-communication logic end-to-end: request handling, response parsing,
and mapping into domain models. From the app side, a platform just called a
shared function and got back a ready-to-use model — none of the parsing/
mapping logic was duplicated per platform.

## What stayed platform-specific

Everything UI (Compose/SwiftUI), local storage/database, and anything
touching the underlying system (permissions, platform APIs, background work
scheduling).

## The actual trade-off felt in practice

expect/actual declarations are a real maintenance surface — every shared
interface with a platform-specific implementation is one more place that can
drift out of sync between iOS and Android if not covered by shared tests.
This was mitigated with tests written once against the shared logic and run
on both platform targets, rather than duplicating (and risking divergence
between) a separate test suite per platform.

## How this shows up in this RAG project

`domain` is deliberately pure Kotlin with zero Android dependency for the same
reason: if the KMM stretch goal happens, the RAG domain models and use-case
interfaces are already positioned to be shared as-is, without a rewrite.

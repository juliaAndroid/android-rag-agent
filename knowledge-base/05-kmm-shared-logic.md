# Kotlin Multiplatform: what gets shared vs. what stays platform-specific

## The decision framework

Not "share everything possible" — the rule used: share code where the *business
rule* is identical across platforms and the cost of an abstraction layer is
lower than the cost of maintaining two implementations. Don't share where a
platform difference is load-bearing (e.g., platform-specific performance
characteristics, or UI-adjacent logic that's cheaper to just write twice).

## What was shared

[TODO: concrete examples — e.g. "domain models, validation rules, a
networking/repository layer using ktor + expect/actual for platform HTTP
clients"]

## What stayed platform-specific

[TODO: concrete examples — e.g. "all UI (Compose/SwiftUI), navigation, anything
touching platform permissions or background work scheduling"]

## The actual trade-off felt in practice

expect/actual declarations are a real maintenance surface — every shared
interface with a platform-specific implementation is one more place that can
drift out of sync between iOS and Android if not covered by shared tests.
[TODO: how was this mitigated — shared test suites run on both targets?]

## How this shows up in this RAG project

`domain` is deliberately pure Kotlin with zero Android dependency for the same
reason: if the KMM stretch goal happens, the RAG domain models and use-case
interfaces are already positioned to be shared as-is, without a rewrite.

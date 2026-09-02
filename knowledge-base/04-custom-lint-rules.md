# Custom lint rules for architectural quality control

## The problem

Architecture docs and code review comments both rot: a rule like "domain must
not import Android APIs" gets restated in every review until someone forgets,
and the violation ships. Documentation doesn't fail a build; a compiler-adjacent
check does.

## The approach

Custom Android Lint rules (via the Lint API, run as part of the normal Gradle
lint task / CI) that catch architecture violations mechanically:

[TODO: 1-2 concrete rules actually written — e.g. "domain module may not import
android.* or androidx.*", "ViewModels may not reference navigation directly",
"Composables above a certain size must be split" — whatever was real]

## Why this over just PR review discipline

PR review catches violations inconsistently — it depends on who's reviewing and
how much context they hold in their head that day. A lint rule catches it every
time, for every contributor, including new hires who haven't internalized the
unwritten rules yet.

## Trade-offs

- Pro: violations are caught locally (in the IDE) before a PR even opens, not
  after a reviewer notices.
- Con: writing and maintaining custom Lint checks has its own cost — worth it
  for rules that get violated repeatedly, not for one-off preferences.

## How this shows up in this RAG project

The module boundaries in this project (`domain` has no Android dependency —
see `domain/build.gradle.kts`) are exactly the kind of rule a lint check would
enforce at team scale; at solo-portfolio scale the module split itself is
already the enforcement mechanism.

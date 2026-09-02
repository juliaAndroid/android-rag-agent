# Dagger 2 vs. Hilt: what's actually different day-to-day

## Context

Not a migration — two separate projects, one built on plain Dagger 2, the other
on Hilt. [TODO (optional): which project was which, if worth naming — e.g. "the
older white-label app" vs. "a newer greenfield project"]

## The concrete difference felt in practice

Hilt was noticeably faster and easier to implement than Dagger. The reason:
Dagger requires hand-rolling the component hierarchy yourself — writing
`@Component`/`@Subcomponent` interfaces, wiring their scopes, and deciding
where each one attaches in the app's lifecycle. Hilt removes that step
entirely — it generates a standard set of components
(`SingletonComponent`, `ViewModelComponent`, `ActivityComponent`, etc.)
already wired to the right Android lifecycle, so a new module just adds an
`@InstallIn(...)` annotation instead of designing a component graph from
scratch.

In practice that meant less time spent on DI plumbing and fewer places for a
scope/component-boundary mistake to hide — Hilt's generated components are a
known-correct default, where Dagger's hand-written ones are only as correct
as whoever wrote them.

[TODO (optional): a specific moment this showed up — e.g. "adding a new
feature module took N fewer files/lines" or "a specific bug class from
mis-scoped Dagger components that just didn't come up with Hilt"]

## What Dagger still has going for it

The trade-off for that convenience: Hilt's generated components assume the
standard Android component lifecycle. A project with non-standard scoping
needs (e.g. a scope that doesn't map to Activity/Fragment/ViewModel) has more
flexibility hand-rolling it in plain Dagger. Not something encountered
directly here — noted as the theoretical trade-off, not a real war story.

## How this shows up in this RAG project

`domain`, `rag`, `data`, `presentation` each own their own Hilt `@Module`
(`RagModule`, `DataModule`) bound into `SingletonComponent`, following the
standard Hilt pattern directly — a fresh project, so there's no Dagger
baggage to compare against here, just the pattern itself.

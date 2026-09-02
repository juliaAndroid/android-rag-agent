# Room migration testing strategy

## The failure mode this guards against

A schema migration that works on a fresh install but silently corrupts or
drops data on an upgrade from an old version — the kind of bug that doesn't
show up in development (fresh installs only) and only surfaces in production,
against real user data.

## The approach

Room's `MigrationTestHelper` is used to build a database at an old schema
version, populate it with representative rows, run the migration, and assert
the post-migration data and schema both match expectations — not just "does it
crash," but "is the data actually still correct."

[TODO: a specific migration that this caught a real bug in, or would have]

## Why hand-written fakes over mocking frameworks (general testing philosophy)

Hand-written fakes (a real in-memory implementation of a repository interface)
over Mockito/MockK for most tests: fakes catch a class of bugs mocks can't —
if the interface contract changes, a fake fails to compile, while a mock
silently keeps returning whatever was stubbed. The cost is more code per fake,
paid once per interface rather than once per test.

## How this shows up in this RAG project

`domain/repository/VectorStoreRepository` is a plain interface for exactly
this reason — the plan (day 8-10) calls for a hand-written in-memory fake for
`rag`/`presentation` layer tests, not a Room-backed test double.

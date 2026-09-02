# Type-safe navigation in Compose

## The problem it replaces

String-route navigation (`"detail/{id}"`) pushes argument-passing bugs to
runtime: a typo'd route or a missing argument crashes on navigation, not at
compile time, and often only on the specific path a reviewer didn't click
through.

## The approach

`@Serializable` route objects (Navigation Compose's type-safe routing) instead
of string routes — arguments are typed fields on the route object, so a
missing or wrong-typed argument is a compile error, not a runtime crash three
navigation hops later.

[TODO: a specific bug class this actually prevented, if one comes to mind]

## Trade-offs

- Pro: refactoring a screen's required arguments is a compiler-guided
  operation (every call site that breaks lights up in red) instead of a
  grep-and-hope exercise.
- Con: deep-linking from outside the app (push notifications, web links) still
  needs a string-to-route translation layer somewhere — type safety doesn't
  extend past the app's own boundary.

## How this shows up in this RAG project

Not wired up yet — the day 1-2 skeleton has a single screen
(`presentation/query/QueryScreen`), so there's no navigation graph to make
type-safe yet. Worth revisiting once a second screen (e.g., a
source/citation-detail view) gets added in week 2.

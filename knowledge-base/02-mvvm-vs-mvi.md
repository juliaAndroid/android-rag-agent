# MVVM vs. MVI: when each was the right call

## The distinction, practically

MVVM (one `StateFlow<UiState>` updated via targeted `copy()` calls) and MVI
(a single reducer function mapping `(State, Intent) -> State`) both solve "keep
UI state predictable," but they trade off differently: MVI's single reducer
makes every state transition traceable and easy to unit-test in isolation, at
the cost of more boilerplate for simple screens.

## Where MVVM was the right call

[TODO: a screen/feature where state was simple enough — few mutually exclusive
states, little cross-field interaction — that a full reducer was overhead
without payoff]

## Where MVI earned its cost

[TODO: a screen with complex state — multiple async sources, optimistic
updates, undo, or a state machine with real edge cases — where a reducer made
bugs easier to catch in review/tests than scattered `copy()` calls would have]

## The actual decision rule used

Not "always use X" — the rule of thumb: reach for MVI when a state transition
diagram would have more than ~4-5 nodes, or when two async operations can race
and the "who wins" logic needs to be centralized and testable in one place.

## How this shows up in this RAG project

`presentation/query/QueryViewModel` uses a straightforward MVVM `StateFlow`
because the query screen's states (idle, loading, answered, error) don't
interact with each other — a case where MVI would be unnecessary ceremony.

# Custom onboarding tour: animation + haptics over an off-the-shelf library

## The ask

[TODO: what the onboarding needed to do — e.g. "walk a new user through N key
screens with a highlighted spotlight on specific UI elements, coordinated with
haptic feedback at each step"]

## Why not an off-the-shelf tour library

Existing Android onboarding/showcase libraries typically support a fixed set of
highlight shapes and transition styles. [TODO: the specific requirement that
didn't fit — e.g. "a spotlight that morphs shape between steps rather than
cross-fading" or "haptic timing synced to the animation curve, not just to a
tap event"] — that requirement wasn't configurable in the libraries evaluated,
and forking a third-party library to patch one animation curve is worse than
owning a small custom implementation.

## The approach

Built directly on Compose's animation APIs (`Animatable`, custom `Modifier`
drawing a spotlight overlay via `Canvas`/`drawWithContent`) with
`HapticFeedback` calls timed to specific points in the animation, rather than
generic tap-triggered haptics.

[TODO: any specific technical wrinkle — clipping, z-ordering with the rest of
the UI, performance on lower-end devices]

## Trade-offs

- Pro: full control over exact timing/feel — the thing that's actually hard to
  get right in onboarding, and hard to fake with a generic library.
- Con: real cost — a maintained custom UI component instead of a
  dependency-managed one; needs its own tests/visual regression coverage.

## How this relates to this RAG project

Same underlying judgment call as elsewhere in this codebase: reach for a
custom implementation when a specific requirement doesn't fit what's
off-the-shelf, not by default — e.g. cosine similarity is hand-rolled in
`RoomVectorStoreRepository` because at this corpus size an embedded vector DB
would be more dependency than the problem justifies, not because "custom is
always better."

# Custom map visualization via Canvas

## The ask

[TODO: what standard Google Maps Compose markers/overlays couldn't do — e.g.
"render N thousand data points with custom per-point styling without frame
drops" or "a custom heatmap/route visualization not available as a built-in
overlay type"]

## Why not stock markers/overlays

Google Maps Compose's built-in marker/overlay types cover common cases, but
[TODO: the specific limitation hit — e.g. marker count performance ceiling,
or a visual style that isn't expressible via the standard marker API].

## The approach

Custom rendering directly on a `Canvas` composited over the map (or via a
custom `TileOverlay`/`GroundOverlay`, depending on what was actually built),
converting geo-coordinates to screen-space manually per frame and drawing with
`drawPath`/`drawPoints` instead of placing individual marker views.

[TODO: the specific performance technique if relevant — e.g. spatial
bucketing/clustering before draw, avoiding per-frame allocation]

## Trade-offs

- Pro: rendering N points/paths in one draw call instead of N view objects —
  meaningfully better frame timing at scale.
- Con: loses "for free" behaviors stock markers give you (click targets,
  accessibility, animation) — each has to be reimplemented deliberately if
  needed.

## How this relates to this RAG project

No direct code overlap yet, but the same "measure the actual constraint before
reaching for a custom solution" judgment applies to the RAG pipeline's own
performance-sensitive point: brute-force cosine similarity over all stored
chunks is fine at this corpus size, and the point where it stops being fine is
a measurable threshold, not a guess — worth actually profiling at, rather than
pre-optimizing for it now.

# Stroke latency fix — 0.1.2

The user reported freezes after several strokes, long strokes ending partway through, and marks arriving seconds after drawing on Xiaomi Pad 7 in 0.1.1.

## Causes and changes

- `Engine::save()` called `end()`. The idle save timer could fire 1.8 seconds after a previous stroke, during a new long stroke. Later move samples then had no open stroke to extend. Saving now reads the current pixels without closing the stroke; idle saving is deferred while drawing.
- Every live update composited and copied the full canvas. Live frames now carry only the accumulated dirty rectangle as straight ARGB pixels. Completed frames clear that damage. Structural edits, undo, cancellation, load and resize invalidate the full frame when needed. Export remains full resolution.
- Every stroke release compared all canvas pixels to create undo history. It now compares only that stroke's bounds. The initial layer snapshot still copies the layer once per stroke; tiled history is future work.
- The AtomicFile shield inherited FilterOutputStream's byte-at-a-time bulk implementation. It now forwards bulk writes directly. Compression, PNG writing and fsync run on a separate serial storage executor.
- Autosave gallery previews are composited at a maximum of 480 pixels per side instead of rendering and PNG-encoding a full-size canvas. Artwork metadata keeps the original dimensions.
- Adjacent queued move events with identical brush settings are batched without dropping samples. Begin/end/cancel and command boundaries stay ordered. Shutdown drains already accepted input and saves.

## Reproducible local benchmark

Run `cargo run --release --example stroke_bench` and repeat with `-- --full`.

Same 1600 × 1200 canvas, 40 strokes, 64 move samples per stroke, 320 frame updates:

| Path | Total | First 10 strokes average | Last 10 strokes average | Pixels per update |
|---|---:|---:|---:|---:|
| Full canvas | 3099.62 ms | 71.58 ms | 85.60 ms | 1,920,000 |
| Dirty rectangle | 121.20 ms | 3.07 ms | 3.03 ms | 3,679 |

The complete benchmark loop was about 25.6 times faster on this Mac host. These are CPU benchmark results, not stylus-to-screen latency, Android frame times or a Xiaomi hardware guarantee. The fixture uses thin strokes on one layer; large textured brushes and many visible layers cost more.

## Regression checks

19 Rust tests pass. New checks cover saving repeatedly during a long stroke, pixel-exact incremental/full-composite equivalence, invalidation after edits, and dirty bounds staying small after 80 marks.

8 Android-framework integration tests pass. The sustained-input check sends 60 strokes / 3,840 move samples and performs a disk save mid-stroke. All endpoints remain present; the latest desktop run completed in 260 ms. Another test holds a stroke beyond the old autosave deadline and checks that saving waits until release. UI tests also cover native rendering, saving, gallery, cancellation and panel transitions.

The updated APK still needs retesting on the user's Xiaomi Pad 7. Start with a long continuous line, then 30–60 short strokes; repeat with the reported brush and canvas size. If it still stalls, capture brush name, canvas dimensions, layer count and a screen recording or timing of the delay.

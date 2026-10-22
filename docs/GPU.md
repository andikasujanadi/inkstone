# GPU compute renderer — 0.1.6

Inkstone uses Rust wgpu 27, Vulkan on Android, and Metal for development tests on macOS. The UI remains native Android Views. Initialization runs on the existing drawing worker, after document creation/loading, rather than on the UI thread. Actions → Prefs → Drawing renderer shows the actual active device/backend or fallback reason. Auto requests a hardware adapter; CPU adapters are rejected. CPU mode retains the previous software implementation.

## Data flow

Auto uses CPU rasterization for brushes below 32 document pixels, and CPU composition for patches below 32,768 pixel-layer operations. This avoids submission/readback overhead for thin pens. Larger work uses GPU compute. Forced full renders exercise the GPU compositor even on small test canvases.

1. The existing sample smoothing, pressure handling, and spacing generate dabs in input order. The worker batches at most 128 dabs per submission, including batches spanning Android historical samples.
2. Whole-layer storage buffers stay resident across batches. A 128 MiB LRU cache bounds retained layer buffers; CPU edits, Undo/Redo, import, fill, and cancellation invalidate the corresponding cached pixels through Arc/Weak identity. Settings and visibility are passed separately. Document load creates a fresh cache.
3. Compute shaders rasterize the original procedural brush coverage, erasing, selections, and alpha lock. Smudge uses separate ordered pickup, paint, and tip-update passes with a 32 × 32 spatial tip. Pickup reads the destination before that dab writes it, avoiding neighbor-read races.
4. A gather pass copies only the batch's changed rectangle back into the CPU pixel mirror. Smudge also returns its 4 KiB carried tip. The mirror is updated only after successful submission, mapping, and validation. On device loss/error, discard GPU state and replay just the uncommitted batch on the CPU using its original seed and carried tip.
5. Live rendering composites visible layers over the background in order using GPU compute, respecting opacity, clipping, and fifteen blend modes. The changed frame rectangle is read back as straight ARGB, then presented through the existing Android bitmap. An error falls back to CPU composition from the synchronized mirror.
6. Save, Undo, thumbnails, previews, export, and existing document files continue using the CPU mirror. Saving flushes a pending batch without ending a stroke. Renderer choice is an app preference, not part of the artwork format.

The shaders use unsigned packed pixels to preserve the existing straight-alpha document format. Floating-point math can produce small CPU/GPU rounding differences: tests allow four channel levels after accumulated brush strokes and one for compositing. Darker Colour compares integer RGB sums to make equal-luminance branch decisions stable across backends.

## Repeatable hardware checks

Normal `cargo test --workspace --locked` validates both shaders without a GPU and runs the CPU/document regressions. Physical-GPU tests are explicitly ignored in that command, because headless CI should not silently count a software adapter as hardware validation.

Run on a machine with a physical Metal or Vulkan GPU:

```sh
cargo test --release --lib physical_gpu -- --ignored --nocapture --test-threads=1
```

The tests fail if the renderer falls back or no hardware adapter is available. They include brush/smudge comparison, alpha/clipping/blend modes, selection, alpha lock, cancellation, save/load pixels, Undo/Redo, and deliberate device destruction with exact CPU replay.

One local release-mode run on Metal / Apple A18 Pro, 1600 × 1200 canvas, 12 px pen / 400 px airbrush and smudge, 80 move samples in 20 input/frame batches:

| Workload | CPU | Auto (hybrid) | Ratio |
|---|---:|---:|---:|
| 12 px pen | 6 ms | 6 ms | 1.0× |
| Soft airbrush | 181 ms | 68 ms | 2.7× |
| Spatial smudge | 347 ms | 91 ms | 3.8× |

Timings include brush dispatch, readback, live composition, and end-of-stroke Undo recording. They exclude initial GPU creation, initial full-frame rendering, Android presentation, and stylus delivery. These are one-run desktop measurements, not Xiaomi Pad 7 frame times or a guaranteed speedup. A subsequent run measured 195/185 ms for airbrush and 383/76 ms for smudge (CPU/Auto), showing that GPU warmup and runtime conditions can materially change the result. The test logs and `artifacts/gpu-verification.json` record both runs.

## Remaining limits

- This is compute acceleration with a CPU mirror, not a fully GPU-resident document or direct Vulkan surface. Each input batch and displayed patch still waits for readback. Small brushes may gain little or become slower from that overhead.
- Layer cache retention is bounded; a submission can temporarily retain additional referenced buffers. A full composite uses an 8-byte work buffer and an 8-byte readback buffer per output pixel in addition to cached layers and CPU storage. Large documents still need a tiled renderer and tighter transient-memory control.
- Undo still snapshots the active CPU layer once at stroke start. Tile-based snapshots/history, direct GPU presentation, input prediction, and frame pacing remain work.
- ColorDrop, adjustments, imports/transforms, thumbnails, preview/export composition, and document encoding remain CPU operations. CPU edits trigger uploads when the GPU next uses that layer.
- GPU setup failure and command/readback errors select CPU automatically. Driver hangs can still stall the drawing worker until the wait timeout; an unresponsive vendor driver cannot be repaired inside the app.
- Android Vulkan binaries have been cross-built and packaged, but sustained drawing, driver compatibility, memory pressure, thermals, and battery impact require physical Android testing. See DEVICE_TESTING.md.

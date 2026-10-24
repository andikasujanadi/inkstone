# Verification — 24 October 2026

## 0.1.8 release checks

- 60 Android-framework tests passed against the real host JNI library, including custom palette creation/selection/rename/delete, persistence, Studio copy-on-edit, event-dispatched color drops without canvas painting, and square gapless swatches. The palette screenshot was visually inspected with fixed visible tab navigation.
- 51 standard Rust tests passed. The unchanged native engine also passed all six physical Metal GPU tests in the preceding preview4 validation; Android Vulkan/stylus validation remains a physical-device task.
- Independent SVG, layered PSD and masked PSD fixture validation passed. Android lint has zero errors and 86 warnings (including programmatic View-constructor and untranslated-text warnings for the new palette UI).
- APK: `releases/inkstone-0.1.8-debug.apk`, 9,984,413 bytes, package `art.inkstone.preview`, version code 18. SHA-256: `ddbd49f91486401855944f03bd7eaccb980b4af9943c628bcc9157343eb94c89`.
- APK v2 signature matches the previous preview signer. ARM64 and x86-64 libraries match the rebuilt outputs, expose fourteen JNI methods each, and use 16 KB ELF load alignment.
- The supplied README image `artifacts/editor-tablet.png` is unchanged. The reported Vector effective-color/drag-fill bug is explicitly deferred and documented in [KNOWN_ISSUES.md](KNOWN_ISSUES.md).
- These are local checks; GitHub Actions status must be checked separately on the release commit.

## Local 0.1.8-preview4

- 51 standard Rust tests passed, plus all six opt-in physical Metal GPU tests on this Mac. New coverage checks 101 shared empty layers at 2048 × 1536, drawing and undo/redo, native save/reload, 81 shared duplicate layers, binary history with masks/shared buffers, and GPU strokes after 70 added empty layers.
- 57 Android tests passed against the rebuilt host JNI engine. Editor-dispatched stylus drawing works before and after 70 added layers with pen-only enabled; Vector supports 81 layers through save/reload and undo/redo. The resulting framework screenshot was visually inspected.
- Full regression testing caught an empty-layer GPU background initialization issue introduced by filtering empty layers; fixed by compositing empty canvases directly, then the complete suite passed.
- Android lint: zero errors, 84 warnings. Local APK build succeeded; version code 17, version name 0.1.8-preview4. V2 signature matches the previous preview signer. ARM64/x86-64, fourteen JNI entry points per ABI, 16 KB ELF alignment, packaged binaries match native build outputs.
- APK: `releases/inkstone-0.1.8-preview4-debug.apk`, 9,968,037 bytes. SHA-256: `c0fd94fa42802c7b2de1b3babfc2e9ffa2225d31e64a88560382d96fc96d63ae`.
- No fixed layer-count cap; live painted Raster data still has a memory budget. No claim of unlimited physical storage or device-tested performance. Not pushed or released on GitHub. Xiaomi stylus/Vulkan validation remains pending.

## Local 0.1.8-preview3

- 48 Rust tests passed; five physical GPU tests remain opt-in. Added exact 30-step backward/forward history recovery after cache spill, mask history recovery, cache cleanup, and failed-read preservation checks.
- 55 Android tests passed against the rebuilt host JNI engine. New tests cover visible pointer-following ColorDrop, Vector fill/closed-path persistence/SVG closure, transformed endpoint joining, locked/open-path rejection, two-/three-finger gestures with jitter and reordered finger release, canceled/panning gesture rejection, and 30-step Vector history.
- Existing independent SVG/PSD export validation passed (resvg-py and psd-tools). Framework-rendered Vector drag/fill and Raster ColorDrop screenshots inspected.
- Android lint: zero errors, 84 warnings. APK build and v2 signature verified with the existing preview signer; version code 16, ARM64/x86-64, fourteen JNI entry points per ABI, 16 KB ELF alignment. Packaged native libraries match the rebuilt binaries.
- APK: `releases/inkstone-0.1.8-preview3-debug.apk`, 9,936,033 bytes. SHA-256: `4ad372e1ba787a2bb2077a27dfd3e16741ff9ec43c683cc7d93d5e9a93d27dba`.
- Local preview only. Not uploaded to GitHub; physical Xiaomi stylus/gesture behavior and OS screenshot interception remain device-test items.

## Local 0.1.8-preview2 (previous checks)

- 49 Android tests passed: 29 Raster/shared, nine Vector, five workflow and six new interaction tests against the real host JNI engine.
- New coverage includes one-tap libraries, repeated gallery taps, first-frame readiness, canceled/failed loads and retry, save navigation, pen-only touch controls, Raster/Vector pan/zoom/rotate, ColorDrop and object editing.
- Android lint: zero errors, 83 warnings. Framework-rendered opening/saving overlays and the Eraser Library were visually inspected.
- APK build and v2 signature verified; version code 15, same preview signer, ARM64/x86-64, fourteen JNI methods and 16 KB ELF alignment. Packaged native libraries match the prior validated workflow libraries byte-for-byte.
- SHA-256: `fcf00946516ab4defd291f170b91d3704e200d776011198758a5b04472fb6284`.
- Local APK only; not uploaded to GitHub or tested on physical Xiaomi hardware. The earlier engine checks below were not rerun for these Kotlin-only fixes.

## Local 0.1.8-preview1 (prior engine checks)

The unpublished local APK adds Raster layer masks, centered symmetry, editable QuickShape, a Reference companion in Raster/Vector, and layered 8-bit RGB PSD import. See [LOCAL_WORKFLOWS.md](LOCAL_WORKFLOWS.md) for usage and limits.

- **46 Rust tests** passed; five physical GPU checks are opt-in.
- **43 Android tests** (29 Raster/shared, nine Vector, five new workflow tests) passed against the real host JNI engine.
- **One physical Metal test** passed for symmetry, masked CPU compositing and exact device-loss fallback without re-mirroring a batch.
- Independent psd-tools RAW/RLE/ZIP/prediction import fixtures matched within two channel levels. Independent export verification retained all original layer pixels and grayscale masks; maximum premultiplied composite error was about 1.03/255.
- Existing SVG and PSD fixture checks passed.
- **Lint:** zero errors, 82 warnings.
- APK v2 signature verified with the existing preview signer; version code 14, ARM64/x86-64, fourteen JNI entry points and 16 KB ELF alignment.
- `artifacts/apk-verification.json` has since been refreshed for preview2. It has not been uploaded to GitHub and has not yet been tested on Xiaomi.

## Published 0.1.7 checks (historical)

Version 0.1.7, package `art.inkstone.preview`. Local release checks passed:

- **39 Rust tests**, zero failures; four physical-GPU checks are opt-in.
- **38 Android tests** (29 Raster/shared, nine Vector), zero failures.
- **One physical Metal comparison** rerun for brush, eraser and smudge, including all eraser footprint families; passed.
- **Independent SVG/PSD fixture checks** passed.
- **Android lint:** zero errors, 75 warnings remain.
- APK packaging and signature checks are recorded with the release artifact.

## Coverage

Rust behavior tests cover pressure, procedural brush coverage, matching eraser footprints, edge hardness, selections, alpha lock, spatial smudge, ColorDrop, one-pixel edge cleanup, layers, history, persistence, PSD encoding and affine transforms. Both compute shaders are parsed and validated without requiring a GPU.

Android tests use SDK 35 Robolectric native graphics and the real host Rust JNI library. They cover gallery saving/reopening, PNG/JPG Gallery writes and failure cleanup, layered PSD, experimental Vector SVG, cubic node selection/handles/insertion, stabilization persistence, independent eraser presets, live photo transforms, canceled gestures, tooltips, color updates and portrait/landscape layout.

Independent resvg and psd-tools checks assess exported SVG at 1× and 2×, PSD layer metadata/pixels and merged previews. These checks do not establish every external editor's editing behavior.

The supplied `artifacts/editor-tablet.png` is preserved for the README. Other test images in `artifacts/` are desktop framework renders, not device screenshots.

## Hardware boundary

Physical Metal checks assess CPU/GPU brush and smudge agreement, compositing, transforms, history and recovery from intentional device loss. Some checks were run in earlier iterations; the release update reruns the brush/eraser/smudge comparison. Android Vulkan execution, sustained latency, thermals, pressure calibration, palm rejection, hover, stylus shortcuts and frame pacing require physical-device testing. Photoshop desktop interoperability has not been tested directly.

The renderer remains hybrid: GPU compute where available, CPU fallback, CPU bitmap readback/presentation and CPU ColorDrop/adjustments/export. See [GPU.md](GPU.md), [PERFORMANCE.md](PERFORMANCE.md) and [feature status](PARITY.md).

## Build outputs

The APK includes ARM64 and x86-64 libraries, thirteen JNI entry points and 16 KB-aligned ELF load segments. `artifacts/apk-verification.json` records APK size, hash and packaged native metadata. The preview uses the existing signing certificate so it can update an installed preview without clearing artwork.

GitHub Actions runs on pushes. Check its live status separately; local checks are not a claim that a remote workflow has passed.

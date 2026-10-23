# Verification — 24 October 2026

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

# Preview verification — 22 October 2026

Version 0.1.5, package `art.inkstone.preview`.

- Rust engine: 26 tests passed, zero failures.
- Android integration: 16 Robolectric tests passed against SDK 35 with native graphics and the real host Rust JNI library.
- Tested document round trips, native library calls, finger input and cancellation, saved gallery thumbnails, tablet and phone panels, invalid document rejection, and displayed color values.
- `artifacts/editor-tablet.png` is the user-supplied device screenshot used in the README. Other Android View renders in `artifacts/` are desktop framework renders. The tablet rendering test writes `editor-tablet-framework.png` to preserve the supplied screenshot.
- Android lint: zero errors, 52 warnings, mainly draw allocations, fixed layout direction, and platform metadata. These remain follow-up work.
- Debug APK assembled and APK v2 signature verified. ARM64 and x86-64 libraries include all eleven JNI entry points and 16 KB-aligned ELF load segments.
- APK size: 2,876,561 bytes. SHA-256: `4dcf72d177e99ae657a290d65c326b207dda418f506eff017987a2805bc533c6`.

## Screenshot-based interface revision

The six supplied references guided the light anchored New Canvas popup, full Custom Canvas editor, rounded brush category cards, cached frosted panel backgrounds, faint workspace grid, inline layer blend controls, left-swipe actions, and grouped Preferences. Numeric canvas input converts mm/cm/inches through DPI; DPI and layer locks persist in editable documents. Cursor style/opacity and gesture switches persist in preferences. Layer lock blocks drawing and destructive edits.

The integration suite exercises custom dimensions and saved presets, inline blend changes, swipe Lock/Unlock/Duplicate/Delete, cursor and gesture preferences, panel transitions, input cancellation, and uninterrupted long-stroke saving. Actual Android View renders were inspected and text clipping, hidden brush previews, and swipe action bleed-through were corrected. These renders are in `artifacts/`; they are not physical-device screenshots.

Frosted backgrounds use one downsampled, blurred canvas snapshot when opening a panel, avoiding a full-canvas blur on each stroke. Project Canvas, iCloud, CMYK profiles, and time-lapse remain unavailable. Exact Procreate geometry, original animation timing, and Xiaomi frame pacing remain unverified.

All rectangular app surfaces now share the Airbrushing tile radius of 12 dp through `StudioShape.CORNER_DP`, including brush selection, slider tracks, panels, layer rows/actions, settings groups, gallery cards, and canvas controls. Small slider thumbs naturally cap their curve at half their height.

## Device-feedback regression checks

Fourteen integration tests pass, including persistent panel identity across brush categories, shape recognition for line/circle/ellipse/square, hold-to-shape with one-step Undo, pressure/position-lock preference persistence, and Custom Canvas resizing across portrait and landscape. New renders include `classic-square-palette.png`, `quick-shape-circle.png`, `canvas-lock-settings.png`, and `custom-canvas-wide-again.png`.

Swipe actions now use View clip bounds updated on every animation frame instead of parent drawChild clipping, which could remain cached on Android hardware. The change is locally verified, but device confirmation is still required. Press bounce and content transitions respect system-disabled animations. Classic color selection and palette swatches intentionally retain square corners.

## ColorDrop, airbrush, and smudge checks — 0.1.5

ColorDrop uses a live, reversible native fill preview. Tests cover closed outlines, threshold growth and shrinkage from the original pixels, cancellation, a single Undo step, hidden RGB on transparent pixels, locked layers, and preserving committed content when saving during preview. The Android input test drags from the actual color control, previews, adjusts, commits, repeats the operation, cancels, and undoes the result.

Airbrush tests check smooth radial coverage and distinct medium/medium-hard profiles. Smudge tests verify pickup does not paint, colors are transported independently across the tip, the active color is ignored, and Undo restores original pixels. `brush-engine-before-smudge.png` and `brush-engine-after-smudge.png` show output from the real Rust engine rather than library thumbnail approximations. `colordrop-threshold.png` shows the live threshold control. All were inspected locally.

The library now has 46 procedural presets. ColorDrop uses coalesced preview requests on the drawing worker; each threshold preview still scans the affected region on the CPU. The fill reveal uses a temporary, at-most-2048-pixel backdrop and a 420 ms radial clip, honoring disabled system animations. Large-canvas latency, stylus drag coordinates, fill animation smoothness, and subjective brush quality still require Xiaomi Pad 7 testing.

## Boundaries

The user tested 0.1.1 on Xiaomi Pad 7 and reported interrupted strokes and severe lag. Version 0.1.2 fixes the identified save/render bottlenecks and awaits a repeat hardware test. See PERFORMANCE.md. Installation, pen pressure calibration, palm rejection, hover, side buttons, sustained latency, device rotation and large-document memory behavior still need hardware checks. The engine currently rasterizes and composites on the CPU. This preview is not complete Procreate parity; see PARITY.md.

Source is published at https://github.com/bramastahartono20/inkstone. GitHub Actions runs on pushes; consult the Actions page for current remote verification. The test counts above describe the verified local build.

## Local test environment

This build reused a local JDK 21 and Android SDK 36, with Rust installed in an ignored local toolchain directory. Robolectric's instrumented SDK was linked under a path without spaces to work around its native runtime resource loader treating escaped spaces literally. Optional `INKSTONE_TEST_SDK_DIR` and `INKSTONE_TEST_RUNTIME` allow predownloaded original Maven artifacts; normal CI resolves them from Maven. No machine-specific toolchains or paths are required in the source archive.

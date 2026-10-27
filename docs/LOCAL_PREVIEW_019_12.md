# Inkstone 0.1.9-preview12 — Background and light interface fixes

Android 8.0+, package `art.inkstone.preview`, version code 30. Install over your existing preview to keep local artwork; export `.inkstone` backups before uninstalling or clearing app data.

## Background colour

Layers → Background colour opens the same editor-owned picker in Raster and Vector. It includes White, Warm paper, Black, Current colour, a transparency control, a color disc and hex entry. Presets apply immediately; disc/hex choices commit when Apply color is tapped. Brush ink remains unchanged.

Choosing a color makes the background visible again, including after transparency was enabled. Rapid preset choices retain the last selection. Raster background commands run in order and save after completion. Both document types retain Undo support. Vector now edits the background color rather than only toggling transparency.

## Light interface

Actions → Prefs → Light interface applies a shared palette to the toolbar and history controls, side rail, panels, settings, brush previews, multicolor controls, sliders, gallery, Atlas workspace and custom canvas creation. Vector inherits the saved theme. Orientation changes retain the theme; selected controls keep readable contrast. Artwork pixels and actual color swatches are kept separate from interface theming.

## Validation boundaries

Framework/JNI regression tests cover hidden backgrounds, rapid color choices, custom hex, unchanged brush ink, background Undo, Vector editing, theme changes, portrait history, gallery/canvas setup and preserved black artwork swatches. Framework screenshots were inspected. Native engine binaries are unchanged from preview11. Android device stylus behavior, animation pacing, Vulkan performance and OS-specific UI rendering still require device testing.

Final test and packaging results are recorded in `artifacts/preview12-full-test-summary.json`, `artifacts/apk-verification.json` and [verification](VERIFICATION.md).

# Inkstone 0.1.9-preview11

Development preview for Android 8.0+, package `art.inkstone.preview`, version code 29. Install over an existing preview to retain artwork; export `.inkstone` backups before uninstalling or clearing app data.

## Multicolor panel

Open Paint Brush Library → + → Brush Studio → Multicolor settings.

- The dark panel belongs to the editor, with rounded cards, circular color swatches, horizontally scrolling stroke thumbnails, a live color-cycle preview and expandable cycle controls. It replaces the platform dialog and dropdowns.
- Choose Solid, Spectrum or Custom. Tap a swatch for the inline color disc/hex editor. Apply edits, move colors left/right or remove them; keep at least two. Add up to twelve colors or copy a saved palette.
- Thirteen tips: Round, Pencil, Airbrush, Dry ink, Wash, Flat ribbon, Bristle, Marker, Chalk, Pastel, Medium airbrush, Firm airbrush and Spray. The selected tip stays visible when opening the panel.
- Expand Adjust color cycle for Along stroke, Across width or Bristle strands. Drag the custom logarithmic slider, or type a length and tap Set px (20–20,000 canvas pixels).
- Controls save immediately. Cancel inside the color editor discards that un-applied color edit; closing the panel retains applied settings. Use Brush Studio → Save as new brush / Save brush changes for a reusable personal preset.

## Canvas Atlas

Pinch out to reveal nearby artwork without leaving the editor workspace. Pan, zoom and rotate with the same camera; tap an artwork to enable editing at its existing position. A gray frame identifies the active canvas. Tap another artwork to save the current file and activate the selected file in place. A compact loading indicator appears during file switches. Hold and drag overview artworks to arrange them.

Overview blocks painting, ColorDrop and object editing until an artwork is selected. Inactive artwork uses viewport-culled previews with a maximum side of 256 pixels and a cache of at most 48 images. Only the active document has a live engine; outgoing and incoming files can coexist briefly during a switch. Placement is device-local, separate from project exports. There are no board-specific collections, shared boards or simultaneous multi-document editing.

## Groups and personal brushes

Groups in Raster and Vector support Pass Through, Normal, Multiply, Screen, Overlay, Darken, Lighten and Add, plus opacity applied once to combined members. Native save/Undo preserves appearance; SVG retains Vector folder appearance. PSD bakes groups with their own opacity or blend mode into one raster layer; keep `.inkstone` for editable members. Raster isolated-group compositing uses CPU patches. Groups are one level.

Library + supports Brush Studio, New brush, Duplicate current brush and New category. Hold a personal brush or category to manage it. Personal presets store the full profile; categories and recent choices persist. There are 57 built-in procedural presets. Brush libraries remain local; third-party brush-file import and external library export are not included.

Panel widths are set independently, so opening Layers does not resize the next Brush Library.

## Rendering and validation

Multicolor supports Along, Across and Bristle layouts in CPU/GPU brush rendering. Wet mix is an experimental RYB approximation using CPU painting; physical pigment, impasto and wetness simulation are not included. Tip thumbnails approximate the actual renderer.

Local checks: 108 Android framework/JNI tests, 75 Rust tests and 12 physical desktop Metal GPU checks passed. Android lint/build passed with zero errors and 113 warnings. Tests cover applied color/tip/layout/cycle persistence, editor-owned controls, group appearance/export, Atlas input gating and camera continuity. The panel screenshot uses synthetic test artwork.

See `artifacts/preview11-full-test-summary.json` and `artifacts/apk-verification.json` for package evidence. These checks do not establish Android hardware latency, stylus behavior, animation pacing, memory use or Vulkan driver performance. Device testing is still needed.

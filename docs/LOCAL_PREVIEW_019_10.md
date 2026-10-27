# Inkstone 0.1.9-preview10 — local preview

Unpublished local build. Includes preview8 panel sizing and preview9 group appearance / personal brush library changes. Physical Android device testing remains pending.

## Canvas Atlas: one spatial workspace

Atlas now shares the drawing editor's camera and toolbar. There is no separate overview page or Resume header.

- Pinch out to reveal nearby artwork; pan and rotate with the same workspace. Actions → Canvas Atlas animates out into the workspace.
- The overview blocks drawing, object edits and ColorDrop until an artwork is selected. Tap the current artwork to enable editing at its existing position and zoom. Its gray frame identifies the active canvas.
- Tap another artwork to save the current document and activate the chosen file in place. The camera and artwork's screen position are preserved, including switches between Raster and Vector. A compact loading indicator can appear while saving/loading a document; large files are not instant.
- Navigation and drawing are separated: a selection tap does not create a brush dot. Two-finger navigation does not paint. Hold an artwork in the overview to arrange it; positions persist.
- Background canvases are viewport-culled previews, not additional live engines. The cache holds at most 48 previews with a maximum side of 256 pixels. The current document stays loaded. The outgoing and incoming documents may coexist briefly during a file switch.
- Arrangement metadata stays in local library preferences. Board-specific collections, shared boards and simultaneous editing of several documents are not included.

## Multicolor brush settings

Paint Brush Studio → Multicolor settings now includes:

- **Color source:** Solid, Full spectrum, or My combination.
- **Combination editor:** add up to 12 colors, edit with a disc/hex code, move colors left/right, remove colors while keeping at least two, or copy a saved palette.
- **Arrangement:** Along stroke, Across brush width, or Bristle strands.
- **Tip:** Round, Pencil, Airbrush, Dry ink, Wash, Flat ribbon, Bristle, Marker, Chalk, Pastel, Medium airbrush, Firm airbrush or Spray.
- **Color cycle length:** logarithmic slider and exact numeric input, 20–20,000 canvas pixels per complete cycle. Shorter cycles change the colors more frequently along the stroke.

Current tip, colors, arrangement and cycle length persist across sessions. Save as new brush / Save brush changes stores them in a reusable personal preset.

New presets include Rainbow Pencil, Rainbow Bristle, Rainbow Wash, Rainbow Chalk, Rainbow Marker, Rainbow Ribbon and Pastel Threads. There are 57 built-in procedural presets in total.

## Rendering

Multicolor can now carry several colors side by side across the tip, aligned to the direction of drawing, instead of assigning one flat color to every dab. Bristle mode adds strand coverage. Direction is retained for fade replay and CPU fallback. Both CPU and GPU support the new color layouts. Erase and Smudge retain their original color behavior.

These are procedural color/texture brushes, not a physical simulation of thick paint, spectral pigments, wetness or individually loaded bristles. The earlier experimental RYB Wet mix remains an approximation and uses CPU painting. Preview visuals approximate the brush; the exported artwork uses the actual engine output.

## Validation

Results are recorded in `artifacts/preview10-full-test-summary.json` and `artifacts/apk-verification.json`. Tests exercise custom combination/tip persistence, colors across stroke width, CPU/GPU comparisons, fade replay, overview input gating and camera continuity across document types. Screenshots in `artifacts/atlas-*.png` use synthetic test artwork. Desktop tests do not establish Android stylus latency or visual equivalence to another application.

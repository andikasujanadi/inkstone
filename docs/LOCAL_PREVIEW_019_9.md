# Inkstone 0.1.9-preview9 — local preview

This APK is local and unpublished. It includes preview8's independent panel-width fix. Physical Android device testing is pending.

## Canvas Atlas

Pinch out below approximately half the fitted canvas size, then release to enter the board from Raster or Vector. It is also available under Actions → Canvas → Canvas Atlas in Raster, or Actions → Canvas Atlas in Vector.

- Pan with one finger or zoom/pan with two fingers. The overview cannot paint, erase, fill or edit document objects.
- Tap an artwork to activate its editor. A subtle gray frame identifies the current artwork; Resume returns to it. The selected editor is fitted on return.
- Hold and drag artwork to arrange it. Positions persist, including the positions of artworks that were not moved, so deleting or adding files does not rearrange the rest.
- The board shows all local gallery artwork. It uses viewport culling and a cache of at most 48 previews, each at most 192 pixels along its longest side. It does not open a live drawing engine for each preview. Very large overviews can show placeholders for uncached cards.
- One current document remains loaded behind the board. Switching documents saves the current artwork first and uses the existing guarded loading flow. The outgoing and incoming document can coexist briefly during the load transition.
- Atlas layout is local library metadata, not part of an individual `.inkstone` export. This is a first spatial workspace, without board-specific collections, per-board collaboration or simultaneous editing of multiple documents.

## Group blend and opacity

Open a folder's blend/options button in Layers → Blend & opacity. Raster and Vector support Pass Through, Normal, Multiply, Screen, Overlay, Darken, Lighten and Add.

Opacity applies once to the combined folder contents. Pass Through at full opacity retains the existing backdrop interaction; explicit modes and reduced group opacity isolate the folder contents. Apply commits one undo operation; native save/load preserves the settings. Vector SVG includes folder wrappers and appearance settings. Android 10+ uses the modern Vector blend compositor; older Android versions use legacy blend fallbacks.

PSD preserves the appearance of groups with their own opacity or blend mode by rasterizing each such group to one layer. The `.inkstone` file retains its editable members. Existing default pass-through folders continue to export their separate member layers.

Raster painting retains GPU brush support where eligible. Isolated folder compositing currently uses CPU patches. A GPU group compositor remains future work.

## Personal brush library

- Library `+` opens Brush Studio, New brush, Duplicate current brush or New category.
- Hold a brush for Edit, Duplicate, and (for personal brushes) Rename, Move to category and Delete.
- Hold a personal category to rename it or delete it while moving its brushes to My Brushes.
- Studio → Save as new brush / Save brush changes stores size, opacity, flow, stabilization, pressure settings, fade, eraser hardness and color-variation settings.
- Custom brushes, categories and per-tool recent choices persist. Built-in brushes remain available. This does not add third-party brush-file import, grain/shape editing, or external brush-library backup/export.

## Multicolor and experimental wet mixing

Artistic includes Rainbow Ink and Palette Ribbon. Painting includes Wet Paint. There are 50 procedural built-in presets in total.

In Paint Brush Studio, Color variation selects Solid, Rainbow or Palette. A palette needs at least two colors. Color cycle controls the distance of a complete color cycle. Wet mix is an experimental RYB-based approximation that samples the current layer under the brush. It does not physically simulate pigment spectra, wetness, thick paint or separately colored bristles. Wet mixing uses CPU painting; multicolor without wet mix supports the existing GPU path. Erase and Smudge retain their normal color behavior.

## Validation

See `artifacts/preview9-full-test-summary.json` and `artifacts/apk-verification.json` for final results. Desktop GPU checks do not establish Xiaomi or Samsung runtime performance. Android hardware testing is still required for stylus behavior, memory, animation timing and navigation feel.

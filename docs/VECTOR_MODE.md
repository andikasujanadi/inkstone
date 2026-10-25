# Experimental Vector mode — 0.1.8

The preview includes a separate Vector document mode. Choose **Gallery → + → Vector · SVG → a preset**, or **Custom Canvas → Document type → Vector**. Existing Raster documents remain Raster.

**Known issue in 0.1.8:** color codes may update without changing the effective Vector color, and drag-to-fill may fail on a device. Recorded at the user’s request for a later fix; see [KNOWN_ISSUES.md](KNOWN_ISSUES.md).

## Try it

1. Draw with Pen, Line, Rectangle or Ellipse. Enable Fill before drawing a filled shape; Colors changes stroke/fill and accepts hex values.
2. Choose Select and tap a shape or stroke. Drag the object to move it, a corner to resize it, or the handle above it to rotate. For an unfilled shape, tap its outline.
3. Choose Nodes after selecting an object to move its stored points. Freehand paths have selectable cubic anchors and direction handles. Only the active anchor shows its handles. Use Previous/Next and Point coordinates for crowded nodes; coordinates are local to the object, before its transform. Use **+ Anchor** and tap a selected line/path to split it without changing its shape, then drag the new anchor or handles to bend it. Rectangle/ellipse nodes remain their defining endpoints.
4. Object duplicates, deletes and changes stacking order. Layers adds, selects, renames, hides, locks, changes opacity and reorders layers. The background can be white or transparent.
5. Save project to Inkstone, return to Gallery and reopen it. Save → Export SVG writes actual paths/shapes, fill/stroke colors, affine transforms and named layer groups, without embedding a raster image.
6. Save PNG/JPG to Pictures/Inkstone or a selected folder. PSD exports separate **rasterized** vector layers, not editable PSD vector shapes. Keep `.inkstone` or SVG as the vector master.

Undo/Redo covers geometry, transforms, nodes, styles and layers. A canceled gesture restores the previous state. Autosave and explicit snapshots preserve the last committed state while a gesture is in progress. Two fingers pan/zoom/rotate; Actions → Fit resets the view. Pen-only input blocks finger drawing while keeping touch navigation, object/node editing and controls available. Actions → Prefs shares the input/palm settings with Raster. The stylus eraser end does not paint; delete vector objects through Select → Object → Delete.

## Architecture and limits

Geometry is stored in a gzip-wrapped version-2 `.inkstone` document with `mode: vector`. Raster's native format is unchanged. Vector uses Android Canvas paths, not the Rust pixel brush engine. Each freehand path is a smoothed monoline stroke; textured brushes, airbrush, smudge, ColorDrop, image insertion, raster selections, raster QuickShape and raster pressure dynamics belong to Raster mode.

This is a first Vector implementation. There is no SVG import, automatic tracing, conversion of existing Raster documents, text, gradients, boolean operations, multi-object selection, snapping, pressure-varying outlines or full path topology editing. SVG layers export as standard named groups; other editors may present those groups differently.

Limits: 16–8192 px per side, at most 16,777,216 canvas pixels; no fixed layer-count cap, 5,000 objects, 250,000 total points, 4,096 samples per legacy Pen path or 12,289 coordinate entries per cubic path and a 16 MiB serialized project. Freehand input is downsampled above 2,048 samples. Undo/Redo retains at most 64 actions, with a 32 MiB target that preserves at least 30 steps. Rasterized PSD additionally uses the native 67,108,864 total layer-pixel budget. Large documents can still require substantial memory and may export slowly. This is not a tiled professional vector engine.

## Verification

- Nine Vector Robolectric/native-graphics tests cover versioned round trips, Undo/Redo, malformed input, rollback, shape creation, selection, move/scale/rotate handles, point editing, canceled gestures, pen-only drawing, gallery reopen, SVG picker MIME and phone/tablet layouts. Existing Raster Android tests remain included.
- `scripts/verify-vector.py` reads generated fixtures with independent `resvg-py` and `psd-tools`. SVG is rendered at 1× and 2× and compared with Android's native rendering, including transparency, group opacity and transforms. Rasterized PSD layer count, names, opacity and visibility are checked separately.
- Test renders are framework artifacts, not physical-device screenshots. This version has not yet been tested on Xiaomi Pad 7, in Photoshop desktop or against an external SVG editor's editing workflow.

Run Android tests first to produce `artifacts/vector-fixture.*`, then run `python scripts/verify-vector.py` in an environment with Pillow, resvg-py and psd-tools.

The current toolbar, wheel/layer panels, responsive layout and slider preview are documented in [LOCAL_PREVIEW_2.md](LOCAL_PREVIEW_2.md). Shape tools may be grouped under a Shapes icon in narrow windows.

The current preview also includes [node editing, stabilization and ColorDrop corrections](LOCAL_PREVIEW_3.md). New freehand strokes are simplified at about 1.2 screen pixels and stored as cubic curves. Existing quadratic Pen paths are converted exactly when a node edit is committed. Tap-only selection or a canceled gesture leaves the document unchanged. Older previews may reject cubic Vector objects; keep the current APK and editable backups together.

## Local 0.1.8-preview3

ColorDrop now fills closed Vector paths. In Nodes, Close path connects the path back to its start; Join ends joins another open path in the same layer while preserving transforms. The closed flag survives native saves and SVG export. Two-/three-finger taps provide Undo/Redo, configurable in Vector Prefs. History retains up to 64 compressed snapshots, with a 30-step minimum under its memory budget. See [LOCAL_WORKFLOWS.md](LOCAL_WORKFLOWS.md) for exact usage and limits.

## Unpublished local color fix — 0.1.9-preview1

Wheel and valid hex edits update the active drawing/ColorDrop ink immediately. Stroke/Fill apply the chosen color to a selected object explicitly. Toolbar drag-fill now uses the current active color after either kind of edit. Framework event tests passed; retest on the device that reported the original issue. See [LOCAL_PREVIEW_019.md](LOCAL_PREVIEW_019.md).

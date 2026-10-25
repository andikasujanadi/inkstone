# Local 0.1.9-preview2

Unpublished device-test APK. No Git commit, push, tag, release, or replacement of public release assets has been made for this work.

## Compact bottom tools

Selection displays six main groups: **Shape, Combine, Clipboard, Refine, Saved, Actions**. Tap a group to expand its choices in a second row; tap it again to collapse. Opening another group replaces the expanded row. The floating panel is capped at 536 dp instead of stretching across the canvas. Both rows scroll horizontally when needed; portrait shows about five main entries before scrolling. Expanded state survives ordinary artwork updates without replaying the opening animation.

Transform displays **Mode, Adjust, Options, Reset, Cancel, Done**. Mode expands Freeform/Uniform/Distort/Warp; Adjust expands size, flips, Rotate 45° and Fit to Canvas; Options contains Snapping and interpolation. Reset/Cancel/Done remain directly accessible. The existing Vector node strip also has the same width cap and horizontal scrolling.

## Fade Brush: long strokes, constant width

Choose **Inking → Fade Brush**. The brush stays at its configured width and can draw a line of any supported length. When the pen lifts, opacity fades over the last 150 document pixels by default. Brush Studio → **Fade tail length** changes that length or turns it off. A shorter stroke scales the fade to its own length; a tap remains a visible dot. Fade does not change brush diameter. Pressure → Size remains an independent control.

The tail is applied after release, using the recorded dabs and their individual tool profiles. Live input remains visible immediately. Finalization replays the stroke from its original pixels and refreshes the full original damage region to avoid leftover opaque tail pixels. Very long, large or textured strokes can take additional work at release; physical-device latency remains a test item.

This replaces preview1's fixed-distance fade-and-taper implementation, which shrank and exhausted long strokes. It is an opacity-tail effect rather than a forced size taper. References used to distinguish these behaviors: [Photoshop brush dynamics](https://helpx.adobe.com/photoshop/desktop/apply-painting-techniques/fill-objects-selections-layers/brush-dynamics-settings.html), [ibisPaint Force Fade](https://ibispaint.com/lecture/index.jsp?no=50).

## Selection

- **Shape:** Automatic, Freehand, Rectangle, Ellipse. Automatic samples the visible composite and selects a connected region; set its tolerance through Refine → Threshold, then tap the region.
- **Combine:** Replace, Add, Remove, Invert.
- **Clipboard:** Copy & Paste, Copy, Cut, Paste. Paste creates a new layer and enters Transform. The internal clipboard is available across Raster canvases during the app session. If pasted content cannot fit the target canvas at its source position, it is fitted and centered. It is not an OS-wide or persistent clipboard.
- **Refine:** Threshold and Feather. Feather uses a soft grayscale selection mask, not just a visual blur; brush/eraser CPU and GPU rendering respect its coverage. Feather is applied from the current selection's original mask, avoiding cumulative blur while adjusting its radius.
- **Saved:** Save selection, Load selection, Delete saved. Named masks persist inside editable Raster `.inkstone` documents, including feathered masks. Older builds may ignore this additional metadata. Saved masks are capped at 32 MiB of run data per project; this does not add PSD selection channels.
- **Actions:** Color Fill, Clear, Deselect, Transform. Clear removes selected pixels; Deselect removes the selection restriction. Copy/cut/paste, fills and pixel deletion preserve artwork Undo/Redo. Pure selection edits do not add artwork-history steps.

The canvas shows a soft selection overlay and dashed boundaries, including combined, inverted and restored masks. The preview is bounded to 512 pixels on its longest side; editing uses the full-resolution native mask. Empty selections prevent painting until replaced or deselected. Locked layers reject pixel-changing selection actions.

## Transform

- **Freeform:** resize width and height independently with the corners and edges.
- **Uniform:** keep proportions while resizing.
- **Distort:** independently move corners or edges of a projective quadrilateral. Crossing/singular configurations are rejected.
- **Warp:** edit a 4 × 4 control-point grid (3 × 3 cells). Individual points deform the content; folded cells are rejected. The preview is tessellated within each cell to track the native bilinear warp geometry rather than jumping on commit. This is a mesh warp, without Bezier tangent controls or liquify.
- **Snapping:** move edges/centers onto canvas and other layer bounds, with guides; rotation snaps near multiples of 15°.
- **Adjust:** Smaller/Larger, Flip H/V, Rotate 45°, Fit to Canvas.
- **Options:** Nearest or Bilinear interpolation. Nearest suits pixel work; Bilinear filters alpha-aware pixel samples.
- **Reset:** restore the original pending geometry. **Cancel:** discard the pending transform. **Done:** resample once at full resolution and commit one artwork Undo action.

Selection masks travel as coverage during resampling. A layer mask must be applied before transforming a selected portion of that layer; whole-layer masks are supported. Transform is rasterized and canvas-clipped on commit, so repeated committed transformations can lose detail. Large CPU resampling operations and device performance still need testing.

## Previous local fixes retained

The configurable Raster stylus button, independent held-eraser profile, direct Colors → Eyedropper, single Pen-only control, and live Vector wheel/hex/drag-fill fixes from [preview1](LOCAL_PREVIEW_019.md) remain included.

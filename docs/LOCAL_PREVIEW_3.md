# Local preview 3 — 0.1.7-preview3

> Historical implementation notes from the local previews. These changes are included in the 0.1.7 early-development release. Publication statements below describe the earlier preview stage.

Unpublished. No commit, push, tag or GitHub release was made.

## Changes

- ToolIcon now supplies Android hover/long-press tooltips throughout Raster and Vector. Vector labels distinguish Pen, Nodes and object selection. Android controls the hover delay; the setting also works with a mouse. Existing accessible labels remain available.
- Raster Colors' header swatch follows every wheel, palette, RGB or hex update without reopening the panel.
- ColorDrop repairs a one-pixel antialias fringe using the original pixel buffer, without propagating through the outline. For transparent target regions it places the fill behind the partial-coverage stroke; for opaque target regions it estimates the old background contribution using a nearby stronger stroke color and replaces that contribution when the color model fits. Opaque cores stay unchanged. Selection restrictions and alpha-lock alpha remain respected. Fill-preview cancellation and one-step Undo retain original pixels. Wide soft edges, photographic textures and reference-layer fills are not fully solved.
- New-canvas choices say **Vector · Experimental**, including the custom-canvas mode. The accessibility label stays “Vector document”.
- Vector → Actions → Stroke settings adds a saved 0–100% stabilizer. It uses time-based input filtering before path creation; higher values reduce jitter and introduce lag. It affects new Pen strokes and does not smooth node movements. Lifting the pen preserves the final input endpoint.
- New Pen strokes simplify input samples at roughly 1.2 screen pixels, then store true cubic geometry. Nodes selects anchors on the rendered path, rather than the old quadratic control samples. Legacy Pen curves convert exactly when edited, retaining appearance; a tap without dragging or a canceled edit rolls the conversion back.
- Node selection gives the currently selected object priority, then chooses its nearest anchor in screen space. Touch has a 22 dp radius, stylus 12 dp. Dragging uses the initial grab offset, so an off-center touch does not snap the point under the finger. The active anchor is highlighted; only its two adjacent curve handles appear, with contrasting outlines. Moving an anchor carries its adjacent handles; dragging a handle changes that handle alone.
- The bottom node controls cycle Previous/Next anchors, show Point n/N, and open exact Local X/Y coordinate editing. These positions are before the object's affine transform. Shapes still edit their two defining endpoints; this is not a full vector topology editor, SVG importer or tracing system.

## VectorCraft attribution

Nearest-anchor and selected-object-first picking were adapted to Kotlin from [VectorCraft direct.rs](https://github.com/storytold/vectorcraft/blob/125df6d8ecfae01d01ce369ac02e4aa03e0deed5/crates/tools/src/direct.rs), `nearest_anchor` and `hit_anchor`. Android touch targets, gestures, rendering and document integration use Inkstone's own implementation. Attribution and the MIT license are in `third_party/vectorcraft/`; the license also ships in the APK assets. No VectorCraft branding, assets or full application engine was included.

## Validation

Rust tests cover transparent/opaque anti-aliased boundaries, unchanged opaque outline/outside pixels, preview cancellation, Undo/Redo, selection and alpha-lock. Android tests cover the native fill result, tooltips, live header swatches, nearest/selected-object priority, off-center node grabs, handle movement, cancellation, Undo/Redo, geometry-preserving legacy conversion, SVG serialization, precise coordinates, jitter reduction and stabilizer persistence, in addition to the existing regression suite.

The Vector fixture contains cubic SVG geometry and is independently rendered with resvg at 1× and 2×. PSD is checked using psd-tools. Framework renders are not screenshots from a physical tablet. Mouse tooltip behavior, Xiaomi pen latency/palm rejection and actual device export destinations still require device testing.

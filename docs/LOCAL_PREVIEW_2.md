# Local preview 2 — 0.1.7-preview2

> Historical implementation notes from the local previews. These changes are included in the 0.1.7 early-development release. Publication statements below describe the earlier preview stage.

Unpublished APK; no GitHub commit, push, tag or release was made.

- Vector uses the same dark toolbar/rail arrangement as Raster. Gallery is at the upper left, vector icon tools follow it, and Layers plus active color are at the upper right. The color panel has the same hue wheel component as Raster. Vector Layers opens a rounded panel with thumbnails, layer selection, visibility and options. Narrow windows group Line/Rectangle/Ellipse under Shapes.
- Undo/Redo's pair is anchored to the exact horizontal midpoint, independently of the widths of the left/right tools; Save sits beside it. In portrait/compact windows history is at the bottom, and the top has two 44 dp rows. Landscape has one 44 dp row. Both editors remeasure their chrome when window size changes, without reopening the document. Gallery/Save labels are retained across chrome rebuilds.
- Vector width/opacity sliders show the shared BrushHud preview. Nodes chooses the nearest point, has larger visible targets and can start editing a point immediately when tapping an object's node.
- Tap Erase twice to open Hardness, including Soft/Medium/Hard shortcuts. The preference persists; eraser-button input uses it too. CPU and GPU use the same quantized feathered masks. Size/opacity remain on the dock.
- Raster deposition uses carried distance between dabs across input batches. Duplicate stationary samples add no extra ink; high-frequency events no longer increase coverage simply because more input events arrive. Soft-brush spacing is smaller, and Water Wash falls off smoothly to zero at its edge. Overlapping strokes can still build pigment; this is not a physical watercolor simulation or a timed stationary airbrush.
- Inserting a photo activates Transform on the new layer. Layers → tap active layer → Move / resize contents also starts Transform and clears any old selection. Drag to translate, use Smaller/Larger for 10% resizing, and Done to finish. Raster does not yet have live content previews or freeform scaling handles during the drag; the bitmap updates after release.
- Gallery/document opening fades/scales in, and closing a saved document animates back to Gallery. System-disabled animations are respected.

Validation: 32 Rust tests, shader validation and a separate physical Metal GPU brush/smudge/history comparison, now including soft/medium/hard erasers. Android regression coverage includes portrait-start to landscape configuration changes, centered/visible Vector chrome, wheel/layer panels, slider preview, eraser preference and imported-layer transformation, alongside the previous drawing/export tests. These are local framework and Apple GPU checks; Xiaomi stylus feel, sustained performance and physical rotation remain to be tried on the device.

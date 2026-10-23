# Local preview 4 — live Raster transforms

> Historical implementation notes from the local previews. These changes are included in the 0.1.7 early-development release. Publication statements below describe the earlier preview stage.

Version: `0.1.7-preview4`. Unpublished; no commit, push, tag or GitHub release.

## Try it

Insert a photo, or choose an existing layer and start Transform. The blue box follows the selected painted pixels on the active layer, rather than the page.

- Drag a corner to resize while preserving the current proportions.
- Drag an edge midpoint to change that dimension independently.
- Drag the photo/body to move it.
- Drag the handle above the box to rotate freely.
- Two fingers on the drawing area translate, pinch and rotate the selected content. The canvas viewport stays still during Transform. Two-finger tap Undo is reserved for ordinary drawing mode.
- Smaller, Larger, Flip and Rotate 45° update the same live preview immediately.
- Done applies the complete transform once, as one Undo action. Cancel restores the state before entering the transform session. Canceling an interrupted touch gesture restores that gesture's initial matrix.

Strict Pen-only input still rejects finger input, including two-finger transforms. Turn it off to use touch gestures. The canvas position lock does not prevent object transformation.

## Rendering and lifecycle

A single native snapshot supplies cropped layers plus the moving pixels, respecting the active selection. It is capped at six million preview pixels and a 1536 px long side. Android Canvas updates the preview using an affine matrix; no full-resolution pixel warp runs on pointer movement or each size-button press. The box uses the same matrix as the image. Preview gestures can move content out of view and back before applying.

Done resamples the source once at document resolution using premultiplied-alpha bilinear interpolation, then records one Undo change. The saved result is rasterized and clipped to the canvas. Applying, saving, leaving Transform, changing layers, or starting another document operation can commit the current preview. Repeated committed sessions can still accumulate raster resampling loss; this does not add persistent image objects or smart objects.

Automatic saving is deferred while a transform is being edited so idle time does not unexpectedly defeat Cancel. Explicit Save/export and Activity pause commit first, then capture the updated document. Leaving the tool commits; Cancel discards. Drawing worker ordering keeps a layer change or file capture behind the pending affine operation. The preview remains visible until the native frame arrives, and its bitmaps are then recycled.

Layer order, opacity, visibility and clipping are included in the preview. Normal and supported Android blend modes are represented directly. Linear Burn, Darker Colour and Subtract preview as Normal; Android 8/9 also approximate modes unavailable through PorterDuff. The full-resolution native commit/export retains the actual blend behavior. A downsampled preview may lose fine detail when zoomed far in.

## Verification

- Rust tests verify content/selection bounds, cropped snapshot planes, the preview pixel budget, unchanged source/history during capture, exact integer translation, free affine rotation, premultiplied edge colors, unchanged unselected pixels, invalid-matrix rejection, layer locks and Undo/Redo.
- Android tests exercise corner dragging, live box changes, repeated Smaller/Larger without native history changes, Cancel after idle time, two-finger angle/scale/translation with a fixed viewport, full-resolution application, save/layer-change ordering, and live layer opacity/Multiply/clipping against native output.
- A physical Metal GPU test verifies compositor cache updates after the affine commit and after Undo/Redo. This is a host GPU check, not a Xiaomi Vulkan or stylus benchmark.
- Framework renders include `preview4-transform-before.png`, `preview4-corner-resize-live.png`, `preview4-two-finger-rotation.png` and `preview4-transform-applied.png` under artifacts.

The native bridge now has 13 JNI methods per ABI. Existing drawing, Vector, export, ColorDrop and UI regression tests remain included. Physical Pad 7 gesture feel, large-document memory/performance and file destinations remain to be checked on the device.

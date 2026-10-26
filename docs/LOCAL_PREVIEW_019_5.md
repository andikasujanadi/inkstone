# Local 0.1.9-preview5

Unpublished build for device testing, 26 October 2026. Install `releases/inkstone-0.1.9-preview5-debug.apk` over the existing preview. The package and signing certificate are retained.

## Fixes

- **Fade start cap:** defer the first Fade dab until movement establishes velocity. The first moving sample initializes velocity without an artificial slow-start estimate. Stationary samples cannot stamp an opaque head. Short taps still deposit ink, including on the GPU. Constant width, speed-sensitive coverage, unrestricted length and fading tails remain.
- **Layer drag and drop:** hold a layer row, then drag up/down to reorder. Drop on a folder heading or member to join that group; dropping into a collapsed folder expands it. Drop on **Outside groups**, or use the left edge of a row, to move a layer back to the root. Targets highlight during dragging and the panel scrolls near its top/bottom edges. The operation is one Undo step. Raster and Vector both support this; the existing Layer group menu remains available.

## Undo and Redo feedback

Undo/Redo identify the action, such as **Undo: Create layer**, **Undo: Brush stroke**, **Undo: Shape**, **Undo: Move layer**, or **Redo: Liquify**. Toolbar, touch gestures, keyboard and stylus-button history actions share this feedback. Raster names survive in-session disk archiving of history; Vector snapshots carry their action names. As before, Undo history is not restored when reopening a saved project.

## Perspective and isometric guides

- Raster: **Actions → Canvas → Drawing guide settings**.
- Vector: **Actions → Drawing guides**.
- Choose 2D Grid, Isometric, or 1/2/3-point Perspective. Adjust spacing and opacity. Perspective spacing controls angular separation of rays; other modes use document pixels.
- **Edit vanishing points** shows draggable VP handles. The first two points share a horizon. Tap **Done** when finished.
- These are drawing overlays, not stroke snapping/assisted drawing. They are excluded from image/SVG/PSD exports. Guide display settings are stored on this device and shared across artworks; they are not embedded in `.inkstone` documents.

## Liquify

Raster: **Adjustments → Liquify**. Choose **Mode** for Push, Pinch, Expand, Twirl right/left, or Restore. The docked size and opacity sliders control Liquify size and strength while the tool is active; **Brush** also exposes these settings. Drag over the active layer to preview. Two-finger navigation remains available, including with pen-only input.

**Done** commits the entire tool session in one Undo step; **Cancel** restores the entry state; **Reset** restores it and starts a fresh session. **Restore** paints back the pixels present when this Liquify session began. Destination edits respect the current selection and alpha lock. Apply a layer mask before entering Liquify. Editing is bounded to the canvas.

This is a basic CPU raster deformation tool with bilinear resampling. It does not deform editable Vector geometry, and does not include Freeze, mesh saving, face detection or advanced reconstruction. Repeated resampling can soften edges. Android performance with large radii still needs device testing.

## Validation

- 88 Android framework/JNI tests passed. The final perspective-spacing change also passed a focused guide test, lint and rebuild.
- 65 standard Rust tests and ten physical Metal GPU tests passed. New checks cover the Fade head/tap, archived action names, layer membership/order and undo, Liquify modes/cancel/commit/selection/undo, real Android layer drops, draggable vanishing points, and Liquify interaction through the editor. Existing QuickShape testing now checks the Shape history name.
- Independent SVG, layered PSD and masked PSD fixture validation passed. Perspective/isometric guides and the live Liquify preview were visually inspected.
- Build and lint succeeded: zero errors, 96 warnings. V2 signing, existing certificate, ARM64/x86-64 native library equality, fourteen JNI entry points, 16 KB ELF alignment, license assets and package metadata verified.
- Package `art.inkstone.preview`, version code 23, version `0.1.9-preview5`.
- APK: 10,302,769 bytes. SHA-256: `0f0d195eccb35225e861d8c13af080a85eb048ad123805a9231e00fe862f7586`.

No push, tag or GitHub release was made. Physical Xiaomi/Samsung stylus and Android Vulkan testing remain pending. Earlier [preview4 features](LOCAL_PREVIEW_019_4.md) are retained.

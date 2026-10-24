# Workflows — 0.1.8 early-development preview

## Custom palettes in 0.1.8

In Raster Colors, drag the rectangular active-color preview at the upper right into the palette grid. The grid highlights while accepting a drop. The swatches remain square and touch without gaps. Dropping adds the color; it never flood-fills the canvas. Duplicate colors are not added twice.

Choose **Palette → + New palette**, or the pinned **Palettes** tab → **New palette**, enter a name, then drag or choose **Add current color**. Palette opens the saved-palette chooser. The Palettes tab also offers rename/delete; deletion asks for confirmation. Holding a swatch replaces it with the active color. Editing Studio first creates a private “My palette” copy. Palettes and the selected palette persist across artworks and app restarts, locally on the device.

The reported Vector color-wheel/effective-color and drag-fill bug is deferred, as requested. This release does not claim it is fixed; see [KNOWN_ISSUES.md](KNOWN_ISSUES.md).

## Drawing and layers in preview4

- Raster and Vector no longer impose fixed layer-count caps. New Raster layers share a transparent bitmap until painted; duplicate layers share their source bitmap until edited. Empty layers are excluded from compositing work while preserving clipping behavior.
- Native save/load preserves shared transparent and identical bitmap storage. Canvas setup now shows “Layers: Memory dependent”.
- Painted Raster data and masks still have a 256 MiB live-buffer budget, separate from undo, render buffers and GPU memory. When a shared layer cannot be expanded safely, drawing reports a memory message. This is not unlimited physical memory or a tiled, disk-backed layer engine. Vector retains its object, point and project-size safeguards; PSD import/export retains its separate format/resource limits.
- Undo accounts for shared buffers once and excludes live-canvas buffers. Cached history stores binary pixel blocks with gzip compression and preserves shared structure buffers, instead of encoding every pixel as a JSON integer on the drawing worker.
- Local regression checks add 100 empty Raster layers at 2048 × 1536, save/reload, draw and undo/redo; editor-dispatched stylus input is checked before and after 70 added layers. Vector save/reload and undo/redo are checked with 81 layers. Physical Xiaomi validation remains pending.

## ColorDrop feedback in preview3

The active color circle moves with the finger or pen while dragging to fill. It remains visible during threshold adjustment. The toolbar swatch returns after release or cancellation. Corrected overlay coordinates previously placed the drag circle at the screen corner.

## Vector fill, connected paths and history

- Drag the active color from the top-right corner onto the interior of a closed Vector object. The floating circle follows the pointer. ColorDrop changes the topmost visible closed object's fill; locked objects reject the operation. Drops on empty space or open paths explain how to close the path. This edits object fills, not arbitrary regions formed by overlapping objects.
- To close a freehand stroke: choose **Nodes**, select the path, then **Close path** in the bottom bar. **Open path** reverses that operation. The closed flag is saved and exported as an SVG closed path.
- To join separate strokes: select an open path with **Nodes**, optionally select its first endpoint (otherwise its last endpoint is used), choose **Join ends**, and tap an endpoint on another open path in the same unlocked layer. The original path's stroke style is kept. Both object transforms are respected and the result remains editable. Use **Close path** if it should enclose an area.
- Tap with two fingers for Undo, three fingers for Redo in Raster and Vector. Small finger movement is tolerated; moving more than 12 dp or holding longer than 500 ms counts as navigation rather than a history tap. The Vector Prefs panel exposes both gesture toggles. Device-level three-finger screenshot gestures must not intercept these taps.
- Raster retains the latest 64 edits in the current canvas session. Above its 96 MiB resident history budget, older changes are gzip-compressed into private app cache files and loaded when needed. Entries are removed when history is discarded or the engine closes. A failed history read leaves the artwork and stack unchanged and reports an error.
- Vector retains up to 64 compressed snapshots, with at least 30 steps retained when its 32 MiB history budget is exceeded. Undo and Redo share the same timeline; making a new edit after Undo starts a new branch and clears Redo. History does not survive closing/reopening a document.

## Input and navigation fixes in preview2

- Brush, Smudge and Eraser select the tool and open its library on the first tap. Tapping the active tool again closes its open library.
- Opening an artwork immediately dims its gallery card and shows a loading overlay. Repeated taps are ignored until the first load finishes; the Raster editor appears after its first rendered frame is available.
- Returning to Gallery shows “Saving artwork…”. Back cancels navigation; late callbacks cannot replace a newer canvas. Failed opens restore the gallery and allow retry.
- Pen-only blocks finger painting, erasing and smudging. Touch pan, pinch zoom, rotation, controls, ColorDrop and object editing remain available. Control palm protection applies while the pen is physically down, not after hover or release.
- Vector two-finger navigation now includes rotation. Canvas-position lock still intentionally prevents view navigation.


This working-tree APK extends the published 0.1.7 preview. It has not been pushed or released on GitHub. Install it over the existing preview; the package and signing certificate are unchanged. Keep editable backups before testing.

## Layer masks (Raster)

Open **Layers**, tap the active layer again, and choose **Add layer mask**. A white mask initially reveals everything; an existing selection creates a mask that reveals the selection. The original layer pixels are retained.

- Paint black to hide, white to reveal, or gray for partial coverage. Size, opacity, pressure and brush texture apply. Eraser hides mask coverage.
- The bottom bar provides Black/White shortcuts and **Layer pixels** to leave mask editing. The previous drawing color is restored when leaving the mask.
- A layer's **M** button selects its mask. Layer options include Edit, Enable/Disable, Invert, Apply and Delete mask. Apply bakes the enabled mask into pixel alpha; Delete removes the mask while retaining original pixels.
- Mask operations and strokes support Undo/Redo, layer locking, duplicate, native project saving, flattened image export and layered PSD mask channels.
- Whole-layer affine transforms move the linked mask with the image. Apply the mask before transforming a selection or merging masked layers. Smudge, ColorDrop and image adjustments require selecting Layer pixels first.

Mask strokes currently use CPU rasterization. Compositing documents with enabled masks also uses the CPU; ordinary pixel strokes can still use GPU compute. History and mask storage count against the layer/history memory budgets. Native Raster documents containing masks use version 2; older previews will reject them rather than silently discarding masks.

## Symmetry (Raster)

Open **Actions → Canvas → Symmetry** and choose Vertical, Horizontal, Quadrant or Radial 4. Guides use the canvas center. Symmetry applies to Paint and Erase, including mask strokes, and the complete mirrored stroke remains one Undo action. Marks exactly on an axis are deduplicated. Smudge is not mirrored. The selected mode is an app preference and survives a new canvas or relaunch.

Movable/rotatable symmetry axes and additional radial division counts are not included.

## Editable QuickShape (Raster)

Enable **Actions → Prefs → Hold to perfect shape**. Draw a line, circle, oval or rectangle and hold the endpoint still for about 0.65 seconds. After lifting the pen:

- Drag inside the shape to move it.
- Drag a corner to resize; circles/squares retain their proportions.
- Drag the handle above the shape to rotate. Lines use endpoint handles.
- Use **Circle** or **Square** to constrain a recognized oval/rectangle.
- Choose **Done** to commit one Undo action, **Cancel shape** to discard the shape, or start drawing outside its bounds to commit and begin another stroke.

Saving, switching tools and other editing commands commit the pending shape first. Autosave waits while it is being edited. Drag previews are coalesced on the drawing worker to avoid building a queue of old handle positions. The handles edit the pending shape, not previously committed artwork; use Transform for older artwork. The result is rasterized brushwork, without later Vector anchors.

## Reference image companion (Raster and Vector)

In Raster, open **Actions → Canvas → Reference image**. In Vector, open **Actions → Reference image**.

- **Import** loads a reference image without adding a drawing layer.
- **Canvas** captures a static snapshot of the current artwork.
- Drag the title to move the window; drag its bottom-right corner to resize.
- Drag the reference to pan, pinch to zoom, and use **Fit** to reset.
- Enable **Pick** and tap the image to select a color.
- Close with ×. Reopening restores the reference image and window position for that artwork.

Reference images are downsampled to approximately two megapixels. The image and window state are stored locally per artwork; they are not embedded in exported `.inkstone`, PSD or SVG files and do not sync between devices. Canvas captures are static. EXIF orientation correction is not yet implemented.

## Layered PSD import

Use **Gallery → Import** or **Actions → Add → Import Inkstone / PSD document**. Vector Actions also provides an Import entry. The current artwork is saved before opening the picker; a successful PSD import opens a new Raster document. A failed/canceled import keeps the current artwork.

The reader follows the [Adobe PSD file format specification](https://www.adobe.com/devnet-apps/photoshop/fileformatashtml/) and supports standard PSD version 1, **8-bit RGB**, raw channels, PackBits RLE, ZIP and ZIP prediction. It retains raster layer pixels, signed layer offsets/cropping, names including Unicode, opacity, visibility, supported blend modes, clipping, protection flags, DPI and grayscale user masks. PSDs without layer records import their merged image as one layer with an explicit notice.

Limits and behavior:

- At most 128 MiB input, 16 megapixels, 64 retained layers, and the shared layer/mask memory budget. Over-limit files are rejected.
- PSB, CMYK, 16/32-bit channels, groups, unsupported blend modes, adjustments/effects and vector masks are rejected with instructions to simplify or rasterize in the source editor.
- Text/smart objects with stored raster data import as raster pixels, with a notice; their editable source data is not retained.
- Embedded non-sRGB profiles are not converted; the app reports that RGB values are treated as sRGB. Mask density/feather metadata is also reported when not applied.
- Direct Photoshop desktop editing has not been tested. Independent psd-tools fixtures verify all four compression types, masks, negative offsets and merged appearance; the imported data remains within two 8-bit channel levels of the independent composite.

## Validation

Local engine, Android framework, independent file-reader and physical GPU checks are recorded in `artifacts/0.1.8-*.log`. Test PNGs are framework renders, not Xiaomi screenshots. A real Android drawing session, stylus behavior, Vulkan drivers, memory/thermals and external editor interoperability still require device testing.

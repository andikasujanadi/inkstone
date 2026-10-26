# 0.1.9-preview7

Early-development release, 27 October 2026. Includes the locally tested preview4–6 work plus grouped-layer indentation. Install over an existing Inkstone preview to retain local artwork; package and signing certificate are unchanged.

## What changed since preview3

- **Layer groups:** create and rename folders, collapse/expand, hide/lock members together, and move layers with long-press drag/drop in Raster and Vector. Group members are indented 20 dp; root layers and folder headings remain aligned left. Reparenting animates both horizontal indentation and vertical placement. The blend-options dropdown follows the member indent.
- **Clear placement feedback:** pulsing insertion lines above/below rows; a rectangular glow only when joining another folder. Drag above a folder heading or below its last member to leave it. There is no Outside groups row.
- **Gallery organization:** date/name sorting, tags and tag filters, named stacks with artwork counts, bulk organization, and direct artwork drops into stack cards or any destination in the Drag to strip. Sorting/filtering and stack moves use eased card movement/crossfades. Tap an artwork's ⋯ button for its context menu.
- **Brush fixes:** persistent Recent history per tool; category changes preserve sidebar scroll. Fade keeps its radius and arbitrary stroke length, responds to input speed, and has a softer opacity lead-in to reduce the remaining slow-start cap. Physical-device feedback is still needed.
- **Smooth Warp:** curved interpolation through the 3×3 cell controls, shared by live preview and the committed image.
- **Named history feedback:** Undo/Redo identify the action, including layer operations, brush/shape strokes, transforms and Liquify.
- **Guide overlays:** isometric and 1/2/3-point perspective with editable vanishing points, adjustable spacing and opacity in both editors. Raster: Actions → Canvas → Drawing guide settings. Vector: Actions → Drawing guides.
- **Raster Liquify:** Adjustments → Liquify offers Push, Pinch, Expand, Twirl and Restore, with live previews, size/strength controls, Cancel and one-step Undo on Done.

## Limits

Folders and gallery stacks are one level. Folder blend/opacity and gesture-based group scaling are not included. Basic folder content movement uses numeric pixel offsets. PSD/SVG exports retain individual layers, not the folder hierarchy. Gallery tags/stacks are local library metadata and are not embedded in exported artwork.

Guides are overlays rather than assisted stroke snapping, and their settings are stored on the device. Liquify uses CPU raster resampling; apply layer masks first. It does not edit Vector geometry, and repeated resampling can soften edges. Hardware pen/Vulkan behavior, animation frame pacing and large-radius Liquify performance require Android device testing.

## Contributors and licensing

Contributor and third-party attribution is retained. Contributing does not automatically grant commercial software publishing rights. See [CONTRIBUTING](../CONTRIBUTING.md), the [commercial publishing policy](CONTRIBUTOR_PERMISSIONS.md), and [licensing](LICENSING.md).

Detailed workflows are retained in [preview4](LOCAL_PREVIEW_019_4.md), [preview5](LOCAL_PREVIEW_019_5.md) and [preview6](LOCAL_PREVIEW_019_6.md). The current folder-boundary instructions supersede earlier Outside groups instructions.

## Validation

- 95 Android framework/JNI tests passed, including Raster/Vector member indentation and returning a layer to the root. Existing coverage retains drawing, gallery, animated drops, guide and Liquify checks.
- 67 standard Rust tests passed. Native libraries are unchanged from preview6's ten passing physical Metal GPU checks; Android Vulkan/stylus and animation pacing still require device testing.
- Android build and lint passed: zero errors, 97 warnings. Both packaged ABI libraries match the validated native outputs; fourteen JNI methods and 16 KB ELF alignment. V2 certificate matches prior previews. Bundled licensing files match the release source.
- APK: `inkstone-0.1.9-preview7-debug.apk`, 10,352,969 bytes, package `art.inkstone.preview`, version code 25. SHA-256: `3d5addd0e19859262afeda5f93166f847083b58d788a374b5c5de3ae48487952`.

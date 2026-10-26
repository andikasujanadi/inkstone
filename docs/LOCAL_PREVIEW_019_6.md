# Local 0.1.9-preview6

Unpublished build for device testing, 27 October 2026. Install the preview6 APK over the existing preview; the package and certificate are retained.

## Fade

Timed Fade strokes now have a short, distance-based opacity lead-in, keeping the brush radius constant. This smooths the remaining cap visible on slow starts, including after a stationary pen-down. The final lead-in is shortened for very short strokes. Taps, unrestricted length, speed-sensitive coverage and the fading tail remain.

## Layers: placement and animation

- Hold a layer row and drag it up/down. A pulsing line at the top or bottom marks the insertion position. The rectangular glow is reserved for entering a different folder.
- Drop on the middle of a folder heading to join it; a collapsed destination opens.
- To leave a folder, drag above its heading or below its last member. The layer becomes a root layer. **Outside groups has been removed.** A folder with only one member also supports dragging that member beyond its own boundary.
- Rows settle into place using 300–380 ms easing, and the layer list preserves its scroll position. This applies to Raster and Vector.

## Gallery: sort, tags and stacks

The row below the gallery header contains **Sort**, **Tags**, and **New stack**. Sort by updated date, name, or creation date in either direction. Tags filters the library; **All tags** clears the filter.

A stack has its own name, thumbnail covers and artwork count. Tap to open it; use **‹ Gallery** or Back to return. A filtered stack shows the number of matching artworks out of its total. Stack organization is one level.

- Tap **⋯** on an artwork for Rename, Duplicate, Delete, Tags and Move to stack. Tags are comma-separated; leaving the field empty removes tags.
- **Select** several artworks, then **Stack** or **Tags** for bulk organization. Choose an existing stack or create one.
- **Hold and drag an artwork** onto a stack card. The **Drag to** destination strip also accepts drops into any named stack or back into Gallery, including while inside another stack. A selected set can move together.
- Long-press a stack to rename it or ungroup it. Ungrouping retains the artworks.
- New canvases created while browsing a stack are saved into that stack.

Sorting, tag filtering/editing, opening stacks and moving artwork use eased card movement/crossfades. Stack drops animate toward the destination; insertion targets pulse. Existing visible thumbnails are reused during reordering. Metadata-only edits preserve the current scroll position; changing the folder, sort or tag filter returns to the top.

Gallery tags, stack membership and creation dates survive artwork saves, renames, duplication and app restarts. The gallery library is local app metadata; it is not embedded in individual `.inkstone`, PNG, JPG, PSD or SVG exports. Physical Android drag/animation timing still needs device testing.

## Retained tools

Preview5's named history feedback, perspective/isometric guide overlays and basic Raster Liquify remain. See [preview5 instructions](LOCAL_PREVIEW_019_5.md) for their access and limits. Earlier instructions mentioning Outside groups are superseded by this build.

## Validation

- 94 Android framework/JNI tests, 67 standard Rust tests and ten physical Metal GPU tests passed. New coverage includes slow Fade lead-ins, short marks, sole-member folder exit, edge-versus-folder glow, row settlement, persistent gallery metadata, mixed Raster/Vector sorting and tag filters, actual artwork drops across stacks, and gallery snapshot settlement.
- Android build/lint succeeded: zero errors, 97 warnings. Independent SVG, layered PSD and masked PSD fixture validation passed. Gallery and tag-filter screenshots were inspected.
- Package `art.inkstone.preview`, version code 24, `0.1.9-preview6`. Existing v2 signer retained. Both packaged native libraries match the rebuilt ARM64/x86-64 outputs; fourteen JNI entry points per ABI, 16 KB ELF alignment, and bundled license/source equality verified.
- APK `releases/inkstone-0.1.9-preview6-debug.apk`: 10,352,825 bytes; SHA-256 `9c7fe7ef6a26602e8b8782442c57ebacea797444275065ed64c6e97e0e05abcb`.

No push, tag or GitHub release was made. Physical Xiaomi/Samsung input, Android Vulkan, and animation frame pacing remain device-test tasks.

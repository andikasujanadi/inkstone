# Local 0.1.9-preview4

Unpublished test build, 26 October 2026. Install `releases/inkstone-0.1.9-preview4-debug.apk` over the existing preview. Package and signing certificate are retained. Physical Android stylus and Vulkan behavior still need device testing.

## What to try

- **Curved Warp:** Transform → Mode → Warp. Move an interior handle in the 3×3 cell grid. Grid lines and artwork now interpolate smoothly through the handles. The live preview and committed transform share a 24×24 sampled surface. Folded cells are rejected; Done makes one Undo step and Cancel restores the source. Freeform continues to resize width and height independently.
- **Speed-sensitive Fade:** choose Inking → Fade Brush. A faster stroke produces lighter coverage and a longer fading tail; a slower stroke is denser. Brush width stays constant, and stroke length is unrestricted. Tail opacity finishes at pen lift. Real event timestamps, including historical samples, travel through merged input batches. Paint/Smudge/Eraser retain independent profiles.
- **Recent brushes:** choose several brushes, then Recent. Up to twenty distinct presets are retained per tool across app restarts. Selecting from Recent keeps that category open.
- **Category scrolling:** scroll to a lower library category and select neighboring categories. The category column stays in place while only the brush list changes.
- **Raster groups:** Layers → Group creates a folder containing the active layer. Tap a layer again → Layer group to move it into a folder, create a folder, or remove it. New layers inherit the active layer's folder. Tap a folder heading to collapse/expand; its checkbox hides/shows all members. The `…` menu renames, locks, moves contents or ungroups it.
- **Vector groups:** Layers → Create layer group. Layer options → Layer group changes membership. Folder headings collapse/expand; checkbox and `…` provide visibility, locking, renaming, movement and ungrouping.

Groups currently have one level. Move contents accepts horizontal/vertical pixel offsets and moves all unlocked members in one Undo step; direct gesture-based group scaling/rotation, group opacity/blend modes and nested folders are not included. Raster movement clips pixels outside the canvas, so use Undo to restore clipped content.

Editable `.inkstone` saves preserve group membership, folder names, collapse state, visibility and locks. Group visibility/locks preserve each member's own settings. PSD export retains individual raster layers and their effective visibility/locks; folder hierarchy is not exported to PSD. Existing PSD-group import restrictions remain. SVG exports retain individual named layer elements and effective visibility, rather than folder hierarchy.

## Local validation

- 84 Android framework tests against the real host JNI engine; 61 standard Rust tests; nine physical Metal GPU tests.
- Tests cover Fade speed response, merged timed JNI input, CPU/GPU alpha agreement, smooth mesh tangents/handle interpolation, Warp preview/commit/Undo parity, persistent per-tool Recent history, stable category scroll views, group UI collapse, group save/load, member flags, locks, movement and Undo.
- Independent SVG, layered PSD and masked PSD fixture checks passed. Warp and Raster group screenshots were visually inspected.
- Android lint: zero errors, 95 warnings. APK v2 signing, both ABI libraries, fourteen JNI entry points, 16 KB alignment and version metadata verified. The packaged native libraries match the rebuilt ARM64/x86-64 outputs.
- APK: 10,250,653 bytes. SHA-256: `2554cfad1419b01b17551726d0b447c8d83c68e0138e4b07864aa6b53a468979`.

No push, tag, merge or release was made for this build. See [known issues](KNOWN_ISSUES.md) for device retesting boundaries.

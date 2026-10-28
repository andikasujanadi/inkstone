# Inkstone 0.20

Early-development release for Android 8.0 and later. Vector mode and Paint Mixing remain experimental.

## What changed

- A dedicated Paint Mixing palette/swirl button sits beside Brush. Adjust mixing strength, color pickup and wetness without entering Brush Studio. Choose Brush to return to dry painting; tip changes while mixing retain the mixing controls.
- Multicolor stays in Brush Studio and the library's + menu. Seven redundant built-in Rainbow presets were removed; configurable multicolor and personal brushes remain available. There are 50 built-in procedural presets.
- Brush/library actions, category management, naming and sliders now use Inkstone panels. Rapid switching no longer leaves an interrupted panel transparent.
- Layer-drop animation begins at the last release position on both axes, including upward and downward movement.
- In Canvas Atlas, select Eyedropper and touch an inactive artwork to sample without switching the active document. Holding an inactive artwork while editing another canvas also samples; holding in overview still arranges artwork.
- Hold still on the drawing canvas to pick a color directly, including finger hold in pen-only mode. Recent colours remembers up to 30 colors used in completed strokes, fills and vector objects.
- Gallery remains normal weight, with a soft stylus-hover glow. Gallery, Save, panel headings and background controls adapt to larger fonts; Apply aligns with the hex field.

## Install and artwork

Download `inkstone-0.20-debug.apk` from [the 0.20 release](https://github.com/bramastahartono20/inkstone/releases/tag/v0.20). Package `art.inkstone.preview`, version code 33; ARM64 and x86-64 are included. The existing preview signing certificate is retained, so installation over an earlier official preview keeps local artwork. Export editable `.inkstone` backups before uninstalling or clearing app data.

## Limits

Paint Mixing is an approximate active-layer RYB paint-load model. It samples pre-stroke pixels and uses CPU painting; it is not spectral pigment, fluid, impasto or independent bristle simulation. Symmetry shares the loaded paint. The dramatic paint imagery in promotion is illustrative.

Atlas samples saved previews up to 480 pixels on the long edge. Fine details can give approximate colors; unsaved changes are not reflected in another artwork's preview. Inactive canvases are not opened as live drawing engines.

Physical Android stylus input, animation pacing, Vulkan behavior and sustained thermals still require tablet testing. Local automated results and APK evidence are recorded in [verification](VERIFICATION.md).

## Promotion

[Paint Mixing artwork](../artifacts/promotion/05-paint-mixing.png) and [Canvas Atlas artwork](../artifacts/promotion/06-canvas-atlas.png) are supplied promotional illustrations, not screenshots or measured rendering results.

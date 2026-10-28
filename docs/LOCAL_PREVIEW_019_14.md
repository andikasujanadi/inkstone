# Inkstone 0.1.9-preview14 — local preview

Not published. Package `art.inkstone.preview`, version code 32, with the existing preview signing certificate. Install over the earlier preview to keep artwork; export project backups before uninstalling or clearing app data.

- Paint Mixing has its own palette/swirl toolbar button immediately beside Brush. It opens mixing controls directly and uses the regular paint tool underneath. Choose Brush to return to dry painting. Mixing strength, pickup and wetness persist, and changing the tip while mixing retains the mixing settings.
- Multicolor remains inside Brush Studio and is also reachable through the brush library's + menu. Seven built-in Rainbow-named presets have been removed. Rainbow/Spectrum configuration, other palette presets and personal brushes remain available.
- Brush library actions, naming, category management, duplication, deletion and brush controls use Inkstone panels. Sliders support touch, keyboard and accessibility progress actions. Rapid panel changes finish the outer panel's interrupted reveal rather than leaving it transparent.
- Layer dragging uses the complete row as its drag shadow. Settlement starts at the last release position on both axes, including upward/downward moves. Other rows animate into their new positions.
- Atlas: select Eyedropper and touch an inactive artwork to sample its color without opening it or changing the current document. While an active canvas is in editing mode, holding an inactive artwork also samples. Move across inactive artworks to sample more colors. In overview, ordinary holding still moves artworks. Two-finger navigation remains available.

Atlas color sampling uses saved previews (up to 480 pixels on the long edge), with a small temporary sampling bitmap; it does not load inactive live drawing engines. Fine details may produce an approximate color, and unsaved changes are not represented in another document's stored preview. Mixing remains the experimental active-layer RYB model described in preview13.

Validation: 122 Android framework/JNI tests and 77 Rust tests passed. Build and lint completed with 0 errors and 125 warnings. Regression tests cover independent mixing/painting, custom brush controls, inactive Atlas picking without document switching, and release-position layer motion in both directions. New panel screenshots were inspected. APK static checks, both native ABIs, 14 JNI exports, 16 KB ELF alignment, matching license assets and the existing v2 signature were verified. Physical Android input and animation pacing still need tablet testing.

APK: `releases/inkstone-0.1.9-preview14-debug.apk` (10,987,919 bytes).
SHA-256: `1722011b2e4d4bfcca4ddefdc601aad606e958121c1032fd67c3845dc9315a5f`.
Evidence: `artifacts/preview14-full-test-summary.json` and `artifacts/apk-verification.json`.

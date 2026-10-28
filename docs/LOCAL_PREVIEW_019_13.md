# Inkstone 0.1.9-preview13 — local preview

Local APK only; not published. Package `art.inkstone.preview`, version code 31, same signing certificate. Install over an existing preview to retain artwork. Export `.inkstone` backups before uninstalling or clearing app data.

- Gallery stays normal weight. Its width follows the actual text size. A soft blue text glow appears only for stylus hover and fades on exit. Save expands with the font size; Undo/Redo remain centered.
- Panel headings wrap with their measured height. Background controls expand with the text size, and Apply aligns with the hex field. Regression checks use 170% fonts in Raster and Vector.
- Hold still on the drawing canvas for roughly 450 ms to pick a color directly. Finger and pen work, including finger hold in pen-only mode. The temporary drawing mark is canceled. Movement, additional pointers, tool changes and cancel events stop the hold. Vector picks the rendered artwork without adding a path.
- Colors used in completed Paint strokes, fills and Vector objects enter Recent colours in the Colors panel. Up to 30 distinct colors persist across sessions. Palette editing is separate; canceled strokes and mask painting do not add ink colors.
- Paint Brush Studio adds Mix strength, Color pickup and Wetness. Pickup loads paint from the active layer; wetness controls retention along the stroke and softens coverage near the edge. Settings persist and are saved in personal presets. Set Mix strength to 0 to turn mixing off.

Mixing is an experimental RYB approximation with a basic brush-load model, not spectral pigment, liquid/impasto simulation or independently simulated bristles. It samples the active layer's pre-stroke pixels and uses CPU painting; ordinary painting retains its GPU path. Symmetry currently shares the wet brush load.

Validation: 119 Android framework/JNI tests, 77 Rust tests and 12 desktop Metal GPU checks passed. Large-font framework screenshots were inspected. Android tablet input behavior, visual pacing and Vulkan performance still need physical-device testing. Package evidence is in `artifacts/preview13-full-test-summary.json` and `artifacts/apk-verification.json`.

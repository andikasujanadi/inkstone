# Pad 7 and community testing

Start with a new artwork; export an `.inkstone` copy before uninstalling a preview build.

## Xiaomi Pad 7 + original Xiaomi pen

1. Install the APK and open the default canvas.
2. Draw strokes from light to firm pressure with Studio Pen and Graphite. Check the pressure range in Actions → Prefs → Stylus diagnostics. Record the pen's exact model and HyperOS version.
3. Test tilted pencil marks, hover cursor, and the side button. Record which events Android actually exposes; manufacturer specifications alone are not runtime evidence.
4. Rest a palm on the screen while drawing. Enable Pen-only input. Verify that canceled strokes leave no marks and that finger navigation remains usable.
5. Pan, rotate, and zoom with two fingers. Try two-finger undo and three-finger redo without adding stray marks.
6. Add several layers, hide/reorder them, change opacity, try alpha lock and clipping masks, then undo and redo the edits.
7. Select an area and paint/fill/adjust/translate it. Confirm other pixels remain intact.
8. Export PNG and editable `.inkstone`. Reopen the document and verify layers and opacity survive. Open the PNG in another app.
9. Return to Gallery, rename and duplicate the artwork, restart the app, and verify both copies remain.
10. Rotate the tablet, enter split screen, background the app, and resume. Check toolbar/panel placement and saves.

## Community bug reports

Include device model, Android/OS version, pen model, APK version, canvas dimensions, layer count, reproduction steps, and whether finger or pen input was used. Include a small exported test document when relevant, using non-private artwork.

Physical Xiaomi testing and a broader Android device matrix remain pending. Local tests cannot certify drawing latency, palm rejection quality, or device GPU behavior.

## GPU renderer — 0.1.6

1. Install over the previous preview, export an editable backup, and open Actions → Prefs → Drawing renderer. Record the active device/backend and any fallback reason. Auto should report Vulkan on a supported Xiaomi GPU. A software adapter is rejected in favor of the CPU renderer.
2. On a 1600 × 1200 canvas, compare GPU · Auto and CPU using a 400 px Soft Airbrush and Soft Brush smudge. Draw long lines, then 60 short strokes; watch for delayed marks, missing endpoints, or increasing lag.
3. Repeat with 4+ visible layers, alpha lock, a selection, clipping, each blend mode, Undo/Redo, ColorDrop followed by drawing, and saving/reopening. Change renderer after a stroke and verify artwork is preserved.
4. Repeat after backgrounding/foregrounding and rotating the tablet. Run for 10 minutes and record heat, battery use, UI freezes, and memory failures. Compare thin pen strokes too; GPU submission/readback overhead may outweigh raster savings for small brushes.
5. If Auto falls back, capture the reason from Drawing renderer and continue with CPU. Include APK version, HyperOS version, canvas size, layer count, and brush name when reporting.

Metal tests on the development Mac and APK packaging checks do not certify the Android Vulkan driver or physical stylus latency.

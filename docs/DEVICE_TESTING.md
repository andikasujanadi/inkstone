# Pad 7 and community testing

Start with a new artwork; export an `.inkstone` copy before uninstalling a preview build.

## Xiaomi Pad 7 + original Xiaomi pen

1. Install the APK and open the default canvas.
2. Draw strokes from light to firm pressure with Studio Pen and Graphite. Check the pressure range in Actions → Prefs → Stylus diagnostics. Record the pen's exact model and HyperOS version.
3. Test tilted pencil marks, hover cursor, and the side button. Record which events Android actually exposes; manufacturer specifications alone are not runtime evidence.
4. Rest a palm on the screen while drawing. Toggle Paint with finger off. Verify that canceled strokes leave no marks and that finger navigation remains usable.
5. Pan, rotate, and zoom with two fingers. Try two-finger undo and three-finger redo without adding stray marks.
6. Add several layers, hide/reorder them, change opacity, try alpha lock and clipping masks, then undo and redo the edits.
7. Select an area and paint/fill/adjust/translate it. Confirm other pixels remain intact.
8. Export PNG and editable `.inkstone`. Reopen the document and verify layers and opacity survive. Open the PNG in another app.
9. Return to Gallery, rename and duplicate the artwork, restart the app, and verify both copies remain.
10. Rotate the tablet, enter split screen, background the app, and resume. Check toolbar/panel placement and saves.

## Community bug reports

Include device model, Android/OS version, pen model, APK version, canvas dimensions, layer count, reproduction steps, and whether finger or pen input was used. Include a small exported test document when relevant, using non-private artwork.

Physical Xiaomi testing and a broader Android device matrix remain pending. Local tests cannot certify drawing latency, palm rejection quality, or device GPU behavior.

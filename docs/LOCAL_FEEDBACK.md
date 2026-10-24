# Local feedback preview — 0.1.7-dev

> Historical implementation notes from the local previews. These changes are included in the 0.1.7 early-development release. Publication statements below describe the earlier preview stage.

This is an unpublished working-tree build for review. It has not been pushed, tagged, or uploaded to GitHub.

## Changes

- Actions → Prefs → Pen-only input: as of 0.1.8-preview2, finger input cannot paint, erase or smudge. Fingers can still navigate, select, transform, pick colors, change controls and use ColorDrop. Stylus and eraser tool types remain accepted.
- Protect controls from palm: enabled by default. Finger gestures beginning on the dock are ignored while a stylus was touching/hovering within 700 ms; toolbar touches are also guarded during a pen stroke. This supplements Android's hardware palm rejection and can be disabled. It does not identify palms independently of device input reports.
- Stabilization persists across panels, brush selection, new canvases, and app preferences. The 100% control correctly maps to the engine's 0.95 maximum and reopens at 100%. This fixes persistence and slider normalization; the filtering algorithm is unchanged.
- The 44 dp side rail is solid and fills the complete editor height below the toolbar. The drawing viewport reserves its width. Right-hand mode moves both rail and reserved space; tablet and phone layouts reflow. The outer dock edge is intentionally square; slider tracks keep their rounded corners.
- Undo/Redo sit in the top toolbar with a visible Save button. Save offers local-gallery saving and PNG/JPEG/editable document export through the existing Android file picker. The eyedropper has a recognizable pipette icon.
- UI colors retain a close dark-neutral/blue appearance with shared Inkstone values: toolbar `#1E2023`, workspace `#2B2D30`, surface `#25282C`, rail `#23272B`, accent `#248EF0`. These affect chrome, not document pixels or the user's saved palette.
- Transform adds Smaller/Larger (10% steps). Scaling/rotation/flip are centered on selected painted content, or painted content of the active layer, rather than the full canvas. Translation remains available by dragging. These are committed raster operations; live scale handles, warp, and a non-destructive transform session are not implemented.
- The activity no longer forces the screen to remain awake when idle. Thermal/battery improvements have not been measured and are not claimed.

## Local validation

- 30 Rust tests passed; three physical-GPU tests remain opt-in and were not rerun for this UI-focused build. The new Rust regression checks off-center object scaling and exact Undo.
- 21 Android framework/JNI tests cover pen-vs-finger drawing, strict slider input, hover-based palm guard and its timeout, full-height docking on either side, top Save/export discovery, and stabilization at 100% across panel/new-canvas changes, alongside the existing integration suite.
- Framework renders were inspected for tablet, phone, preferences and stabilization. They are not physical-device screenshots. The supplied `artifacts/editor-tablet.png` is preserved.
- APK v2 signature, both ABI libraries, twelve JNI methods per ABI, and 16 KB ELF alignment passed. Signing matches the previous preview. Lint has zero errors and 53 warnings. APK hash and size are in `artifacts/apk-verification.json`. Physical Xiaomi palm behavior, sustained latency, driver performance, heat, battery use, and Android export destinations still require device testing.

Install the local APK over the existing preview to retain its package and signing identity. Export editable backups before clearing app data or uninstalling.

The local build also adds PNG/JPG Photo Gallery saving and layered PSD export; see [EXPORT_FORMATS.md](EXPORT_FORMATS.md). The newer `0.1.7-vector-dev` APK also includes selectable Vector canvases; see [VECTOR_MODE.md](VECTOR_MODE.md).

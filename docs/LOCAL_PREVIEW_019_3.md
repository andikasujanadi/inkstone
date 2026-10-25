# 0.1.9-preview3

Early-development build integrating [PR #1](https://github.com/bramastahartono20/inkstone/pull/1), by [andikasujanadi](https://github.com/andikasujanadi), at `f19aa0903aed00e6a84bc3e4e86d42cd712f73cc` into the existing local preview2 changes. Prepared for release on 26 October 2026 after local integration and regression checks. See [known issues](KNOWN_ISSUES.md) for the device-reported Recent, Fade and category-scroll issues, and the missing layer-group feature.

## What to try

- Choose different Paint, Smudge and Eraser brushes, change their size, opacity, flow, pressure response and Fade tail length. Close and reopen the app: each tool keeps its own profile. Existing saved presets without the newly stored fields use their own preset defaults.
- Actions → Prefs → Shortcut keys: assign Undo, Redo and Eraser / Previous. Defaults are Ctrl+Z, Ctrl+Shift+Z and E. Reset restores the default binding. Holding E does not repeatedly toggle tools. B still selects Paint.
- In Vector, Actions → Shortcut keys records keys directly in the dialog. E toggles Select / Previous because Vector has no eraser tool. Undo/Redo shortcuts act on the Vector document. Dismissing the dialog cancels recording.
- Quickly pinch inward with two fingers to fit the Raster canvas and reset its rotation. Toggle this in Actions → Canvas or Gesture controls. Canvas lock disables the gesture. Touching during the animation stops it at its current position rather than snapping to the end.
- Open a right-side library, then Actions: Actions stays on the left. Preferences, Studio and adjustment subpanels update in place, with back navigation. Category and active brush scroll into view when opening a tool's library.

## Earlier fixes retained

Preview2's constant-width opacity-tail Fade Brush, full Raster selections, Freeform/Uniform/Distort/Warp, expandable six-group bottom toolbar, configurable stylus buttons, pen-only gestures and live Vector colors remain included. See [preview2 details and limits](LOCAL_PREVIEW_019_2.md).

Framework tests use the real host JNI engine. Physical Android stylus, Bluetooth keyboard and Vulkan behavior still require device testing. The native engine is unchanged from preview2; this integration changes Kotlin and documentation only.

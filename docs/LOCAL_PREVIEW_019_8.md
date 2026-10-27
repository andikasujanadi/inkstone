# Local 0.1.9-preview8

Fixes the width inherited from the last open popover. Replacing a panel in place now reapplies its own width, height, alignment and margins, and updates the glass sampling origin when geometry changes. The same outer panel is retained, so switching menus does not replay the opening animation.

On a sufficiently wide tablet, brush/eraser/smudge libraries use 450 dp, Layers and Colors use 350 dp, and Actions uses 390 dp; widths are clamped to the available viewport. Subpanels use their own layout rather than retaining the previous panel's width.

Try Layers → Brush → Eraser → Layers → Smudge → Colors → Brush repeatedly. Also open Actions → Prefs → Pressure and Smoothing. Width and left/right placement should remain appropriate for each menu.

## Local validation

- 96 Android framework/JNI tests passed, including a new regression that verifies panel identity, measured widths, right margin and left-panel alignment during repeated switches.
- Android build/lint passed: zero errors, 101 warnings. Rust/native code and ABI binaries are unchanged.
- Package `art.inkstone.preview`, version code 26, version `0.1.9-preview8`; existing v2 certificate retained. Both packaged native libraries and license assets match current source; fourteen JNI methods and 16 KB ELF alignment verified.
- APK: `releases/inkstone-0.1.9-preview8-debug.apk`, 10,353,009 bytes; SHA-256 `e12ef4ff4f89ae029e4b6d3d9d071299b9a3bca66100186e912972da9ac61655`.

Local test build only; no GitHub push or release was made for this fix. Physical-device layout testing remains pending.

# Local 0.1.9-preview1 — 25 October 2026

Unpublished device-test APK. No commit, push, tag, GitHub release, or replacement of existing release assets has been made for these changes.

## Stylus barrel button — Raster

Open **Actions → Prefs → Gesture controls → Stylus button**. Choose:

- Hold to erase (default): use the independent Eraser preset, size, opacity, flow, fade and edge hardness, including when pressed partway through a stroke. Releasing resumes the drawing profile. The native stroke retains a single Undo record.
- Hold for Eyedropper: hold the button and touch/drag on the canvas to sample a color.
- Undo or Redo: one action per press; repeated key-down events do not repeat the action.
- Switch brush / eraser: switch the selected tool on each press.
- None: no app action for the button.

The binding is saved. The previous Pen button eraser preference migrates to Hold to erase or None. Primary/secondary barrel motion bits, legacy stylus secondary/tertiary bits, modern stylus button key events, and stylus-sourced Back key events are handled. Mouse secondary clicks are excluded. Cancellation, key release, focus loss and pause clear held-button state. Some device/system shortcuts can intercept a button before an app receives it; physical Galaxy Tab S6 Lite/S Pen and Xiaomi validation remains pending. These configurable bindings apply to Raster; Vector input tools are unchanged.

Android input references: [MotionEvent](https://developer.android.com/reference/android/view/MotionEvent), [stylus key events](https://developer.android.com/reference/android/view/KeyEvent#KEYCODE_STYLUS_BUTTON_PRIMARY).

**Colors → Eyedropper** also provides direct canvas sampling without a hold gesture. Existing persistent custom palettes remain available.

## One finger-drawing setting

Pen-only input is the sole control for Paint/Erase/Smudge contact on the Raster canvas. On: only a pen/eraser can paint. Off: a finger can paint too. The redundant Paint with finger toggle and state have been removed, and the obsolete `finger` preference is cleared so old settings cannot silently block drawing. Touch navigation, tool controls, selection, eyedropper and ColorDrop remain available in Pen-only mode. Vector already uses the same Pen-only preference for drawing.

## Fade Brush

Choose **Brush Library → Inking → Fade Brush**. The same preset is available to Eraser through its shared library. It tapers width and fades opacity over 500 document pixels by default, independently of pressure. In Brush/Eraser Studio, **Fade length** ranges from Off to 2000 document pixels; adjust it on any preset. Each stroke restarts the distance counter. Distances follow the stabilized stroke path, rather than elapsed time or input-event count. Symmetry copies share the original stroke's fade progress. The fade-aware brush preview shows the tapered result.

Fade applies before CPU/GPU stamp generation, and the JNI brush settings carry fade length. Native brush updates now respect profile changes during a stroke, fixing ignored mid-stroke eraser settings. This changes the private JNI stroke parameter list; the APK packages matching rebuilt libraries for both ABIs.

## Vector color selection and fill

The wheel previously updated a panel-local variable and hex text while leaving `canvas.ink` unchanged until Stroke/Fill was pressed. Wheel and valid hex edits now update the active ink, toolbar swatch and panel preview immediately. Hex edits synchronize the picker; wheel changes preserve active alpha. **Stroke** and **Fill** explicitly apply that chosen color to the selected editable object. Merely choosing an active color does not recolor a selected object.

Drag the toolbar color circle onto a closed shape to fill using that active color. The operation is undoable. Existing open-path and locked-layer checks remain in place. Local tests cover wheel → hex → toolbar drag → actual object fill → Undo/Redo; the original device report still needs retesting on hardware.

## Validation

See [VERIFICATION.md](VERIFICATION.md) for counts, APK checksum and device-test boundaries. Native and Android checks exercise real pixel output and the full editor event route. Framework screenshots show faded strokes, wide temporary erasing, the stylus menu and a Vector object filled with the selected hex color.

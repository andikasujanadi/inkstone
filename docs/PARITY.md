# Inkstone feature status

Current 0.1.9-preview3 includes the [preview2 selection and transform tools](LOCAL_PREVIEW_019_2.md), plus [PR integration and input improvements](LOCAL_PREVIEW_019_3.md). Fade, Recent history and category scrolling remain device-reported issues. See [workflows](LOCAL_WORKFLOWS.md) and [known issues](KNOWN_ISSUES.md).

| Area | Current implementation | Remaining work |
|---|---|---|
| Canvas layout | Anchored panels and pointers, category glyphs and stroke previews, Actions icon tabs, pill switches, live size HUD, filled toolbar silhouettes, 44 dp tablet bar, interruptible eased popovers | Device testing of layout, timings, gallery transitions and every panel state |
| Gallery | Local documents, thumbnails, create/import, rename, duplicate, delete | Stacks, drag ordering, selection sharing, gallery preview gestures |
| Brushes | Forty-seven procedural presets; distinct airbrush hardness, bristle, marker, grain, and spray masks; pressure, flow, smoothing | Recent history, device-reported Fade behavior and category scrolling; grain/shape editors, dual brushes, brush dynamics, persistent custom libraries |
| Smudge | Spatial color pickup and transport with brush masks and alpha-aware mixing | High-quality wet mixing, physical pigment models, lower smudge latency |
| Erase | Shared procedural brush library, separate tool settings, inverted previews, pressure and edge hardness | Persistent custom libraries |
| Stylus | Android pressure with global disable switch, tilt, hover, cancellation, side-button events | Xiaomi physical testing; calibration curves; prediction; vendor-specific shortcuts |
| Touch | Pan/zoom/rotate with position lock, undo/redo taps, finger eyedropper, editable hold-to-shape fitting, centered drawing symmetry | Movable symmetry axes, QuickMenu, customizable gesture bindings |
| Color | Hue ring, inner color area, Classic square, RGB/hex, basic harmony, persistent named custom palettes, drag preview to save a swatch | Expanded harmony modes, color history, profiles and CMYK; Vector color selection/fill device bug remains open |
| ColorDrop | Drag active color to fill, live threshold preview, radial reveal, cancellation and one-step Undo | Reference layers and further edge refinement |
| Layers | Core operations, layer locking, alpha lock, clipping, editable grayscale layer masks, fifteen blend modes, thumbnails, inline blend dropdown, swipe actions | Groups, reference layers, selection gestures, more blending modes |
| Selection | Automatic/freehand/rectangle/ellipse, Replace/Add/Remove/Invert, Feather, named saved masks, clipboard actions, soft overlay and dashed outlines; edits constrained by coverage | Device testing; more accurate full-resolution boundary preview |
| Transform | Freeform/Uniform/Distort/fixed-grid Warp, bounds and handles, two-finger object gestures, snapping, Nearest/Bilinear, flip, fit/reset, cancel/one-commit Undo | Higher-order warp, persistent image objects, device testing |
| Adjustments | Brightness, saturation, box blur, grayscale, invert | Live previews; hue/curves/color balance; true Gaussian blur; Liquify, Clone, Bloom, Glitch, Noise, Sharpen |
| Canvas | Preset popup, full custom editor, physical units, persisted DPI, background options, workspace grid, fit | Crop/resize after creation, movable symmetry axes, perspective/isometric guides |
| Files | Editable `.inkstone`, layered 8-bit RGB PSD import/export, flattened PNG/JPEG with Photo Gallery saving, image insertion | Additional PSD features, PDF, TIFF, profile conversion, EXIF orientation |
| Vector documents | Separate Pen/Line/Rectangle/Ellipse mode, object move/scale/rotate and cubic anchors/handles, anchor insertion, layers, editable SVG and project export | SVG import, text, boolean operations, gradients, closed-path topology editing, multi-object selection |
| Reference companion | Local per-artwork image, movable/resizable window, pan/zoom, canvas snapshot and eyedropper in Raster/Vector | Embedding reference assets in exports, live canvas reference, orientation metadata |
| Text | Not implemented | Font management, editing, vector/raster conversion |
| Animation / Page Assist | Not implemented | Timeline, onion skin, frame playback/export, multi-page workflows |
| 3D painting | Not implemented | Mesh loading, UV painting, lighting, texture export |
| Time-lapse | Not implemented | Stroke recording, replay, video encoding/export |
| Accessibility | Native labels/focus, basic keyboard bindings, pen/finger toggle | Full TalkBack pass, large-text reflow, switch access, color-independent selection cues |
| Performance | wgpu compute brush/eraser/smudge and live layer compositing; bounded layer cache, CPU fallback, dirty-rectangle readback, batched input, independent storage worker | Tiled renderer, direct GPU presentation, input prediction, Android hardware benchmarks |

Next priorities:

1. Measure Android pen delivery, stroke latency, memory and thermals on physical devices.
2. Improve accessibility, layout and interaction feedback from device reports.
3. Extend tiled GPU rendering and reduce readback overhead.
4. Improve masks, transforms, adjustments, guides and file interoperability.

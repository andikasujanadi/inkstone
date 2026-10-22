# Procreate parity tracker

Reference: [Procreate interface handbook](https://help.procreate.com/procreate/handbook/interface-gestures/interface). This tracker distinguishes working implementations from fidelity goals. No pixel-by-pixel comparison against a live iPad session has been performed.

| Area | Current implementation | Remaining work |
|---|---|---|
| Canvas layout | Screenshot-based panel proportions and pointers, category glyphs and stroke previews, Actions icon tabs, pill switches, live size HUD, filled toolbar silhouettes, 44 dp tablet bar, interruptible eased popovers | Live iPad comparison for exact geometry, timings, gallery transitions, and every panel state |
| Gallery | Local documents, thumbnails, create/import, rename, duplicate, delete | Stacks, drag ordering, selection sharing, gallery preview gestures |
| Brushes | Forty-six procedural presets; distinct airbrush hardness, bristle, marker, grain, and spray masks; pressure, flow, smoothing | Original brush-engine equivalence, grain/shape editors, dual brushes, brush dynamics, persistent custom libraries |
| Smudge | Spatial color pickup and transport with brush masks and alpha-aware mixing | High-quality wet mixing, physical pigment models, lower smudge latency |
| Erase | Coverage removal with pressure | Brush-library-specific eraser textures |
| Stylus | Android pressure with global disable switch, tilt, hover, cancellation, side-button events | Xiaomi physical testing; calibration curves; prediction; vendor-specific shortcuts |
| Touch | Pan/zoom/rotate with position lock, undo/redo taps, finger eyedropper, basic hold-to-shape fitting | Shape editing handles, QuickMenu, customizable gesture bindings |
| Color | Hue ring, inner color area, Classic square, RGB/hex, basic harmony, palette | Match disc mapping, proper harmony modes, history, persistent named palettes, profiles and CMYK |
| ColorDrop | Drag active color to fill, live threshold preview, radial reveal, cancellation and one-step Undo | Reference layers, anti-aliased edge cleanup |
| Layers | Core operations, layer locking, alpha lock, clipping, fifteen blend modes, thumbnails, inline blend dropdown, swipe actions | Groups, layer masks, reference layers, selection gestures, more blending modes |
| Selection | Freehand, rectangle, ellipse, invert; edits constrained by mask | Automatic selection, additive/subtractive masks, feathering, save selections, marching ants for inverted masks |
| Transform | Translation, flip, fixed rotation; selection-aware | On-canvas scale/rotate handles, freeform/distort/warp, interpolation controls, snapping |
| Adjustments | Brightness, saturation, box blur, grayscale, invert | Live previews; hue/curves/color balance; true Gaussian blur; Liquify, Clone, Bloom, Glitch, Noise, Sharpen |
| Canvas | Preset popup, full custom editor, physical units, persisted DPI, background options, workspace grid, fit | Crop/resize after creation, symmetry, perspective/isometric guides |
| Files | Editable `.inkstone`, flattened PNG/JPEG, image insertion | `.procreate`, `.brush`, `.brushset`, PSD, PDF, TIFF, color profiles, EXIF orientation |
| Text | Not implemented | Font management, editing, vector/raster conversion |
| Animation / Page Assist | Not implemented | Timeline, onion skin, frame playback/export, multi-page workflows |
| 3D painting | Not implemented | Mesh loading, UV painting, lighting, texture export |
| Time-lapse | Not implemented | Stroke recording, replay, video encoding/export |
| Accessibility | Native labels/focus, basic keyboard bindings, pen/finger toggle | Full TalkBack pass, large-text reflow, switch access, color-independent selection cues |
| Performance | Dirty-rectangle compositing, batched input, independent storage worker; Android bitmap presentation | wgpu/tiled renderer, input prediction, front-buffer path, hardware benchmarks |

Work toward parity in this order:

1. Install on Pad 7, measure pressure/tilt/button delivery and stroke latency, fix any data-loss or input issues.
2. Compare actual iPad screen recordings and screenshots for the editor, gallery, brushes, colors, layers, and Actions panel; reproduce each interaction state.
3. Build the tiled GPU renderer and improve brush dynamics, smudge, and ColorDrop.
4. Extend transforms, masks, adjustments, import/export, and guides.
5. Add larger feature families such as text, time-lapse, animation, and 3D painting.

There is no claim of compatibility with private Procreate file formats or identical brush output.

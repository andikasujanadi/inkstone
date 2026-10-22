# Inkstone — The Procreate experience, built for Android

Inkstone aims to bring the Procreate drawing experience to Android, starting with tablets and pressure-sensitive pens. It combines a native Android interface with a Rust drawing engine.

**Early development:** this is a work in progress. Features are incomplete, bugs are expected, and many improvements and updates are still ahead. Feedback from real devices helps guide each release.

Inkstone is independent and is not affiliated with Savage Interactive. It contains original code, vector icons, and procedural brush presets. It does not include Procreate's source, artwork, brush assets, name, or file-format implementation.

![Tablet editor](artifacts/editor-tablet.png)

See [verified checks and remaining device tests](docs/VERIFICATION.md).

## Try the Android app

Download the APK from [GitHub Releases](https://github.com/bramastahartono20/inkstone/releases). Inkstone is an early-development preview. Enable installation from the app used to open the APK when Android asks.

- Android 8.0 / API 26 or later.
- ARM64 Android tablets and phones; x86-64 builds are included for testing.
- A pressure-capable stylus is recommended. Finger drawing also works.
- No account, server, internet permission, analytics, or subscriptions.
- Artwork is saved in app-private local storage. Export editable `.inkstone` copies before uninstalling or clearing app data.

Debug builds use package ID `art.inkstone.preview`. Production builds use `art.inkstone`. Debug signing is for testing: configure a stable signing key before publishing release updates.

## GPU update 0.1.6

The Android drawing worker now initializes a Rust `wgpu` compute renderer using Vulkan. Brush and eraser rasterization, spatial smudge pickup/transport, and live layer compositing run on the GPU when a supported hardware adapter is available. The existing CPU renderer remains available and is selected automatically if initialization or a GPU batch fails.

Open **Actions → Prefs → Drawing renderer** to see the active backend/device and choose **GPU · Auto** or **CPU**. The choice persists. GPU initialization and painting stay off the UI thread. Existing artwork, selection constraints, alpha lock, cancellation, and Undo keep their previous document format and behavior.

Layers are cached on the GPU with a 128 MiB residency budget. Input is submitted in batches of up to 128 dabs; only changed pixels are read back into the CPU mirror. This is an initial compute integration: bitmap presentation, readback synchronization, full-layer stroke snapshots, CPU ColorDrop/adjustments/export, and a future tiled renderer remain performance work. Auto keeps brushes below 32 px and small composite patches on the CPU to avoid GPU overhead. See [measured results and architecture](docs/GPU.md).

Local tests ran on a physical Metal GPU, including deliberate device loss and CPU recovery. Android Vulkan binaries are built for ARM64 and x86-64; Xiaomi Pad 7 driver compatibility, sustained latency, thermals, and memory use still need physical-device testing. This release does not claim Procreate-level performance.

## ColorDrop and brush update 0.1.5

Drag the active color circle from the top-right toolbar into a closed shape. Hold still briefly to start the fill preview, then slide left for a lower threshold or right for a higher threshold. Release to commit; cancel the gesture to restore the previous drawing. The fill expands outward from the drop point over 420 ms when system animations are enabled. Live threshold changes create only one Undo step and defer autosave until the gesture finishes.

Airbrushes now have separate soft, medium, medium-hard, and hard coverage profiles. The library includes 46 procedural presets, including new chalk, pastel, bristle, marker, spray, and wash choices. Smudge carries a spatial color tip, mixes it with destination pigment, and uses the selected brush footprint rather than repainting one sampled center color. These are Inkstone brush algorithms, not imported Procreate assets.

Live fill previews use scanline flood filling on the active layer, respect selections and alpha lock, and ignore hidden RGB in transparent pixels. Large-canvas preview and smudge latency still need physical-device measurements. Reference-layer filling, pigment physics, and Procreate brush-file import are not implemented.

## Device feedback update 0.1.4

Classic color selection is a standalone square with a hue strip; palette swatches have square corners and no gaps. Swiped layer buttons now update their own clipping bounds during animation for Android hardware rendering. Brush/color/Actions section changes reuse the panel container, with 260 ms content transitions and a 460 ms brush selection bounce. The smudge silhouette was redrawn. Custom Canvas rebuilds from its current measure specifications, including portrait/landscape and section changes.

- **Actions → Prefs → Pen pressure:** disable pressure response globally, independent of the selected brush.
- **Actions → Canvas → Lock canvas position:** prevent two-finger pan/zoom/rotation while continuing to draw.
- **Actions → Prefs → Hold to perfect shape:** draw a line, circle, oval, square, or rectangle, then hold the endpoint still for about 0.65 seconds. A recognized shape replaces the pending stroke and remains one Undo step. Unrecognized strokes remain unchanged. This is basic geometric fitting, without post-creation shape handles or all Procreate QuickShape features.

Motion respects Android's system animation setting. The hardware-specific swipe fix and timing still need confirmation on Xiaomi Pad 7.

## Interface update 0.1.3

The gallery now opens an anchored light New Canvas menu and a full Custom Canvas editor with a numeric keypad, physical units, DPI, saved presets, background options, and a memory-based layer limit. Editor panels use a cached blurred canvas backdrop; rounded brush-category cards and a faint workspace grid follow the supplied references. Layer blend controls expand inline, and a left swipe reveals working Lock, Duplicate, and Delete actions. Preferences are grouped, with persistent cursor and gesture controls. App surfaces share the Airbrushing tile corner radius (12 dp), including brush selection and size/opacity sliders. iCloud, Project Canvas, CMYK, and time-lapse remain unavailable and are identified as such in the UI.

## Performance update 0.1.2

Long strokes no longer end when autosave runs. Live rendering composites only changed areas, and file compression/writes run separately from input processing. A 60-stroke / 3,840-sample integration test, including a mid-stroke save, preserves every endpoint. See [performance measurements and limitations](docs/PERFORMANCE.md). Install the new APK over the existing preview to retain local artwork; the local update uses the same signing certificate.

## Interface update 0.1.1

The toolbar uses compact filled silhouettes based on the official interface reference. Popovers ease in over 220 ms and dismiss over 140 ms; tool tint changes, press feedback, and full-screen chrome are animated. System-disabled animations are respected. Rapid panel switching is covered by an integration test. Exact Procreate timing and Xiaomi frame pacing remain unverified.

The screenshot-driven revision adds anchored panel pointers, independently scrolling brush categories and stroke previews, six Actions tabs, animated pill switches, and a live Size/Opacity popup. Dynamic brush scaling, rapid undo delay, and selection overlay opacity are adjustable. Project Canvas and time-lapse remain unavailable. Matching brush names describe approximate procedural presets, not the original Procreate brush algorithms or assets.

## Working tools

- Paint, smudge, and erase, with pressure-driven brush size and opacity.
- Forty-six procedural presets across eighteen populated brush categories; adjustable flow and stabilization.
- Pinch, pan, and rotate; two-finger undo; three-finger redo; four-finger interface toggle.
- Hover cursor and pen-side-button eraser when Android reports those events.
- Finger-hold eyedropper and dedicated eyedropper tool.
- Layers, visibility, opacity, rename, duplicate, reorder, clear, alpha lock, and clipping masks.
- Fifteen blend modes, including Multiply, Screen, Colour Burn, Colour Dodge, Hard Light, Difference, and Exclusion.
- Rectangle, ellipse, and freehand selections; selection inversion.
- Translate, horizontal/vertical flip, and 45-degree rotation of the active layer or selection.
- Enclosed-region flood fill, full-layer fill, brightness, saturation, invert, grayscale, and blur.
- Disc/Classic color picking, RGB values, hexadecimal entry, a basic harmony view, and a studio palette.
- Gallery thumbnails, rename, duplicate, delete, new canvas presets, and image insertion.
- Autosave, editable document import/export, flattened PNG/JPEG export through Android's file picker.
- Keyboard shortcuts: B, E, Ctrl+Z, Ctrl+Shift+Z, Ctrl+S, Escape.

The [parity tracker](docs/PARITY.md) records the differences and missing features. In particular, `.procreate`, `.brush`, `.brushset`, PSD, animation, text, 3D painting, time-lapse, advanced warp, and exact brush behavior are not implemented.

## Build

You need Rust stable, JDK 21, Android SDK platform/build-tools 36, and Android NDK 27 or newer. Paths containing spaces are supported.

```sh
rustup target add aarch64-linux-android x86_64-linux-android
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.2.12479018"
export JAVA_HOME=/path/to/jdk-21
bash scripts/build-android.sh
```

The resulting APK is copied to `releases/inkstone-0.1.6-debug.apk`. The build fails if either native ABI library is missing, rather than producing an APK that cannot open the engine.

Open the `android` folder in Android Studio for editing and device installation. Build the Rust libraries with `bash scripts/build-native.sh` after changing the engine.

The optional `scripts/fetch-link-libs.py` is a local-bootstrap alternative for macOS: it retrieves only the official NDK r27c link stubs and verifies the archive-entry CRCs, then links through Rust's bundled LLVM linker. A normal installed NDK remains the recommended path.

## Verify

```sh
cargo test --workspace
cargo build --release --features host-jni
cd android
./gradlew testDebugUnitTest lintDebug
```

The Robolectric tests load the real Rust JNI library compiled for the build host. They exercise document round-trips, input cancellation, gallery saving, tool panels, and tablet/phone layout rendering. Their PNG outputs go into `artifacts/`. These are desktop Android-framework tests; they do not establish physical stylus behavior or device GPU performance.

The GitHub Actions workflow builds the native libraries, runs the tests, and uploads the debug APK and verification artifacts. It does not publish a release automatically. Check [GitHub Actions](https://github.com/bramastahartono20/inkstone/actions) for the current build status.

## Architecture

```
Native Android Views / Kotlin
        │ MotionEvent batches, commands, document I/O
        ▼ JNI
Rust engine
        │ pressure sampling, procedural brush stamps, layer pixels,
        │ blend modes, selection masks, undo patches, document encoding
        ▼ dirty rectangle with straight ARGB pixels (full RGBA export)
Android Bitmap + hardware-backed Canvas presentation
```

The drawing worker uses Rust `wgpu` compute for brush/eraser rasterization, spatial smudge, and live layer compositing, with Vulkan on Android and a CPU fallback. Android still presents a bitmap backed by synchronized CPU pixels. ColorDrop, adjustments, document encoding, previews, and export remain CPU operations. Tiled rendering, direct GPU presentation, input prediction, and physical-device latency work remain next steps. See [GPU architecture and validation](docs/GPU.md).

Undo stores pixel patches and structural changes with a memory budget, up to 250 actions. The actual history length depends on canvas size and edits. Canvas area is capped at 16 megapixels, with a separate layer-pixel budget. Editable documents contain versioned JSON and run-length-encoded straight-alpha layer pixels, compressed with gzip by Android. They are unrelated to the `.procreate` format.

## Repository

- `crates/ink-engine`: portable Rust engine, JNI bridge, behavior tests.
- `android/app/src/main/java/art/inkstone`: native editor, input handling, gallery, and file storage.
- `android/app/src/test`: integration and screenshot checks.
- `scripts`: native/Android builds and static APK verification.
- `docs`: parity scope, device testing, and verification notes.

## License

Original project code is MIT licensed. Dependencies retain their own licenses. Procreate is a trademark of its respective owner.

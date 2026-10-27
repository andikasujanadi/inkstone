# Saving and export — 0.1.8

Save now distinguishes app-private projects, Photo Gallery output, and files in a chosen folder.

| Output | Destination | Contents |
|---|---|---|
| PNG | Pictures/Inkstone or selected folder | Flattened image; preserves transparency |
| JPG | Pictures/Inkstone or selected folder | Flattened image, quality 95; transparency is composited over white |
| PSD | Selected folder | Separate editable raster layers, opacity, names (including Unicode), visibility, clipping and blend metadata; separate background layer, DPI, sRGB ICC profile and merged preview |
| SVG (Vector mode) | Selected folder | Editable paths, shapes, named layer groups, colors and transforms; no embedded raster image |
| `.inkstone` | App-private gallery or selected folder | Original editable Inkstone project, including layers |

Android 10+ Gallery saves use scoped MediaStore storage and publish only after the image is written. A failed write removes the pending record. Android 8/9 request legacy storage permission only when saving to the Photo Gallery; file-picker exports remain available if permission is declined. PSD is a document and is exported to a folder, not the Photo Gallery.

The PSD encoder follows the [Adobe PSD specification](https://www.adobe.com/devnet-apps/photoshop/fileformatashtml/). It writes cropped, uncompressed RGB/alpha channels and retains empty and hidden layers. The merged RGB preview uses the PSD white-matte convention; editable layer channels remain straight alpha. The embedded sRGB profile was generated with LittleCMS through Pillow ImageCms (`createProfile('sRGB')`). This is 8-bit RGB PSD, without CMYK, 16-bit channels, vector objects, smart objects, groups, adjustment layers, or the full set of PSD import features. Inkstone and other editors may composite some clipping/blend cases differently. Photoshop desktop has not been tested directly.

PSD writes run on the drawing worker into an app-cache file, then copy to the selected destination on the storage executor. Pixel history is unchanged and a live stroke is not ended. The encoder streams layer data rather than constructing the entire PSD in memory; the merged image uses one canvas-sized u32 buffer (up to 64 MiB), plus small row/header buffers. Uncompressed files can be large. A canceled picker or normal completion removes the temporary PSD. PNG/JPG export no longer also serializes the whole project unnecessarily. Canceling an image picker recycles the temporary image.

## Local checks

- Rust regression: export leaves a live stroke and its Undo count intact and reports output errors.
- Android/JNI regression: native PSD export, MIME and picker launch, canceled PSD cleanup, PNG transparency, JPEG white background, MediaStore pending/publish behavior, and failed-write deletion.
- Independent `psd-tools` reader verifies dimensions, layer count/order, names, visibility, clipping, opacity, protection flags, the ICC profile, exact pixels in written layer bounds, and merged-preview transparency. White-matte rounding is assessed in premultiplied space.
- Direct Photoshop/other-editor interoperability, legacy Android storage permission, gallery visibility on Xiaomi, large-document memory/disk behavior and actual export destinations still require device checks.

Reproduce the independent fixture check (install `psd-tools` in a local Python environment first):

```sh
cargo run --release --example psd_fixture
python scripts/verify-psd.py
```

Vector documents preserve geometry in `.inkstone` and SVG; PNG/JPG and PSD rasterize at document resolution. PSD retains separate rasterized layers. See [VECTOR_MODE.md](VECTOR_MODE.md) for tools and limitations.

These exports are included in the 0.1.8 early-development preview.

Pending Raster transforms commit before project/image/PSD snapshots; exports use the full-resolution affine result, not the bounded live preview. See [LOCAL_PREVIEW_4.md](LOCAL_PREVIEW_4.md).

## Layer masks and PSD import

Raster layer masks export as separate grayscale PSD user-mask channels while original RGB/alpha pixels remain editable. Layered 8-bit RGB PSD import supports RAW, RLE, ZIP and ZIP prediction. See [LOCAL_WORKFLOWS.md](LOCAL_WORKFLOWS.md) for supported metadata, rejection cases, memory limits and import warnings.

## Group appearance in preview11

`.inkstone` preserves editable folder members and group blend/opacity. SVG preserves Vector folder appearance. PSD preserves the appearance of folders with a nondefault blend mode or opacity by baking each such folder into one raster layer. Full-opacity Pass Through folders retain separate member layers. Atlas placement, gallery tags/stacks and personal brush libraries remain device-local metadata.

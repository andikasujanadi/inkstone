# VectorCraft selection adaptation

`android/app/src/main/java/art/inkstone/VectorGeometry.kt` adapts the nearest-anchor selection and selected-object-first picking logic from `crates/tools/src/direct.rs` (`nearest_anchor`, `hit_anchor`) in [storytold/vectorcraft](https://github.com/storytold/vectorcraft), revision `125df6d8ecfae01d01ce369ac02e4aa03e0deed5`.

Copyright (c) 2026 ArtCraft Team and the VectorCraft contributors. Used under the MIT license, reproduced in LICENSE-MIT. Inkstone's Kotlin adaptation uses screen-space tolerances, mobile gestures, its own geometry/document model, node controls and Android renderer. It does not include VectorCraft's full application, assets or brand.

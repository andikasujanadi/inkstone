# Known issues

## Device feedback — 26 October 2026

The following issues remain in 0.1.9-preview3. They are recorded for later work, not claimed fixed in this release.

- **Recent brushes:** Recent currently shows only the active brush rather than a history of multiple brushes.
- **Fade Brush:** the user reports that the fade still does not behave correctly. The intended behavior is a fading tail with constant width and unrestricted stroke length. Framework/native regression tests do not establish that the device result meets that expectation.
- **Brush-category scroll jump:** selecting a category lower in the library can recreate its list and animate from the top back down to the category. The intended behavior is to retain the category list's scroll position and update only the brush list. Current auto-scroll remains unchanged for this release.
- **Layer groups:** editable layer folders/grouping are not implemented in Raster or Vector mode. SVG export's named layer `<g>` elements represent individual layers; they are not an in-app layer-folder feature.

## Vector color selection and fill — pending device retest

On the user's device, changing the Vector color could update the displayed color code without updating the effective color, and dragging the color onto a closed shape did not reliably recolor it. This affects 0.1.8.

The 0.1.9-preview3 build includes the local fix introduced in preview1: wheel and valid hex changes update active ink, swatches and picker consistently. An editor-event regression test confirms that a real toolbar drag-fill uses the selected hex color and Undo/Redo restores the fill. Physical retesting on the reporting device is still needed.

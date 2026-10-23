#!/usr/bin/env python3
"""Verify a Rust-generated PSD with the independent psd-tools reader."""
import json
import pathlib
import sys
from psd_tools import PSDImage
from psd_tools.constants import Resource, Tag

base = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'artifacts/psd-fixture')
source = json.loads(base.with_suffix('.json').read_text())
psd = PSDImage.open(base.with_suffix('.psd'))
assert psd.size == (source['width'], source['height'])
assert len(psd) == len(source['layers']) + 1
assert psd.image_resources.get_data(Resource.ICC_PROFILE)[36:40] == b'acsp'
assert psd[0].name == 'Background colour'
assert psd[0].visible == source['background_visible']
checked = 0
for layer, saved in zip(list(psd)[1:], source['layers']):
    assert layer.name == saved['name']
    assert layer.visible == saved['visible']
    assert layer.opacity == int(saved['opacity'] * 255 + .5)
    assert layer.clipping == saved['clipping']
    assert layer._record.flags.transparency_protected == saved['alpha_lock']
    assert int(layer.tagged_blocks.get_data(Tag.PROTECTED_SETTING)) == (7 if saved['locked'] else int(saved['alpha_lock']))
    raw = [c for n, c in saved['runs'] for _ in range(n)]
    image = layer.topil()
    x0, y0, x1, y1 = layer.bbox
    if image is None:
        assert not any(c >> 24 for c in raw)
        continue
    expected = [raw[y * source['width'] + x] for y in range(y0, y1) for x in range(x0, x1)]
    expected = [((c >> 16) & 255, (c >> 8) & 255, c & 255, c >> 24) for c in expected]
    assert list(image.convert('RGBA').getdata()) == expected, layer.name
    checked += len(expected)
reference = json.loads(base.with_name(base.name + '-merged').with_suffix('.json').read_text())
expected = [((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >> 24) & 255) for c in reference]
actual = list(psd.topil().getdata())
assert [c[3] for c in actual] == [c[3] for c in expected]
# White matte quantization is evaluated in premultiplied space, including faint edges.
error = max(abs(a[k] * a[3] - b[k] * b[3]) / 255 for a, b in zip(actual, expected) for k in range(3))
assert error <= 1.5, error
result = {'reader': 'psd-tools', 'dimensions': list(psd.size), 'layers': [l.name for l in psd],
          'exact_layer_pixels_checked': checked, 'merged_premultiplied_error': error,
          'photoshop_desktop_tested': False, 'passed': True}
base.with_name(base.name + '-verification').with_suffix('.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))

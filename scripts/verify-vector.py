#!/usr/bin/env python3
"""Check Android vector fixtures with independent SVG and PSD readers.

Run VectorTest first. Requires Pillow, resvg-py and psd-tools.
"""
import json
from pathlib import Path
from xml.etree import ElementTree as ET
from PIL import Image, ImageChops, ImageStat
from psd_tools import PSDImage
import resvg_py

base = Path('artifacts/vector-fixture')
source = json.loads(base.with_suffix('.json').read_text())
svg = base.with_suffix('.svg')
root = ET.parse(svg).getroot()
ns = '{http://www.w3.org/2000/svg}'
assert not root.findall('.//' + ns + 'image')
assert len(root.findall('.//' + ns + 'path')) == 2
assert len(root.findall('.//' + ns + 'ellipse')) == 1
render = base.with_name(base.name + '-resvg').with_suffix('.png')
render.write_bytes(resvg_py.svg_to_bytes(svg_path=str(svg)))
native = Image.open(base.with_suffix('.png')).convert('RGBA')
independent = Image.open(render).convert('RGBA')
assert native.size == independent.size == (source['width'], source['height'])
assert independent.getpixel((0, 0))[3] == 0

def premult(image):
    r, g, b, a = image.split()
    return Image.merge('RGBA', tuple(ImageChops.multiply(c, a) for c in (r, g, b)) + (a,))

means = ImageStat.Stat(ImageChops.difference(premult(native), premult(independent))).mean
# Skia and resvg use different edge coverage; compare premultiplied channels.
assert max(means) < 1.0, means
scaled = base.with_name(base.name + '-resvg-2x').with_suffix('.png')
scaled.write_bytes(resvg_py.svg_to_bytes(svg_path=str(svg), zoom=2))
assert Image.open(scaled).size == (source['width'] * 2, source['height'] * 2)
psd = PSDImage.open(base.with_suffix('.psd'))
assert psd.size == native.size and len(psd) == len(source['layers']) + 1
assert psd[0].visible == source['backgroundVisible']
for layer, expected in zip(list(psd)[1:], source['layers']):
    assert layer.name == expected['name']
    assert layer.opacity == int(expected['opacity'] * 255 + .5)
    assert layer.visible == expected['visible']
result = {'passed': True, 'svg_renderer': 'resvg-py', 'psd_reader': 'psd-tools',
          'svg_contains_embedded_images': False, 'dimensions': list(native.size),
          'svg_2x_dimensions': list(Image.open(scaled).size),
          'svg_native_premultiplied_mean_error': means,
          'psd_layers': [layer.name for layer in psd], 'photoshop_desktop_tested': False}
base.with_name(base.name + '-verification').with_suffix('.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))

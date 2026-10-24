#!/usr/bin/env python3
"""Independently inspect the native mask PSD fixture with psd-tools."""
from pathlib import Path
import json
from psd_tools import PSDImage

base = Path('artifacts/masked-roundtrip')
source = json.loads(base.with_suffix('.json').read_text())
psd = PSDImage.open(base.with_suffix('.psd'))
assert psd.size == (source['width'], source['height'])
assert len(psd) == len(source['layers'])+1
count = 0
for layer, data in zip(list(psd)[1:], source['layers']):
    assert layer.name == data['name']
    raw = [c for n,c in data['runs'] for _ in range(n)]
    image = layer.topil().convert('RGBA')
    x0,y0,x1,y1 = layer.bbox
    for y in range(y0,y1):
        for x in range(x0,x1):
            r,g,b,a = image.getpixel((x-x0,y-y0))
            assert (a<<24)|(r<<16)|(g<<8)|b == raw[y*source['width']+x]
            count += 1
    if data.get('mask'):
        assert layer.has_mask()
        mask = [c for n,c in data['mask'] for _ in range(n)]
        assert list(layer.mask.topil().convert('L').getdata()) == mask
    else:
        assert not layer.has_mask()
composite = psd.composite(force=True).convert('RGBA')
error = 0
for actual, expected in zip(composite.getdata(), source['composite']):
    a = expected>>24
    target = ((expected>>16)&255,(expected>>8)&255,expected&255,a)
    diffs = [abs(actual[i]*actual[3]/255-target[i]*a/255) for i in range(3)] + [abs(actual[3]-a)]
    error = max(error,max(diffs))
assert error <= 2, error
report = {'passed':True, 'reader':'psd-tools', 'original_layer_pixels_checked':count, 'grayscale_mask_retained':True, 'composite_max_premultiplied_error':error, 'photoshop_desktop_tested':False}
Path('artifacts/masked-psd-verification.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))

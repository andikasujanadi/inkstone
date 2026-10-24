#!/usr/bin/env python3
"""Generate independent RAW/RLE/ZIP/prediction PSD fixtures with psd-tools."""
from pathlib import Path
import json
from PIL import Image
from psd_tools import PSDImage
from psd_tools.api.layers import PixelLayer
from psd_tools.psd.layer_and_mask import MaskData, MaskFlags, ChannelData, ChannelInfo
from psd_tools.constants import Compression, ChannelID, BlendMode

base = Path(__file__).resolve().parents[1] / 'crates/ink-engine/tests/fixtures'
base.mkdir(parents=True, exist_ok=True)
for compression in (Compression.RAW, Compression.RLE, Compression.ZIP, Compression.ZIP_WITH_PREDICTION):
    psd = PSDImage.new('RGBA', (48, 32), color=(0, 0, 0, 0))
    image = Image.new('RGBA', (25, 22))
    image.putdata([(150+x*3, 40+y*5, 60, 120+x*4) for y in range(22) for x in range(25)])
    layer = PixelLayer.frompil(image, psd, 'Cropped base', top=4, left=-3, compression=compression)
    mask = bytes(0 if x<8 else 128 if x<15 else 255 for y in range(22) for x in range(25))
    layer._record.mask_data = MaskData(top=4, left=-3, bottom=26, right=22, background_color=255, flags=MaskFlags())
    data = ChannelData(compression)
    data.set_data(mask, 25, 22, 8, 1)
    layer._channels.append(data)
    layer._record.channel_info.append(ChannelInfo(id=ChannelID.USER_LAYER_MASK, length=len(data.data)+2))
    top = PixelLayer.frompil(Image.new('RGBA', (18, 12), (30, 180, 230, 180)), psd, 'Overlay', top=11, left=16, compression=compression)
    top.opacity = 170
    top.blend_mode = BlendMode.MULTIPLY
    target = base / f'layered-{int(compression)}.psd'
    psd.save(target)
    check = PSDImage.open(target)
    pixels = list(check.composite(force=True).convert('RGBA').getdata())
    target.with_suffix('.json').write_text(json.dumps({'width':48, 'height':32, 'pixels':[(a<<24)|(r<<16)|(g<<8)|b for r,g,b,a in pixels]}))
    print(target.name, 'mask:', check[0].has_mask())

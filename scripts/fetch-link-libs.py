#!/usr/bin/env python3
"""Fetch only official NDK link stubs for Rust's bundled LLD (not a full NDK)."""
import concurrent.futures
import io
import pathlib
import struct
import sys
import time
import urllib.request
import zipfile
import zlib

URL = 'https://dl.google.com/android/repository/android-ndk-r27c-darwin.zip'
DEST = pathlib.Path(__file__).resolve().parents[1] / '.toolchain' / 'android-link'

def fetch(start, end):
    cache = DEST.parent / 'ndk-range-cache' / f'{start}-{end}'
    if cache.exists():
        return cache.read_bytes()
    if end - start + 1 > 65536:
        ranges = [(s, min(s + 65535, end)) for s in range(start, end + 1, 65536)]
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            parts = list(pool.map(lambda r: fetch(*r), ranges))
        result = b''.join(parts)
        cache.parent.mkdir(parents=True, exist_ok=True)
        cache.write_bytes(result)
        return result
    for attempt in range(6):
        try:
            request = urllib.request.Request(URL, headers={'Range': f'bytes={start}-{end}', 'Accept-Encoding': 'identity'})
            with urllib.request.urlopen(request, timeout=60) as response:
                if response.status != 206:
                    raise RuntimeError('Server did not honor the range request')
                result = response.read()
                if len(result) != end - start + 1:
                    raise RuntimeError('Incomplete download')
                cache.parent.mkdir(parents=True, exist_ok=True)
                cache.write_bytes(result)
                return result
        except Exception:
            if attempt == 5:
                raise
            time.sleep(min(attempt + 1, 3))

def main():
    size = 836128272
    tail = fetch(size - 131072, size - 1)
    end = tail.rfind(b'PK\x05\x06')
    record = struct.unpack_from('<4s4H2LH', tail, end)
    directory_size, directory_start = record[5], record[6]
    central = fetch(directory_start, directory_start + directory_size - 1)
    # ZipFile accepts a standalone central directory plus adjusted EOCD.
    fake_end = struct.pack('<4s4H2LH', b'PK\x05\x06', 0, 0, record[3], record[4], directory_size, 0, 0)
    archive = zipfile.ZipFile(io.BytesIO(central + fake_end))
    wanted = []
    for item in archive.infolist():
        name = item.filename
        if name.endswith('/libunwind.a'):
            if '/aarch64/' in name:
                wanted.append((item, 'aarch64-linux-android', 'libunwind.a'))
            elif '/x86_64/' in name:
                wanted.append((item, 'x86_64-linux-android', 'libunwind.a'))
        for arch in ['aarch64-linux-android', 'x86_64-linux-android']:
            marker = f'/sysroot/usr/lib/{arch}/'
            if marker not in name or item.is_dir():
                continue
            rest = name.split(marker, 1)[1]
            if rest.startswith('26/') and '/' not in rest[3:]:
                wanted.append((item, arch, rest[3:]))
            elif rest in ['libunwind.a', 'libandroid_support.a']:
                wanted.append((item, arch, rest))
    if len(wanted) < 10:
        raise RuntimeError('Missing expected official NDK link files')
    def extract(entry):
        item, arch, name = entry
        target = DEST / arch / name
        if target.exists() and target.stat().st_size == item.file_size:
            return name
        header = fetch(item.header_offset, item.header_offset + 29)
        values = struct.unpack('<4s5H3L2H', header)
        start = item.header_offset + 30 + values[-2] + values[-1]
        data = fetch(start, start + item.compress_size - 1)
        if item.compress_type == 8:
            data = zlib.decompress(data, -15)
        if len(data) != item.file_size or zlib.crc32(data) & 0xffffffff != item.CRC:
            raise RuntimeError('NDK entry checksum mismatch')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        return f'{arch}/{name}'
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        for name in pool.map(extract, wanted):
            print(name, flush=True)
    print(f'Fetched {len(wanted)} official NDK link files to {DEST}')

if __name__ == '__main__':
    main()

#!/usr/bin/env python
"""QC every exported page PNG: dimensions, brightness, and ink coverage."""
import struct, zlib, json, sys, os

def decode(path):
    d = open(path, 'rb').read()
    pos, idat, w, h = 8, b'', 0, 0
    while pos < len(d):
        ln = struct.unpack('>I', d[pos:pos+4])[0]
        typ = d[pos+4:pos+8]
        data = d[pos+8:pos+8+ln]
        if typ == b'IHDR':
            w, h, bd, ct = struct.unpack('>IIBB', data[:10])
        if typ == b'IDAT':
            idat += data
        pos += 12 + ln
    raw = zlib.decompress(idat)
    bpp, stride = 4, w * 4
    out, prev, o = bytearray(), bytearray(stride), 0
    for _ in range(h):
        f = raw[o]; o += 1
        line = bytearray(raw[o:o+stride]); o += stride
        if f == 1:
            for i in range(bpp, stride): line[i] = (line[i] + line[i-bpp]) & 255
        elif f == 2:
            for i in range(stride): line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                line[i] = (line[i] + ((line[i-bpp] if i >= bpp else 0) + prev[i]) // 2) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i-bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i-bpp] if i >= bpp else 0
                pa, pb, pc = abs(b-c), abs(a-c), abs(a+b-2*c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out += line; prev = line
    return w, h, bytes(out)

outdir = sys.argv[1] if len(sys.argv) > 1 else 'pencli/png'
files = sorted(f for f in os.listdir(outdir) if f.endswith('.png'))
print(f"{'file':<28}{'size':<14}{'text px':<10}{'unique fills'}")
bad = []
for f in files:
    path = os.path.join(outdir, f)
    w, h, px = decode(path)
    text = sum(1 for i in range(0, len(px), 4) if px[i] > 150 and px[i+1] > 150 and px[i+2] > 150)
    uniq = {}
    for i in range(0, len(px), 4):
        k = px[i:i+4]; uniq[k] = uniq.get(k, 0) + 1
    status = 'ok'
    if text < 200:
        status = 'LOW-TEXT'; bad.append((f, status))
    if len(uniq) < 6:
        status = 'FLAT'; bad.append((f, status))
    print(f"{f:<28}{f'{w}x{h}':<14}{text:<10}{len(uniq):<8}{status if status != 'ok' else ''}")
print()
print('suspect:', bad if bad else 'none')

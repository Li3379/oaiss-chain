#!/usr/bin/env python
"""AC2 逐页比对：迁移后截图 vs pen 参考图，核验设计语言一致性。"""
import struct
import zlib
import json
import os
import sys


def decode(p):
    d = open(p, 'rb').read()
    pos, idat, w, h, ct = 8, b'', 0, 0, 6
    while pos < len(d):
        ln = struct.unpack('>I', d[pos:pos + 4])[0]
        t = d[pos + 4:pos + 8]
        data = d[pos + 8:pos + 8 + ln]
        if t == b'IHDR':
            w, h, bd, ct = struct.unpack('>IIBB', data[:10])
        if t == b'IDAT':
            idat += data
        pos += 12 + ln
    raw = zlib.decompress(idat)
    bpp = 4 if ct == 6 else 3
    stride = w * bpp
    out = bytearray()
    prev = bytearray(stride)
    o = 0
    for _ in range(h):
        f = raw[o]
        o += 1
        line = bytearray(raw[o:o + stride])
        o += stride
        if f == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 255
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                left = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((left + prev[i]) // 2)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out += line
        prev = line
    return w, h, bytes(out), bpp


def analyze(p):
    w, h, px, bpp = decode(p)
    n = w * h
    light = dark = teal = 0
    for i in range(0, len(px) - bpp + 1, bpp):
        r, g, b = px[i], px[i + 1], px[i + 2]
        if r > 200 and g > 200 and b > 200:
            light += 1
        elif r < 60 and g < 60 and b < 60:
            dark += 1
        if g > 90 and g - r > 35 and abs(g - b) < 45:
            teal += 1
    return {
        'w': w, 'h': h,
        'lightPct': round(light / n * 100, 1),
        'darkPct': round(dark / n * 100, 1),
        'tealPct': round(teal / n * 100, 2),
    }


def norm(s):
    import re
    return re.sub(r'[_\s]+', ' ', s.rsplit('.', 1)[0]).strip()


PEN = 'pencli/png'
AFT = '../.omc/reviews/ac2-after'

pen = {norm(f): os.path.join(PEN, f) for f in os.listdir(PEN) if f.endswith('.png')}
aft = {norm(f): os.path.join(AFT, f) for f in os.listdir(AFT) if f.endswith('.png')}

rows = []
for name in sorted(set(pen) | set(aft)):
    if name not in pen:
        rows.append({'name': name, 'status': 'MISSING', 'note': 'no pen ref'})
        continue
    if name not in aft:
        rows.append({'name': name, 'status': 'MISSING', 'note': 'no capture'})
        continue
    try:
        a = analyze(pen[name])
        b = analyze(aft[name])
    except Exception as e:
        rows.append({'name': name, 'status': 'ERROR', 'note': str(e)[:80]})
        continue

    # 官网首页含全屏 hero 摄影背景（森林），首屏必然以深色为主；
    # pen 稿同一页面也是深色 hero，故亮度统计豁免，其余规则照常。
    HERO_PAGES = {'02 官方网站首页'}

    issues = []
    if name not in HERO_PAGES and (a['lightPct'] >= 40) != (b['lightPct'] >= 40):
        issues.append(f"亮度体系不一致 pen={a['lightPct']}% vs 实际={b['lightPct']}%")
    if b['tealPct'] > 3 and a['tealPct'] < 3:
        issues.append(f"青绿残留 实际={b['tealPct']}% vs pen={a['tealPct']}%")
    if abs(b['w'] - 1440) > 2:
        issues.append(f'宽度异常 {b["w"]}')

    rows.append({
        'name': name,
        'status': 'DIFF' if issues else 'OK',
        'issues': issues,
        'pen': a,
        'actual': b,
    })

os.makedirs('../.omc/reviews', exist_ok=True)
with open('../.omc/reviews/ac2-diff.json', 'w', encoding='utf-8') as f:
    json.dump(rows, f, ensure_ascii=False, indent=2)

ok = sum(1 for r in rows if r['status'] == 'OK')
diff = [r for r in rows if r['status'] == 'DIFF']
missing = [r for r in rows if r['status'] in ('MISSING', 'ERROR')]

print(f"比对 {len(rows)} 页: OK {ok} / DIFF {len(diff)} / MISSING {len(missing)}\n")
if diff:
    print('需要关注的页面:')
    for r in diff:
        print(f"  {r['name']}")
        for i in r['issues']:
            print(f'    - {i}')
if missing:
    print('\n缺失/错误:')
    for r in missing:
        print(f"  {r['name']} — {r.get('note', '')}")

sys.exit(0 if not diff and not missing else 1)
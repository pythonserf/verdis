"""
Exports a "scene" (a hand-made area of a map) into a text file the mod can hide and reveal.

Usage (one or more boxes):
  py -I tools/export_scene.py <map.msav> <x0,x1,y0,y1> [<x0,x1,y0,y1> ...] <out.txt>

Inside the boxes, everything that is not natural terrain is part of the scene:
  - floors that are not in NATURAL_FLOORS (metal floors etc.)
  - all blocks except NATURAL_BLOCKS; stone walls count only as thin lines (cracks),
    big stone masses like the map edge stay
Output lines:
  F x y <real floor> <hidden floor>
  B x y <block>          (one line per building, at its center tile; or per static wall tile)
"""
import sys, zlib, struct, io, collections

NATURAL_FLOORS = {'grass', 'sand-floor', 'darksand', 'moss', 'spore-moss', 'stone', 'dirt', 'mud', 'snow',
                  'ice', 'salt', 'shale', 'basalt', 'char', 'deep-water', 'shallow-water', 'sand-water', 'darksand-water'}
NATURAL_BLOCKS = {'air', 'pine', 'spore-pine', 'snow-pine', 'white-tree', 'white-tree-dead', 'shrubs', 'spore-cluster',
                  'boulder', 'sand-boulder', 'snow-boulder', 'basalt-boulder', 'dacite-boulder', 'shale-boulder', 'pur-bush', 'redweed'}


def read_map(path):
    data = zlib.decompress(open(path, 'rb').read())
    s = io.BytesIO(data)
    rd = s.read
    i32 = lambda: struct.unpack('>i', rd(4))[0]
    i16 = lambda: struct.unpack('>h', rd(2))[0]
    u8 = lambda: rd(1)[0]
    def utf():
        n = struct.unpack('>H', rd(2))[0]
        return rd(n).decode('utf-8', 'replace')

    assert rd(4) == b'MSAV'
    version = i32()
    if version < 11:
        raise SystemExit('map version %d is too old for this tool' % version)
    # chunks: meta, patches, content, map, ...
    ln = i32(); s.seek(s.tell() + ln)          # meta
    ln = i32(); s.seek(s.tell() + ln)          # patches
    ln = i32(); start = s.tell()               # content header
    names = {}
    for _ in range(u8()):
        t = u8(); total = i16()
        names[t] = [utf() for _ in range(total)]
    s.seek(start + ln)
    blocks = names[1]

    ln = i32()                                 # map
    w = i16(); h = i16()
    floor = [None] * (w * h)
    i = 0
    while i < w * h:
        fid = i16(); i16(); c = u8()
        for j in range(i, i + c + 1):
            floor[j] = blocks[fid]
        i += c + 1

    block = ['air'] * (w * h)
    center = [False] * (w * h)
    i = 0
    while i < w * h:
        name = blocks[i16()]
        packed = u8()
        had_entity = packed & 1
        had_data = packed & 4
        if had_data:
            rd(7)
        if had_entity:
            is_center = u8()
            if is_center:
                rd(i32())
            block[i] = name
            center[i] = bool(is_center)
        elif not had_data:
            c = u8()
            for j in range(i, i + c + 1):
                block[j] = name
                center[j] = True
            i += c
        else:
            block[i] = name
            center[i] = True
        i += 1
    return w, h, floor, block, center


BIG_ROCK = 1500  # stone wall groups bigger than this are natural (map edge, cliffs)
_rock_size = {}


def is_natural_rock(block, w, h, x, y):
    """stone wall that belongs to a big connected natural mass, not a crack or debris"""
    k = y * w + x
    if block[k] != 'stone-wall':
        return False
    if k not in _rock_size:
        group, stack = [k], [k]
        seen = {k}
        while stack:
            c = stack.pop()
            cx, cy = c % w, c // w
            for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                n = ny * w + nx
                if 0 <= nx < w and 0 <= ny < h and n not in seen and block[n] == 'stone-wall':
                    seen.add(n)
                    stack.append(n)
                    group.append(n)
        for g in group:
            _rock_size[g] = len(group)
    return _rock_size[k] > BIG_ROCK


def main():
    # usage: export_scene.py <map> <x0,x1,y0,y1> [<x0,x1,y0,y1> ...] <out>
    path, out = sys.argv[1], sys.argv[-1]
    boxes = [tuple(map(int, a.split(','))) for a in sys.argv[2:-1]]
    w, h, floor, block, center = read_map(path)
    lines = []
    seen = set()
    cells = []
    for x0, x1, y0, y1 in boxes:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if (x, y) not in seen:
                    seen.add((x, y))
                    cells.append((x, y))
    for x, y in cells:
            k = y * w + x
            f = floor[k]
            if f not in NATURAL_FLOORS:
                near = collections.Counter(
                    floor[yy * w + xx]
                    for xx in range(max(0, x - 4), min(w, x + 5))
                    for yy in range(max(0, y - 4), min(h, y + 5))
                    if floor[yy * w + xx] in NATURAL_FLOORS)
                hidden = near.most_common(1)[0][0] if near else 'sand-floor'
                lines.append('F %d %d %s %s' % (x, y, f, hidden))
            b = block[k]
            if b not in NATURAL_BLOCKS and center[k] and not is_natural_rock(block, w, h, x, y):
                lines.append('B %d %d %s' % (x, y, b))
    with open(out, 'w', encoding='utf-8') as fo:
        fo.write('\n'.join(lines) + '\n')
    print('scene: %d floor tiles, %d blocks -> %s' % (
        sum(l[0] == 'F' for l in lines), sum(l[0] == 'B' for l in lines), out))


if __name__ == '__main__':
    main()

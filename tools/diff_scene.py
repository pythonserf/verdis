"""
Makes a scene file from two versions of one map: before and after the author drew something on it.

  py -I tools/diff_scene.py <before.msav> <after.msav> <out.txt>

Everything that differs becomes the scene, and the "before" state is what the scene hides it with.
Natural floors (moss etc.) are ignored, so floor touch-ups outside the drawing don't end up in the scene.
Output lines (read by MapScene):
  F x y <real floor> <hidden floor>
  B x y <real block> <hidden block>     real "-" = covered by a building placed at its center
Also prints groups of scene tiles (pieces) with their center and radius, for the cutscene.
"""
import sys
import struct
import zlib

NATURAL_FLOORS = {'grass', 'sand-floor', 'darksand', 'moss', 'spore-moss', 'stone', 'dirt', 'mud', 'snow',
                  'ice', 'salt', 'shale', 'basalt', 'char', 'deep-water', 'shallow-water', 'sand-water', 'darksand-water'}
NATURAL_BLOCKS = {'spore-wall', 'spore-pine'}   # added by hand: stay on the map, not part of the scene
PIECE_GAP = 6          # tiles closer than this belong to one piece


def i16(b, o): return struct.unpack_from('>h', b, o)[0]
def i32(b, o): return struct.unpack_from('>i', b, o)[0]


def read(path):
    data = zlib.decompress(open(path, 'rb').read())
    assert data[:4] == b'MSAV'
    o, chunks = 8, []
    while o < len(data):
        n = i32(data, o)
        chunks.append(data[o + 4:o + 4 + n])
        o += 4 + n
    content, body = chunks[2], chunks[3]
    names = {}
    o = 1
    for _ in range(content[0]):
        t, n = content[o], i16(content, o + 1)
        o += 3
        lst = []
        for _ in range(n):
            ln = struct.unpack_from('>H', content, o)[0]
            lst.append(content[o + 2:o + 2 + ln].decode('utf-8'))
            o += 2 + ln
        names[t] = lst
    blocks = names[1]

    w, h = i16(body, 0), i16(body, 2)
    o = 4
    floor = [None] * (w * h)
    i = 0
    while i < w * h:
        f, run = i16(body, o), body[o + 4]
        o += 5
        for j in range(i, i + run + 1):
            floor[j] = blocks[f]
        i += run + 1
    block, kind = ['air'] * (w * h), [''] * (w * h)   # kind: 'center', 'part' (of a building) or ''
    i = 0
    while i < w * h:
        b, packed = blocks[i16(body, o)], body[o + 2]
        o += 3
        if packed & 4:
            o += 7
        if packed & 1:
            center = body[o]
            o += 1
            if center:
                o += 4 + i32(body, o)
            block[i], kind[i] = b, 'center' if center else 'part'
            i += 1
        elif not packed & 4:
            run = body[o]
            o += 1
            for j in range(i, i + run + 1):
                block[j] = b
            i += run + 1
        else:
            block[i] = b
            i += 1
    return w, h, floor, block, kind


def main():
    before, after, out = sys.argv[1:4]
    w, h, f0, b0, k0 = read(before)
    w1, h1, f1, b1, k1 = read(after)
    assert (w, h) == (w1, h1), 'maps have different sizes'

    lines, tiles = [], []
    for i in range(w * h):
        x, y = i % w, i // w
        if f1[i] != f0[i] and f1[i] not in NATURAL_FLOORS:
            lines.append('F %d %d %s %s' % (x, y, f1[i], f0[i]))
            tiles.append((x, y))
        changed = (b1[i] != b0[i] or k1[i] != k0[i]) and not (b1[i] in NATURAL_BLOCKS and k1[i] == '')
        if changed:
            real = b1[i] if k1[i] != 'part' else '-'
            hidden = b0[i] if k0[i] == '' else 'air'
            lines.append('B %d %d %s %s' % (x, y, real, hidden))
            tiles.append((x, y))

    with open(out, 'w', encoding='utf-8') as fo:
        fo.write('\n'.join(lines) + '\n')
    print('scene: %d floor lines, %d block lines -> %s' % (
        sum(l[0] == 'F' for l in lines), sum(l[0] == 'B' for l in lines), out))

    # pieces: groups of nearby scene tiles
    left = set(tiles)
    pieces = []
    while left:
        seed = left.pop()
        group, stack = [seed], [seed]
        while stack:
            cx, cy = stack.pop()
            near = [t for t in left if abs(t[0] - cx) <= PIECE_GAP and abs(t[1] - cy) <= PIECE_GAP]
            for t in near:
                left.discard(t)
                group.append(t)
                stack.append(t)
        mx = sum(t[0] for t in group) / len(group)
        my = sum(t[1] for t in group) / len(group)
        r = max(((t[0] - mx) ** 2 + (t[1] - my) ** 2) ** 0.5 for t in group)
        pieces.append((len(group), round(mx), round(my), int(r) + 2))
    pieces.sort(reverse=True)
    print('pieces (tiles, x, y, radius):')
    for p in pieces:
        print('  %d: {%d, %d, r=%d}' % p)


if __name__ == '__main__':
    main()

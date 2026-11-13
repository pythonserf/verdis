"""
Generates the Ship Deck map (.msav, save version 13): the first sector of Verdis, a ship in orbit.

  py -I tools/gen_ship_deck.py [assets/maps/ship-deck.msav] [--seed N] [--preview deck.png]

Layout (240x120):
  - space all around (the "space" floor: nothing can stand or be built on it);
  - the ship lies along the map: flat stern with engines on the left, pointed bow on the right;
  - hull of dark metal, the deck inside is dark panels with metal floor in the middle corridor;
  - bulkheads split the ship into compartments, with doors; a wide corridor runs stern to bow,
    side cargo bays hold spilled cargo (ore piles, sand);
  - the core is in the stern compartment, enemies board through a breach in the bow
    (flying waves come from the same side, out of space).
The author is expected to decorate it further in the editor.
Format notes: see ../maze-map/gen_maze_map.py.
"""
import argparse
import math
import random
import struct
import time
import zlib

# --- tunables ---------------------------------------------------------------------------
W, H = 240, 120
STERN, BOW = 14, 228          # x of the stern edge and the bow tip
HALF = 40                     # half-width of the hull (tiles) in the middle part
BOW_START = 168               # where the hull starts to narrow towards the bow
HULL = 3                      # hull wall thickness
CORRIDOR = 11                 # half-width of the middle corridor (inner walls at center +- this)
BULKHEADS = [52, 92, 132, 172, 204]   # x of bulkheads (2 tiles thick)
DOOR_MAIN = 9                 # door width in bulkheads on the corridor
DOOR_SIDE = 6                 # door width between corridor and bays, and in bulkheads of the bays
CORE_X = 34
SPAWN_X = 214
BREACHES = 4                  # holes in the hull (space inside), only in side bays
CARGO = [('ore-copper', 4), ('ore-lead', 4), ('ore-copper', 3), ('ore-lead', 3), ('ore-coal', 2),
         ('ore-titanium', 2), ('ore-scrap', 3)]   # (ore, piles)
SAND_PILES = 3
SAVE_VERSION = 13
BUILD = 160

SPACE, WALL, CORE = 'space', 'dark-metal', 'core-shard'
PANELS = ['dark-panel-1', 'dark-panel-2', 'dark-panel-3', 'dark-panel-4', 'dark-panel-5', 'dark-panel-6']
CORRIDOR_FLOOR = ['metal-floor', 'metal-floor-2', 'metal-floor-damaged']
NAMES = ['air', SPACE, WALL, CORE, 'spawn', 'darksand', 'metal-wall-1', 'metal-wall-3'] + PANELS + CORRIDOR_FLOOR \
    + [c[0] for c in CARGO if c[0] not in ('ore-copper', 'ore-lead')] + ['ore-copper', 'ore-lead']
NAMES = list(dict.fromkeys(NAMES))
BID = {n: i for i, n in enumerate(NAMES)}

TEAM_SHARDED = 1
CORE_HEALTH = 1100.0

# Waves: boarders on foot through the bow breach and fighters out of space.
WAVES = ','.join('{' + s + '}' for s in [
    'type:flare,end:10,scaling:2,max:20',
    'type:dagger,begin:2,end:10,scaling:2,max:12',
    'type:crawler,begin:3,spacing:2,scaling:1.5,amount:3',
    'type:horizon,begin:4,spacing:2,scaling:2,max:10',
    'type:mace,begin:6,spacing:2,scaling:1.5,max:6',
    'type:flare,begin:7,scaling:1,amount:3,max:30',
    'type:zenith,begin:8,spacing:2,scaling:1,max:4',
    'type:fortress,begin:10,spacing:3,scaling:1,max:3',
])
RULES = '{waves:true,waveTimer:true,waveSpacing:2400,initialWaveSpacing:4800,spawns:[' + WAVES + ']}'


def half_width(x):
    """Hull half-width at column x (0 = outside)."""
    if x < STERN or x > BOW:
        return 0
    if x < STERN + 6:                         # rounded stern corners
        return HALF - (STERN + 6 - x)
    if x <= BOW_START:
        return HALF
    t = (x - BOW_START) / (BOW - BOW_START)   # bow: narrows with a slight curve
    return max(0, int(HALF * (1 - t ** 1.4)))


def generate(seed):
    rng = random.Random(seed)
    cy = H // 2
    floor = [[SPACE] * W for _ in range(H)]
    block = [['air'] * W for _ in range(H)]
    overlay = [['air'] * W for _ in range(H)]

    def inside(x, y):
        return abs(y - cy) < half_width(x)

    # deck and hull
    for y in range(H):
        for x in range(W):
            if not inside(x, y):
                continue
            floor[y][x] = rng.choice(PANELS)
            edge = any(not inside(x + dx, y + dy) for dx in range(-HULL, HULL + 1) for dy in range(-HULL, HULL + 1)
                       if abs(dx) + abs(dy) <= HULL)
            if edge:
                block[y][x] = WALL

    # middle corridor: metal floor, walls along it with doors into the bays
    for x in range(STERN + HULL, BOW):
        for y in range(cy - CORRIDOR + 1, cy + CORRIDOR):
            if inside(x, y) and block[y][x] == 'air':
                floor[y][x] = rng.choice(CORRIDOR_FLOOR) if rng.random() < 0.85 else rng.choice(PANELS)
        for wy in (cy - CORRIDOR, cy + CORRIDOR):
            if inside(x, wy) and block[wy][x] == 'air' and half_width(x) > CORRIDOR + 4:
                block[wy][x] = WALL

    # bulkheads with doors
    for bx in BULKHEADS:
        for x in (bx, bx + 1):
            for y in range(H):
                if inside(x, y) and block[y][x] == 'air':
                    block[y][x] = WALL
        for y in range(cy - DOOR_MAIN // 2, cy + DOOR_MAIN // 2 + 1):     # corridor door
            for x in (bx, bx + 1):
                block[y][x] = 'air'
        for side in (-1, 1):                                              # bay doors
            hw = half_width(bx)
            if hw - CORRIDOR < 10:
                continue
            dy = side * rng.randint(CORRIDOR + 4, hw - HULL - DOOR_SIDE - 1)
            for y in range(cy + dy, cy + dy + side * DOOR_SIDE, side):
                for x in (bx, bx + 1):
                    if inside(x, y):
                        block[y][x] = 'air'
    # doors from the corridor into each bay
    sections = [STERN + HULL] + BULKHEADS + [BOW]
    for a, b in zip(sections, sections[1:]):
        for wy in (cy - CORRIDOR, cy + CORRIDOR):
            if b - a < DOOR_SIDE + 6:
                continue
            x0 = rng.randint(a + 3, b - DOOR_SIDE - 3)
            for x in range(x0, x0 + DOOR_SIDE):
                if half_width(x) > CORRIDOR + 4:
                    block[wy][x] = 'air'

    # engines behind the stern: three nozzles of metal walls in space
    for ey in (cy - 24, cy, cy + 24):
        for x in range(STERN - 8, STERN):
            r = 4 + (STERN - x) // 3
            for y in range(ey - r, ey + r + 1):
                if 0 <= x:
                    block[y][x] = 'metal-wall-3' if abs(y - ey) >= r - 1 else WALL
                    floor[y][x] = 'dark-panel-4'

    # breaches: round holes in the side hull, space shows through
    for _ in range(BREACHES):
        x = rng.randint(BULKHEADS[0] + 6, BULKHEADS[-1] - 6)
        side = rng.choice((-1, 1))
        y = cy + side * (half_width(x) - HULL)
        r = rng.uniform(3.5, 6)
        for yy in range(int(y - r) - 1, int(y + r) + 2):
            for xx in range(int(x - r) - 1, int(x + r) + 2):
                if 0 <= yy < H and 0 <= xx < W and (xx - x) ** 2 + (yy - y) ** 2 <= r * r \
                        and abs(yy - cy) > CORRIDOR + 2:
                    floor[yy][xx] = SPACE
                    block[yy][xx] = 'air'

    # bow breach where enemies board: an open hole in the tip
    for y in range(H):
        for x in range(BOW - 14, W):
            if (x - BOW + 6) ** 2 + (y - cy) ** 2 <= 36 and inside(x, y):
                block[y][x] = 'air'

    # cargo: ore piles and sand in the side bays
    bays = [(x, y) for y in range(H) for x in range(BULKHEADS[0] - 30, BULKHEADS[-1])
            if inside(x, y) and block[y][x] == 'air' and floor[y][x] != SPACE and abs(y - cy) > CORRIDOR + 3]
    rng.shuffle(bays)
    piles = []
    todo = [ore for ore, n in CARGO for _ in range(n)] + ['darksand'] * SAND_PILES
    for kind in todo:
        for x, y in bays:
            if all((x - px) ** 2 + (y - py) ** 2 > 100 for px, py in piles):
                piles.append((x, y))
                r = rng.uniform(2.5, 3.8)
                for yy in range(int(y - r) - 1, int(y + r) + 2):
                    for xx in range(int(x - r) - 1, int(x + r) + 2):
                        if 0 <= yy < H and 0 <= xx < W and (xx - x) ** 2 + (yy - y) ** 2 <= r * r * rng.uniform(0.7, 1.1) \
                                and block[yy][xx] == 'air' and floor[yy][xx] != SPACE:
                            if kind == 'darksand':
                                floor[yy][xx] = 'darksand'
                            else:
                                overlay[yy][xx] = kind
                break

    # core and spawn
    for y in range(cy - 1, cy + 2):
        for x in range(CORE_X - 1, CORE_X + 2):
            block[y][x] = CORE
            overlay[y][x] = 'air'
    overlay[cy][SPAWN_X] = 'spawn'
    block[cy][SPAWN_X] = 'air'
    return floor, block, overlay, (CORE_X, cy), (SPAWN_X, cy)


# --- writing ----------------------------------------------------------------------------
class Out:
    def __init__(self): self.b = bytearray()
    def u8(self, v): self.b += struct.pack('>B', v & 0xFF)
    def i16(self, v): self.b += struct.pack('>h', v)
    def i32(self, v): self.b += struct.pack('>i', v)
    def f32(self, v): self.b += struct.pack('>f', v)

    def utf(self, s):
        data = s.encode('utf-8')
        self.b += struct.pack('>H', len(data)) + data

    def chunk(self, body):
        self.i32(len(body))
        self.b += body


def core_entity():
    """Core: Shard of team sharded, empty, as the game writes it (see gen_maze_map.py)."""
    o = Out()
    o.u8(1); o.f32(CORE_HEALTH); o.u8(0x80); o.u8(TEAM_SHARDED); o.u8(3); o.u8(1); o.u8(0x09); o.i16(0)
    o.b += bytes.fromhex('00007fc000007fc00000')
    return bytes(o.b)


def write_map(path, floor, block, overlay, core, name, description):
    cx, cy = core
    meta = {
        'saved': str(int(time.time() * 1000)), 'playtime': '0', 'build': str(BUILD),
        'mapname': name, 'name': name, 'author': '', 'description': description,
        'wave': '1', 'tick': '0.0', 'wavetime': '4800.0', 'stats': '{}', 'rules': RULES,
        'sectorPreset': '', 'locales': '{}', 'mods': '[]', 'genfilters': '[]',
        'controlGroups': 'null', 'width': str(W), 'height': str(H),
        'viewpos': '(%s,%s)' % (float(cx * 8), float(cy * 8)), 'controlledType': 'null',
        'nocores': 'false', 'playerteam': str(TEAM_SHARDED), 'hasExternalAssets': 'false',
    }
    m = Out()
    m.i16(len(meta))
    for k, v in meta.items():
        m.utf(k); m.utf(v)
    patches = Out(); patches.i32(2); patches.i32(0)
    content = Out(); content.u8(1); content.u8(1); content.i16(len(NAMES))
    for n in NAMES:
        content.utf(n)

    flat_f = [BID[floor[i // W][i % W]] for i in range(W * H)]
    flat_o = [BID[overlay[i // W][i % W]] for i in range(W * H)]
    flat_b = [BID[block[i // W][i % W]] for i in range(W * H)]
    mp = Out(); mp.i16(W); mp.i16(H)
    i = 0
    while i < W * H:
        run = 0
        while i + run + 1 < W * H and run < 255 and flat_f[i + run + 1] == flat_f[i] and flat_o[i + run + 1] == flat_o[i]:
            run += 1
        mp.i16(flat_f[i]); mp.i16(flat_o[i]); mp.u8(run)
        i += run + 1
    i = 0
    while i < W * H:
        b = flat_b[i]
        mp.i16(b)
        if b == BID[CORE]:
            mp.u8(1)
            center = (i % W, i // W) == (cx, cy)
            mp.u8(1 if center else 0)
            if center:
                mp.chunk(core_entity())
            i += 1
            continue
        mp.u8(0)
        run = 0
        while i + run + 1 < W * H and run < 255 and flat_b[i + run + 1] == b:
            run += 1
        mp.u8(run)
        i += run + 1

    ent = Out(); ent.i16(0); ent.i32(1); ent.i32(TEAM_SHARDED); ent.i32(0); ent.i32(0)
    custom = Out(); custom.i32(0)
    f = Out()
    f.b += b'MSAV'
    f.i32(SAVE_VERSION)
    for body in (m, patches, content, mp, ent):
        f.chunk(bytes(body.b))
    f.chunk(b'{}')
    f.chunk(bytes(custom.b))
    with open(path, 'wb') as fo:
        fo.write(zlib.compress(bytes(f.b), 6))


COLORS = {SPACE: (0x0b, 0x0b, 0x18), WALL: (0x3c, 0x3c, 0x46), 'metal-wall-1': (0x77, 0x77, 0x80),
          'metal-wall-3': (0x77, 0x77, 0x80), CORE: (0xff, 0xd3, 0x7f), 'spawn': (0xff, 0x40, 0x40),
          'darksand': (0x5c, 0x55, 0x4e), 'ore-copper': (0xd9, 0x9d, 0x73), 'ore-lead': (0x8c, 0x7f, 0xa9),
          'ore-coal': (0x27, 0x27, 0x27), 'ore-titanium': (0x8d, 0xa1, 0xe3), 'ore-scrap': (0x9a, 0x8a, 0x6a)}


def write_preview(path, floor, block, overlay, scale=3):
    rows = []
    for y in reversed(range(H)):
        line = bytearray()
        for x in range(W):
            b, o, f = block[y][x], overlay[y][x], floor[y][x]
            if b != 'air':
                c = COLORS.get(b, (0x55, 0x55, 0x5e))
            elif o != 'air':
                c = COLORS[o]
            elif f in COLORS:
                c = COLORS[f]
            elif f.startswith('metal'):
                c = (0x6a, 0x6a, 0x74)
            else:
                c = (0x4a, 0x4a, 0x55)
            line += bytes(c) * scale
        for _ in range(scale):
            rows.append(b'\x00' + bytes(line))

    def png_chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data))

    with open(path, 'wb') as fo:
        fo.write(b'\x89PNG\r\n\x1a\n' + png_chunk(b'IHDR', struct.pack('>IIBBBBB', W * scale, H * scale, 8, 2, 0, 0, 0))
                 + png_chunk(b'IDAT', zlib.compress(b''.join(rows), 9)) + png_chunk(b'IEND', b''))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    ap.add_argument('out', nargs='?', default='assets/maps/ship-deck.msav')
    ap.add_argument('--seed', type=int, default=None)
    ap.add_argument('--preview', metavar='PNG')
    args = ap.parse_args()
    seed = args.seed if args.seed is not None else random.randrange(1 << 30)
    floor, block, overlay, core, spawn = generate(seed)
    write_map(args.out, floor, block, overlay, core, 'Палуба корабля', 'Корабль на орбите Вердиса. Seed %d.' % seed)
    if args.preview:
        write_preview(args.preview, floor, block, overlay)
    print('seed %d: core %s, spawn %s -> %s' % (seed, core, spawn, args.out))


if __name__ == '__main__':
    main()

"""
Turns a ship outline drawn in the map editor into the Ship Deck map.

  py -I tools/ship_from_outline.py <outline.msav> <out.msav> [--preview deck.png] [--seed N]

The outline is drawn with floor only (any metal floor) on a plain floor. The script:
  - everything outside the outline becomes space;
  - the outline next to space becomes the hull: dark metal, HULL tiles thick;
  - outline lines inside the ship become bulkheads (metal walls) with doors;
  - metal-tiles-3 / metal-tiles-4 on the hull are engine nozzles: glowing hot rock behind them in space;
  - inside: dark panels, a metal corridor along the middle, cargo ore piles;
  - core near the stern, enemy spawn in the bow.
"""
import argparse
import os
import random
import sys
from collections import deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_ship_deck as gen      # noqa: E402  (writer, waves, preview)
from diff_scene import read      # noqa: E402

# --- tunables ---------------------------------------------------------------------------
HULL = 2                  # hull thickness (tiles), counted from the drawn line inwards
DOOR = 5                  # door width in bulkheads
CORRIDOR = 4              # half-width of the metal corridor along the middle of the ship
EXHAUST = 3               # length of the engine glow behind the nozzles
CORE_AT = 0.2             # core position along the ship, from the stern (0) to the bow (1)
SPAWN_FROM_BOW = 7        # spawn distance from the bow tip (tiles)
CARGO = [('ore-copper', 8), ('ore-lead', 7), ('ore-coal', 2), ('ore-titanium', 2), ('ore-scrap', 3)]
SAND_PILES = 4
PILE_RADIUS = (2.0, 3.6)

SPACE, HULL_WALL, BULKHEAD, CORE = 'space', 'dark-metal', 'metal-wall-1', 'core-shard'
NOZZLE = ('metal-tiles-3', 'metal-tiles-4')
GLOW = ['magmarock', 'hotrock', 'basalt']        # from the nozzle outwards


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    ap.add_argument('outline')
    ap.add_argument('out')
    ap.add_argument('--preview', metavar='PNG')
    ap.add_argument('--seed', type=int, default=7)
    ap.add_argument('--mirror', choices=['top', 'bottom'], help='make the ship symmetric: copy this half onto the other one')
    args = ap.parse_args()
    rnd = random.Random(args.seed)

    W, H, floor0, _, _ = read(args.outline)
    base = max(set(floor0), key=floor0.count)                 # the plain floor under the drawing
    line = [[floor0[y * W + x] != base for x in range(W)] for y in range(H)]
    nozzle = [[floor0[y * W + x] in NOZZLE for x in range(W)] for y in range(H)]

    def inb(x, y): return 0 <= x < W and 0 <= y < H

    if args.mirror:
        ys_ = [y for y in range(H) for x in range(W) if line[y][x]]
        axis2 = min(ys_) + max(ys_)          # mirrored y = axis2 - y
        for y in range(H):
            src = axis2 - y
            if (args.mirror == 'bottom') == (y * 2 > axis2) and inb(0, src):
                line[y], nozzle[y] = list(line[src]), list(nozzle[src])

    # close diagonal steps, so a 4-way fill cannot slip between two corner-touching tiles
    for y in range(H - 1):
        for x in range(W - 1):
            if line[y][x] and line[y + 1][x + 1] and not line[y][x + 1] and not line[y + 1][x]:
                line[y][x + 1] = True
            if line[y][x + 1] and line[y + 1][x] and not line[y][x] and not line[y + 1][x + 1]:
                line[y][x] = True

    # outside = reachable from the map edge without crossing the drawing
    out = [[False] * W for _ in range(H)]
    q = deque((x, y) for y in range(H) for x in range(W)
              if (x in (0, W - 1) or y in (0, H - 1)) and not line[y][x])
    for x, y in q:
        out[y][x] = True
    while q:
        x, y = q.popleft()
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if inb(nx, ny) and not out[ny][nx] and not line[ny][nx]:
                out[ny][nx] = True
                q.append((nx, ny))

    def near(grid, x, y, r=1):
        return any(inb(x + dx, y + dy) and grid[y + dy][x + dx]
                   for dx in range(-r, r + 1) for dy in range(-r, r + 1))

    # hull: drawn tiles touching space, then grown inwards
    hull = [[line[y][x] and near(out, x, y) for x in range(W)] for y in range(H)]
    for _ in range(HULL - 1):
        grow = [[not out[y][x] and not hull[y][x] and near(hull, x, y) for x in range(W)] for y in range(H)]
        hull = [[hull[y][x] or grow[y][x] for x in range(W)] for y in range(H)]
    inside = [[not out[y][x] and not hull[y][x] for x in range(W)] for y in range(H)]
    wall = [[inside[y][x] and line[y][x] for x in range(W)] for y in range(H)]   # bulkheads

    xs = [x for y in range(H) for x in range(W) if not out[y][x]]
    stern, bow = min(xs), max(xs)
    ys = [y for y in range(H) for x in range(W) if not out[y][x]]
    mid = (min(ys) + max(ys)) // 2

    # doors: every straight bulkhead run gets a gap in its middle
    def runs(cells):
        cells = sorted(cells)
        start = prev = None
        for c in cells:
            if prev is not None and c == prev + 1:
                prev = c
                continue
            if start is not None:
                yield start, prev
            start = prev = c
        if start is not None:
            yield start, prev

    for x in range(W):
        for a, b in runs([y for y in range(H) if wall[y][x] and not wall[y][x - 1] and not wall[y][min(x + 1, W - 1)]]):
            if b - a + 1 >= DOOR + 4:
                c = (a + b) // 2
                for y in range(c - DOOR // 2, c - DOOR // 2 + DOOR):
                    wall[y][x] = False
    for y in range(H):
        for a, b in runs([x for x in range(W) if wall[y][x] and not wall[y - 1][x] and not wall[min(y + 1, H - 1)][x]]):
            if b - a + 1 >= DOOR + 4:
                c = (a + b) // 2
                for x in range(c - DOOR // 2, c - DOOR // 2 + DOOR):
                    wall[y][x] = False

    floor = [[SPACE] * W for _ in range(H)]
    block = [['air'] * W for _ in range(H)]
    overlay = [['air'] * W for _ in range(H)]
    panels = gen.PANELS
    for y in range(H):
        for x in range(W):
            if out[y][x]:
                continue
            if hull[y][x]:
                block[y][x] = HULL_WALL
                floor[y][x] = 'dark-panel-1'
            elif wall[y][x]:
                block[y][x] = BULKHEAD
                floor[y][x] = 'metal-floor'
            elif abs(y - mid) <= CORRIDOR:
                floor[y][x] = 'metal-floor-damaged' if rnd.random() < 0.06 else ('metal-floor-2' if abs(y - mid) == CORRIDOR else 'metal-floor')
            else:
                floor[y][x] = rnd.choice(panels)
            # doors left by the bulkheads get a metal floor
            if inside[y][x] and line[y][x] and not wall[y][x]:
                floor[y][x] = 'metal-floor-2'

    # engines: hot glow in space behind the nozzle tiles
    for y in range(H):
        for x in range(W):
            if not nozzle[y][x]:
                continue
            for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                for k in range(1, EXHAUST + 1):
                    nx, ny = x + dx * k, y + dy * k
                    if not inb(nx, ny) or not out[ny][nx]:
                        break
                    floor[ny][nx] = GLOW[min(k - 1, len(GLOW) - 1)]

    def free(x, y): return inb(x, y) and inside[y][x] and block[y][x] == 'air'

    # core in the first open hall from the stern, spawn in the bow
    def first_free(xs_, y0):
        for x in xs_:
            for dy in range(0, 30):
                for y in (y0 + dy, y0 - dy):
                    if all(free(x + i, y + j) for i in (-2, -1, 0, 1, 2) for j in (-2, -1, 0, 1, 2)):
                        return x, y
        raise SystemExit('no room for the core / spawn')

    core = first_free(range(stern + int((bow - stern) * CORE_AT), bow), mid)
    spawn = first_free(range(bow - SPAWN_FROM_BOW, stern, -1), mid)
    cx, cy = core
    for i in (-1, 0, 1):
        for j in (-1, 0, 1):
            block[cy + j][cx + i] = CORE
    overlay[spawn[1]][spawn[0]] = 'spawn'          # the spawn is an overlay floor, not a block

    # cargo piles: ore on the panels, away from the core, the spawn and the corridor
    def pile(kind, overlay_ore):
        for _ in range(400):
            x, y = rnd.randrange(stern, bow), rnd.randrange(H)
            if not free(x, y) or abs(y - mid) <= CORRIDOR + 2:
                continue
            if (x - cx) ** 2 + (y - cy) ** 2 < 8 ** 2 or (x - spawn[0]) ** 2 + (y - spawn[1]) ** 2 < 10 ** 2:
                continue
            r = rnd.uniform(*PILE_RADIUS)
            for py in range(int(y - r) - 1, int(y + r) + 2):
                for px in range(int(x - r) - 1, int(x + r) + 2):
                    d = ((px - x) ** 2 + (py - y) ** 2) ** 0.5
                    if free(px, py) and d <= r - rnd.random() * 0.8 and block[py][px] == 'air':
                        if overlay_ore:
                            overlay[py][px] = kind
                        else:
                            floor[py][px] = kind
            return

    for kind, n in CARGO:
        for _ in range(n):
            pile(kind, True)
    for _ in range(SAND_PILES):
        pile('darksand', False)

    names = ['air', SPACE, HULL_WALL, BULKHEAD, CORE, 'spawn', 'darksand'] + panels \
        + ['metal-floor', 'metal-floor-2', 'metal-floor-damaged'] + GLOW + [c[0] for c in CARGO]
    gen.NAMES = list(dict.fromkeys(names))
    gen.BID = {n: i for i, n in enumerate(gen.NAMES)}
    gen.W, gen.H = W, H
    gen.COLORS.update({'magmarock': (0xff, 0x7a, 0x3c), 'hotrock': (0xc8, 0x5a, 0x32), 'basalt': (0x40, 0x3a, 0x3a),
                       BULKHEAD: (0x9a, 0x9a, 0xa6)})
    gen.write_map(args.out, floor, block, overlay, core, 'Палуба корабля', 'Корабль на орбите Вердиса.')
    if args.preview:
        gen.write_preview(args.preview, floor, block, overlay)
    print('%dx%d, core %s, spawn %s -> %s' % (W, H, core, spawn, args.out))


if __name__ == '__main__':
    main()

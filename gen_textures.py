# -*- coding: utf-8 -*-
"""生成 structure_core 与 mini_structure_block 的 16x16 像素贴图（无第三方依赖）。"""
import zlib, struct, os

OUT_DIR = r"E:\workplace\xmod-forge-26.2-65.1.0\src\main\resources\assets\petrification\textures"


def write_png(path, pixels):
    """pixels: 16x16 list of (r,g,b,a)"""
    raw = b""
    for row in pixels:
        raw += b"\x00" + b"".join(struct.pack("4B", *px) for px in row)
    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)
    print("wrote", path)


def clamp(v):
    return max(0, min(255, int(v)))


# ---------- 结构核心：深色边框 + 发光绿色菱形核心 + 高光 ----------
def make_structure_core():
    px = [[(20, 24, 30, 255)] * 16 for _ in range(16)]
    # 菱形核心（中心 7.5,7.5）
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d <= 6.5:
                if d > 5.5:            # 外圈边框：亮青绿描边
                    px[y][x] = (46, 204, 113, 255)
                elif d > 3.5:          # 中圈
                    px[y][x] = (26, 188, 156, 255)
                else:                   # 内核：亮白绿高光
                    px[y][x] = (174, 242, 192, 255)
    # 高光点
    px[5][5] = (240, 255, 245, 255)
    px[6][5] = (222, 255, 233, 255)
    # 四角小点缀（能量点）
    for cx, cy in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        px[cy][cx] = (72, 201, 176, 255)
    return px


# ---------- 微缩建筑方块：木框底 + 微缩小房子（墙+红棕屋顶+门窗） ----------
def make_mini_structure_block():
    # 底色：木板质感（横条纹）
    px = []
    for y in range(16):
        row = []
        base = 158 if (y % 4 != 3) else 120
        for x in range(16):
            r, g, b = clamp(base - 18), clamp(base * 0.72), clamp(base * 0.42)
            row.append((r, g, b, 255))
        px.append(row)
    # 外边框加深
    for i in range(16):
        for x, y in [(i, 0), (i, 15), (0, i), (15, i)]:
            px[y][x] = (94, 62, 33, 255)
    # 小房子：屋顶（三角形，红棕色）
    roof = (168, 74, 50, 255)
    roof_dk = (130, 52, 36, 255)
    for row in range(3, 8):
        half = row - 2          # 屋顶从窄到宽
        for x in range(8 - half, 8 + half + 1):
            px[row][x] = roof
    for x in range(3, 13):      # 屋檐底边
        px[7][x] = roof_dk
    # 墙体（浅奶油色）
    wall = (222, 205, 170, 255)
    wall_dk = (190, 172, 138, 255)
    for y in range(8, 13):
        for x in range(4, 12):
            px[y][x] = wall
    for x in range(4, 12):      # 墙底边
        px[13][x] = wall_dk
    # 门（深棕）
    for y in range(9, 13):
        for x in range(7, 9):
            px[y][x] = (110, 76, 42, 255)
    # 窗（青色玻璃）
    px[9][5] = (94, 180, 200, 255)
    px[9][6] = (94, 180, 200, 255)
    px[10][5] = (74, 156, 178, 255)
    px[10][6] = (74, 156, 178, 255)
    # 烟囱
    px[4][11] = (120, 120, 126, 255)
    px[3][11] = (120, 120, 126, 255)
    px[2][11] = (90, 90, 96, 255)
    return px


os.makedirs(os.path.join(OUT_DIR, "item"), exist_ok=True)
os.makedirs(os.path.join(OUT_DIR, "block"), exist_ok=True)
write_png(os.path.join(OUT_DIR, "item", "structure_core.png"), make_structure_core())
write_png(os.path.join(OUT_DIR, "block", "mini_structure_block.png"), make_mini_structure_block())
print("done")

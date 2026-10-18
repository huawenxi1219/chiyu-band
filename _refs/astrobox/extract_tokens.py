# -*- coding: utf-8 -*-
"""从 AstroBox libapp_lib.so 提取前端设计 token（配色/圆角/动画/毛玻璃）"""
import re, os

so = r"C:\Users\黄\Doubao\chats\2026-10-17\new-chat-1\band-word-app\_refs\astrobox\lib\arm64-v8a\libapp_lib.so"
data = open(so, "rb").read()
print("so 大小:", len(data))

# 提取可打印 ASCII/UTF-8 串
def extract_strings(buf, min_len=6):
    out = []
    cur = bytearray()
    for b in buf:
        if 32 <= b < 127 or b >= 0xC0:  # 可打印 ASCII 或 UTF-8 首字节
            cur.append(b)
        else:
            if len(cur) >= min_len:
                try:
                    out.append(cur.decode("utf-8"))
                except Exception:
                    pass
            cur = bytearray()
    if len(cur) >= min_len:
        try:
            out.append(cur.decode("utf-8"))
        except Exception:
            pass
    return out

print("提取字符串中...")
strs = extract_strings(data, min_len=8)
print("字符串总数:", len(strs))

# 1. 找 CSS 变量/设计 token
css_vars = set()
colors = set()
anim = set()
radius = set()
blur = set()
fonts = set()

color_re = re.compile(r"#[0-9a-fA-F]{3,8}\b|rgba?\([^)]*\)|hsla?\([^)]*\)")
for s in strs:
    if "--" in s and ("px" in s or "rgb" in s or "#" in s or "s" in s):
        for m in re.finditer(r"--[\w-]+\s*:\s*[^;}{]+", s):
            css_vars.add(m.group(0).strip())
    for m in color_re.finditer(s):
        colors.add(m.group(0))
    if "animation" in s or "transition" in s or "ease" in s or "@keyframes" in s:
        for m in re.finditer(r"(?:animation|transition)\s*:\s*[^;}{]+", s):
            anim.add(m.group(0).strip())
    for m in re.finditer(r"border-radius\s*:\s*[^;}{]+", s):
        radius.add(m.group(0).strip())
    for m in re.finditer(r"(?:backdrop-filter|filter)\s*:\s*[^;}{]+", s):
        blur.add(m.group(0).strip())
    for m in re.finditer(r"font-family\s*:\s*[^;}{]+", s):
        fonts.add(m.group(0).strip())

print("\n===== CSS 变量 =====")
for v in sorted(css_vars)[:40]:
    print(" ", v)
print("\n===== 颜色 =====")
for c in sorted(colors)[:30]:
    print(" ", c)
print("\n===== 动画/过渡 =====")
for a in sorted(anim)[:25]:
    print(" ", a)
print("\n===== 圆角 =====")
for r in sorted(radius)[:15]:
    print(" ", r)
print("\n===== 滤镜/毛玻璃 =====")
for b in sorted(blur)[:15]:
    print(" ", b)
print("\n===== 字体 =====")
for f in sorted(fonts)[:10]:
    print(" ", f)

# 2. 找 @keyframes 定义
kf = [s for s in strs if "keyframes" in s or "to{" in s or "from{" in s]
print("\n===== 关键帧片段 =====")
for k in kf[:10]:
    print(" ", k[:300])

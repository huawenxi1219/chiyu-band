# -*- coding: utf-8 -*-
"""扫描 .so 中的 gzip 流，解压提取 AstroBox 前端资源（html/css/js）"""
import zlib, os, re

so = r"C:\Users\黄\Doubao\chats\2026-10-17\new-chat-1\band-word-app\_refs\astrobox\lib\arm64-v8a\libapp_lib.so"
data = open(so, "rb").read()

out_dir = r"C:\Users\黄\Doubao\chats\2026-10-17\new-chat-1\band-word-app\_refs\astrobox\extracted"
os.makedirs(out_dir, exist_ok=True)

hits = []
idx = 0
while True:
    i = data.find(b"\x1f\x8b\x08", idx)
    if i < 0:
        break
    hits.append(i)
    idx = i + 1

print("gzip 头数量:", len(hits))
saved = []
for n, off in enumerate(hits):
    try:
        d = zlib.decompress(data[off:off + 8 * 1024 * 1024], 16 + zlib.MAX_WBITS)
    except Exception:
        continue
    if len(d) < 100:
        continue
    head = d[:200]
    is_html = b"<!doctype" in head.lower() or b"<html" in head.lower()
    is_css = b"{" in head and (b"font-family" in d[:5000] or b"--" in d[:5000])
    is_js = b"function" in d[:2000] or b"=>" in d[:2000] or b"const" in d[:2000]
    is_wasm = d[:4] in (b"\x00asm", b"\x00asm")
    name = None
    if is_html:
        name = f"index_{n}.html"
    elif is_css:
        name = f"style_{n}.css"
    elif is_js:
        name = f"app_{n}.js"
    elif is_wasm:
        name = f"module_{n}.wasm"
    if name:
        path = os.path.join(out_dir, name)
        with open(path, "wb") as f:
            f.write(d)
        saved.append((name, len(d), off))
        print(f"  保存 {name}: {len(d)} 字节 @ offset {off}")
    else:
        # 可能是 tauri 资产（含 html/css/js 任意）
        if b"index.html" in d[:10000] or b"astrobox" in d[:10000]:
            name = f"asset_{n}.bin"
            with open(os.path.join(out_dir, name), "wb") as f:
                f.write(d)
            saved.append((name, len(d), off))
            print(f"  保存 {name}(tauri资产): {len(d)} 字节 @ offset {off}")

print("\n共保存:", len(saved), "个资源")

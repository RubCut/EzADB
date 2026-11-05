#!/usr/bin/env python3
"""Generate Android string resources from tools/strings_data.txt.

Format: key|en|ru|uk|de|es|fr|zh
Missing trailing translations fall back to English.
"""
from pathlib import Path
import html

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "tools" / "strings_data.txt"
RES = ROOT / "app" / "src" / "main" / "res"
LOCALES = [("", "en"), ("-ru", "ru"), ("-uk", "uk"), ("-de", "de"), ("-es", "es"), ("-fr", "fr"), ("-zh-rCN", "zh")]
COLS = ["en", "ru", "uk", "de", "es", "fr", "zh"]

def esc(text: str) -> str:
    text = html.escape(text, quote=False)
    text = text.replace("'", "\\'")
    return text

rows = {}
for line in DATA.read_text(encoding="utf-8").splitlines():
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    parts = line.split("|")
    key = parts[0].strip()
    vals = [p.strip() for p in parts[1:]]
    if len(vals) < 1:
        raise SystemExit(f"missing English value for {key}")
    vals += [vals[0]] * (len(COLS) - len(vals))
    rows[key] = dict(zip(COLS, vals[:len(COLS)]))

for suffix, lang in LOCALES:
    folder = RES / f"values{suffix}"
    folder.mkdir(parents=True, exist_ok=True)
    out = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for key, vals in rows.items():
        out.append(f'    <string name="{key}">{esc(vals[lang])}</string>')
    out.append('</resources>')
    (folder / "strings.xml").write_text("\n".join(out) + "\n", encoding="utf-8")
print(f"generated {len(rows)} strings for {len(LOCALES)} locales")

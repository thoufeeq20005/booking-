"""Replaces individual photos from fetch_venue_photos.py that turned out unsuitable (brand signs, weak matches).

    python backend/seed/redo_venue_photos.py

Never reuses a photo already listed in photos/CREDITS.json.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fetch_venue_photos as fv  # noqa: E402

PHOTOS = fv.PHOTOS
credits_path = PHOTOS / "CREDITS.json"
existing = json.loads(credits_path.read_text(encoding="utf-8"))
taken_sources = {c["source"] for c in existing.values()}
fv.BLOCKED = fv.BLOCKED + ("depot", "yoshinoya", "concert", "cartoon", "caricature", "festival")

orig_search = fv.search
fv.search = lambda q: [h for h in orig_search(q) if (h.get("foreign_landing_url") or h["url"]) not in taken_sources]

fv.SOURCES = "stocksnap,rawpixel"
REDO = {
    "movies/boundary-line-boys.jpg": (["stadium", "sports field", "floodlights stadium", "baseball bat ball"], (600, 900)),
}
for rel, (queries, size) in REDO.items():
    fv.save(queries, rel, size)

existing.update(fv.credits)
credits_path.write_text(json.dumps(existing, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
print(len(fv.credits), "photos replaced")

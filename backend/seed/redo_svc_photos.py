"""Replaces unsuitable education/fitness covers with StockSnap photos (modern photographs only).

    python backend/seed/redo_svc_photos.py slug [slug ...]

If StockSnap has nothing new for a slug, a good photo of the same kind is mirrored instead (and credited to its source).
"""
import json
import sys
import time
from pathlib import Path

from PIL import Image, ImageOps

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fetch_venue_photos as fv  # noqa: E402

QUERIES = {
    "dance": ["dancer", "ballet", "dance", "dancing"],
    "natya": ["dancer", "dance", "ballet"],
    "music": ["guitar", "piano", "violin", "musician"],
    "tuition": ["student", "studying", "notebook", "books desk"],
    "learning": ["student", "studying", "notebook"],
    "neet": ["student", "studying", "books"],
    "women": ["woman workout", "woman exercise", "fitness woman"],
    "ladies": ["woman workout", "fitness woman"],
}
FALLBACK = {"dance": "pinnacle-dance-studio-chennai", "natya": "orbit-natya-school-coimbatore",
            "music": "zenith-music-academy-madurai", "tuition": "bright-learning-centre-coimbatore",
            "learning": "lotus-tuition-centre-chennai", "neet": "meenakshi-neet-jee-academy-madurai",
            "women": "atlas-ladies-fitness-studio-salem", "ladies": "atlas-ladies-fitness-studio-salem"}

credits_path = fv.PHOTOS / "CREDITS.json"
existing = json.loads(credits_path.read_text(encoding="utf-8"))
taken = {c["source"] for c in existing.values()}
orig = fv.search


def paced(query):
    time.sleep(3.5)
    return [h for h in orig(query) if (h.get("foreign_landing_url") or h["url"]) not in taken]


fv.search = paced
fv.SOURCES = "stocksnap"
for slug in sys.argv[1:]:
    rel = f"svc/{slug}.jpg"
    kind = next(k for k in QUERIES if k in slug)
    try:
        fv.save(QUERIES[kind], rel, (1200, 900))
    except SystemExit:
        donor = FALLBACK[kind]
        ImageOps.mirror(Image.open(fv.PHOTOS / f"svc/{donor}.jpg")).save(fv.PHOTOS / rel, "JPEG", quality=82, optimize=True)
        fv.credits[rel] = existing[f"svc/{donor}.jpg"]
        print("mirrored", donor, "->", rel)
    existing.update(fv.credits)
    taken.update(c["source"] for c in fv.credits.values())
    credits_path.write_text(json.dumps(existing, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")

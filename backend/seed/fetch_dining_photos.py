"""CC0 photos for the Dining home (mood tiles) and the premium demo restaurants. Uses fetch_venue_photos helpers.

    python backend/seed/fetch_dining_photos.py
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fetch_venue_photos as fv  # noqa: E402

DINING_PUBLIC = fv.SEED.parent.parent / "frontend" / "public" / "dining"
DINING_PUBLIC.mkdir(exist_ok=True)
existing = json.loads((fv.PHOTOS / "CREDITS.json").read_text(encoding="utf-8"))
taken = {c["source"] for c in existing.values()}
orig = fv.search
fv.search = lambda q: [h for h in orig(q) if (h.get("foreign_landing_url") or h["url"]) not in taken]
fv.BLOCKED = fv.BLOCKED + ("beer", "whisky", "wine bottle", "brand")

MOODS = {
    "cafes.jpg": ["coffee croissant", "cafe breakfast"],
    "family.jpg": ["indian thali", "family dinner table"],
    "veg.jpg": ["salad bowl", "vegetable salad"],
    "premium.jpg": ["fine dining plate", "gourmet dish"],
    "drinks.jpg": ["mocktail", "cocktail glass"],
    "offers.jpg": ["restaurant table setting", "dinner table candles"],
    "hero.jpg": ["indian food spread", "food table top view"],
}
for name, qs in MOODS.items():
    fv.save(qs, name, (900, 900) if name != "hero.jpg" else (1600, 700), DINING_PUBLIC)
for i, qs in [(12, ["bar restaurant interior", "lounge bar"]), (13, ["rooftop restaurant", "restaurant terrace night"])]:
    fv.save(qs, f"venues/dining-{i}.jpg", (1200, 750))

fixed = {k.replace("frontend/public/home/", "frontend/public/dining/") if k.split("/")[-1] in MOODS else k: v
         for k, v in fv.credits.items()}
existing.update(fixed)
(fv.PHOTOS / "CREDITS.json").write_text(json.dumps(existing, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
print(len(fixed), "photos saved")

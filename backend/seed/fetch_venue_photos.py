"""Downloads CC0 (public domain) photos from Openverse for the movies & dining demo and records their credits.

    python backend/seed/fetch_venue_photos.py

Writes photos/movies/*.jpg (2:3 posters), photos/venues/*.jpg (16:10 covers), frontend/public/home/movies.jpg and
dining.jpg, and adds every source to photos/CREDITS.json. Only CC0 images are used. Run once; the results are
committed so generating the SQL needs no network.
"""
import io
import json
import time
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image, ImageOps

SEED = Path(__file__).resolve().parent
PHOTOS = SEED / "photos"
HOME = SEED.parent.parent / "frontend" / "public" / "home"
UA = {"User-Agent": "BookEasy-demo-seed/1.0 (photo fetch for demo data)"}
# Skip photos that are likely to show a real cinema chain, brand or logo.
BLOCKED = ("pvr", "inox", "cinepolis", "amc", "imax", "regal", "logo", "poster", "sign", "starbucks", "mcdonald", "kfc")

# slot -> (search terms tried in order, output path, (width, height))
MOVIES = {
    "signal-thirteen": ["railway signal night", "train night station"],
    "pearl-city-nights": ["harbour boats sunset", "fishing boats night"],
    "rockfort-diaries": ["friends laughing tea", "college friends"],
    "the-monsoon-ferry": ["backwaters boat kerala", "ferry river rain"],
    "starling-and-the-kite": ["kite sky", "colorful kite"],
    "midnight-toll-plaza": ["highway night lights", "toll road night"],
    "boundary-line-boys": ["cricket match", "cricket bat ball"],
    "orbit-cafe": ["neon cafe night", "coffee shop neon"],
    "kaveri-kingdom": ["ancient fort india", "temple ruins sunset"],
    "the-lighthouse-choir": ["lighthouse sea", "lighthouse sunset"],
    "zero-hour-delta": ["city night rain", "bank vault"],
}
CINEMAS = ["cinema seats", "movie theater seats", "cinema hall", "movie theater interior", "auditorium seats red",
           "cinema projector", "popcorn cinema", "theater seats"]
DINING = {
    "south": ["dosa", "idli sambar"],
    "chettinad": ["chicken curry india", "indian curry"],
    "biryani": ["biryani", "biryani rice"],
    "seafood": ["fish fry", "prawn curry"],
    "cafe": ["cafe coffee", "coffee shop interior"],
}
RESTAURANT_KINDS = ["south", "chettinad", "biryani", "cafe", "chettinad", "cafe", "cafe", "chettinad", "seafood", "south",
                    "biryani", "cafe"]  # same order as RESTAURANTS in generate_movies_dining.py

used, credits = set(), {}


SOURCES = None  # e.g. "stocksnap,rawpixel" to search curated stock sites only


def search(q):
    params = {"q": q, "license": "cc0", "page_size": 20, "mature": "false"}
    if SOURCES:
        params["source"] = SOURCES
    url = "https://api.openverse.org/v1/images/?" + urllib.parse.urlencode(params)
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        return json.load(r)["results"]


def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        data = r.read(25 * 1024 * 1024)
    return Image.open(io.BytesIO(data))


def save(queries, rel, size, out_root=PHOTOS):
    target = out_root / rel
    for q in queries:
        for hit in search(q):
            title = (hit.get("title") or "").lower()
            w, h = hit.get("width") or 0, hit.get("height") or 0
            if hit["id"] in used or any(b in title for b in BLOCKED) or min(w, h) < 700:
                continue
            try:
                im = ImageOps.exif_transpose(fetch(hit["url"])).convert("RGB")
            except Exception as e:  # noqa: BLE001 - skip unreachable or odd files
                print("  skip", hit["url"][:80], e)
                continue
            if min(im.size) < 600:
                continue
            ImageOps.fit(im, size, Image.LANCZOS, centering=(0.5, 0.45)).save(target, "JPEG", quality=82, optimize=True)
            used.add(hit["id"])
            credits[rel if out_root == PHOTOS else f"frontend/public/home/{rel}"] = {
                "source": hit.get("foreign_landing_url") or hit["url"], "creator": hit.get("creator") or "unknown",
                "license": hit.get("license", "cc0"), "via": f"Openverse / {hit.get('source')}"}
            print("ok", rel, "<-", q, "|", hit.get("source"))
            time.sleep(0.5)
            return
        time.sleep(0.5)
    raise SystemExit(f"no photo found for {rel}")


def main():
    (PHOTOS / "movies").mkdir(exist_ok=True)
    (PHOTOS / "venues").mkdir(exist_ok=True)
    for slug, qs in MOVIES.items():
        save(qs, f"movies/{slug}.jpg", (600, 900))
    for i, q in enumerate(CINEMAS):
        save([q, "cinema seats", "movie theater"], f"venues/cinema-{i}.jpg", (1200, 750))
    for i, kind in enumerate(RESTAURANT_KINDS):
        save(DINING[kind] + ["restaurant food india"], f"venues/dining-{i}.jpg", (1200, 750))
    save(["cinema seats red", "movie theater"], "movies.jpg", (1200, 900), HOME)
    save(["indian food thali", "restaurant table food"], "dining.jpg", (1200, 900), HOME)

    path = PHOTOS / "CREDITS.json"
    all_credits = json.loads(path.read_text(encoding="utf-8"))
    all_credits.update(credits)
    path.write_text(json.dumps(all_credits, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(len(credits), "photos saved, credits updated")


if __name__ == "__main__":
    main()

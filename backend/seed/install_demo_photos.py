"""Copies the demo photos into BookEasy file storage so the rows in demo-tamilnadu.sql have their bytes.

    python backend/seed/install_demo_photos.py [storage-root]

storage-root defaults to backend/data/uploads (the dev FILE_STORAGE_PATH when the backend runs from backend/).
Only keys under public/demo/ are written; earlier demo photos there are replaced.
"""
import json
import shutil
import sys
from pathlib import Path

seed = Path(__file__).resolve().parent
root = Path(sys.argv[1]) if len(sys.argv) > 1 else seed.parent / "data" / "uploads"
plan = json.loads((seed / "demo-photo-files.json").read_text(encoding="utf-8"))

demo_dir = root / "public" / "demo"
if demo_dir.exists():
    shutil.rmtree(demo_dir)
demo_dir.mkdir(parents=True)
for key, source in plan.items():
    if not key.startswith("public/demo/"):
        raise SystemExit(f"unexpected storage key {key}")
    shutil.copyfile(seed / source, root / key)
print(f"copied {len(plan)} photos into {demo_dir}")

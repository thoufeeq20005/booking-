"""Generates demo-dining-more.sql: more cafés, premium restaurants and family restaurants (mostly Coimbatore).

    python backend/seed/generate_more_dining.py --fetch-photos   # once: downloads CC0 cover photos (Openverse)
    python backend/seed/generate_more_dining.py                  # rewrites demo-dining-more.sql

Load it after demo-movies-dining.sql, then run install_demo_photos.py. Every restaurant is fictional (real localities
only). Owners sign in as demo.resto.*@example.com / Demo@1234. Safe to load again: earlier rows are removed first,
together with bookings made at these restaurants. Never run it in production.
"""
import hashlib
import json
import random
import sys
import uuid
from pathlib import Path

SEED = Path(__file__).resolve().parent
sys.path.insert(0, str(SEED))
import generate_movies_dining as base  # noqa: E402  (also regenerates its own SQL, deterministically)

random.seed(2611)
q, slugify, to_utc, phone = base.q, base.slugify, base.to_utc, base.phone
RESTAURANT_SUB, CAFE_SUB = base.RESTAURANT_SUB, base.CAFE_SUB

MENUS = dict(base.MENUS)
MENUS["pizzeria"] = [
    ("Wood-fired pizza", [("Margherita", 320, True, True, "San Marzano tomato, mozzarella, basil"),
                          ("Paneer tikka pizza", 380, True, False, None), ("Pepper chicken pizza", 420, False, True, None)]),
    ("Pasta", [("Arrabbiata penne", 340, True, False, None), ("Pesto spaghetti", 360, True, False, None)]),
    ("Coffee & desserts", [("Cappuccino", 170, True, False, None), ("Tiramisu", 260, True, True, None)])]
MENUS["mughlai"] = [
    ("Starters", [("Murgh malai tikka", 420, False, True, None), ("Tandoori paneer", 360, True, False, None),
                  ("Galouti kebab", 480, False, False, "Melt-in-the-mouth minced mutton")]),
    ("Mains", [("Dal makhani", 320, True, True, "Slow-cooked overnight"), ("Butter chicken", 440, False, True, None),
               ("Nalli nihari", 620, False, False, None)]),
    ("Breads & rice", [("Garlic naan", 90, True, False, None), ("Lucknowi biryani", 480, False, False, None)]),
    ("Desserts", [("Shahi tukda", 220, True, False, None)])]
MENUS["desserts"] = [
    ("Cakes", [("Belgian chocolate truffle", 240, True, True, None), ("Biscoff cheesecake", 280, True, False, None)]),
    ("Coffee", [("Cold brew", 190, True, True, None), ("Hazelnut latte", 210, True, False, None)]),
    ("Bites", [("Croissant sandwich", 220, True, False, None), ("Chicken puff", 80, False, False, None)])]

LOC = {  # locality: (city, pincode, lat, lng)
    "Saibaba Colony": ("Coimbatore", "641011", 11.0260, 76.9419), "R.S. Puram": ("Coimbatore", "641002", 11.0081, 76.9505),
    "Peelamedu": ("Coimbatore", "641004", 11.0262, 77.0220), "Race Course": ("Coimbatore", "641018", 11.0001, 76.9750),
    "Gandhipuram": ("Coimbatore", "641012", 11.0183, 76.9681), "Singanallur": ("Coimbatore", "641005", 10.9989, 77.0329),
    "Saravanampatti": ("Coimbatore", "641035", 11.0790, 77.0007), "Adyar": ("Chennai", "600020", 13.0012, 80.2565),
    "Nungambakkam": ("Chennai", "600034", 13.0569, 80.2425), "Anna Nagar": ("Chennai", "600040", 13.0850, 80.2101),
}
HOURS = {"cafe": ("08:00", "23:00", None, None), "restaurant": ("12:00", "23:00", "15:30", "19:00"),
         "lounge": ("19:00", "23:59", None, None), "family": ("11:00", "22:30", None, None)}

# (name, sub, tier, locality, cuisines, menu, cost for two, veg only, capacity, features, hours, photo searches, offers)
RESTOS = [
    # Cafés
    ("Brindhavan Brew House", CAFE_SUB, "CAFE", "Saibaba Colony", "Café, Coffee, Desserts", "cafe", 700, False, 30,
     "Wi-Fi, Outdoor seating, Pet friendly", "cafe", ["cafe interior warm lights", "coffee shop interior"], [("Flat 10% off", 10, "ALL_DAY")]),
    ("Kovai Bean & Bake", CAFE_SUB, "CAFE", "R.S. Puram", "Café, Bakery, Sandwiches", "desserts", 600, True, 24,
     "Takeaway, Wi-Fi", "cafe", ["latte art", "coffee cup cafe"], [("Flat 15% off", 15, "ALL_DAY")]),
    ("Little Hills Pizzeria", CAFE_SUB, "CAFE", "Peelamedu", "Café, Pizza, Italian, Coffee", "pizzeria", 1200, False, 36,
     "Wood-fired oven, Family seating, Air conditioned", "cafe", ["pizza", "wood fired pizza"], [("Flat 20% off", 20, "ALL_DAY")]),
    ("Cocoa Lane Dessert Café", CAFE_SUB, "CAFE", "Race Course", "Desserts, Beverages, Café", "desserts", 900, True, 20,
     "Air conditioned, Board games", "cafe", ["chocolate cake", "dessert cafe"], [("Flat 10% off", 10, "ALL_DAY")]),
    ("Pebble Street Café", CAFE_SUB, "CAFE", "Adyar", "Café, Continental, Coffee", "cafe", 1000, False, 28,
     "Outdoor seating, Wi-Fi, Live acoustic on Sundays", "cafe", ["cafe table plants", "cozy cafe"], [("Flat 15% off", 15, "ALL_DAY")]),
    # Premium restaurants
    ("The Saffron Courtyard", RESTAURANT_SUB, "PREMIUM", "Race Course", "North Indian, Mughlai, Kebabs", "mughlai", 2400, False, 70,
     "Fine dining, Private dining, Valet parking, Live ghazals", "restaurant", ["restaurant interior elegant", "fine dining restaurant"],
     [("Flat 25% off", 25, "ALL_DAY"), ("Dinner special: 35% off", 35, "DINNER")]),
    ("Ember & Oak Grill House", RESTAURANT_SUB, "PREMIUM", "Peelamedu", "Steaks, Grills, Continental", "bar", 2000, False, 60,
     "Bar seating, Live music, Air conditioned", "restaurant", ["grilled steak", "restaurant bar interior wood"],
     [("Flat 20% off", 20, "ALL_DAY"), ("Lunch: 30% off", 30, "LUNCH")]),
    ("Skyline Sixteen Rooftop", RESTAURANT_SUB, "PREMIUM", "Gandhipuram", "Continental, Asian, Mocktails", "bar", 2600, False, 80,
     "Rooftop, City view, DJ on weekends, Valet parking", "lounge", ["rooftop restaurant night", "outdoor restaurant lights"],
     [("Flat 30% off", 30, "ALL_DAY")]),
    ("The Brass Lantern Lounge", RESTAURANT_SUB, "PREMIUM", "Nungambakkam", "Asian, Continental, Small plates", "bar", 2800, False, 60,
     "Lounge seating, Live music, Valet parking", "lounge", ["lounge interior", "restaurant lamps interior"],
     [("Flat 25% off", 25, "ALL_DAY")]),
    # Family restaurants
    ("Annapoorani Family Restaurant", RESTAURANT_SUB, "CAFE", "Gandhipuram", "South Indian, North Indian, Thali", "south", 700, True, 90,
     "Family seating, Baby high chair, Air conditioned", "family", ["south indian meals", "indian thali"],
     [("Flat 10% off", 10, "ALL_DAY"), ("Lunch thali: 20% off", 20, "LUNCH")]),
    ("Kongu Biryani Darbar", RESTAURANT_SUB, "CAFE", "Singanallur", "Biryani, Kongunadu, Chettinad", "biryani", 800, False, 80,
     "Family seating, Takeaway, Parking", "family", ["biryani", "chicken biryani"], [("Flat 15% off", 15, "ALL_DAY")]),
    ("Highway Tandoor Dhaba", RESTAURANT_SUB, "CAFE", "Saravanampatti", "North Indian, Tandoor, Punjabi", "mughlai", 900, False, 100,
     "Outdoor seating, Family seating, Parking", "family", ["tandoori chicken", "indian curry bowl"], [("Flat 15% off", 15, "ALL_DAY")]),
    ("Grill & Gather BBQ", RESTAURANT_SUB, "CAFE", "R.S. Puram", "BBQ, Kebabs, North Indian", "mughlai", 1300, False, 90,
     "Live grill on table, Family seating, Birthday packages", "family", ["barbecue skewers", "grilled kebab"],
     [("Flat 20% off", 20, "ALL_DAY")]),
    ("Thalaivaa Meals Hall", RESTAURANT_SUB, "CAFE", "Anna Nagar", "South Indian, Chettinad, Meals", "chettinad", 650, False, 70,
     "Family seating, Takeaway", "family", ["banana leaf meal", "indian food plate"], [("Flat 10% off", 10, "ALL_DAY")]),
]
DINERS = ["Kavya Raman", "Surya Narayanan", "Fathima Begum", "Ganesh Kumar", "Nivetha Senthil", "Rahul Menon"]


def photo_rel(name):
    return f"venues/resto-{slugify(name)}.jpg"


def fetch_photos():
    import fetch_venue_photos as fv
    credits_path = fv.PHOTOS / "CREDITS.json"
    existing = json.loads(credits_path.read_text(encoding="utf-8"))
    taken = {c["source"] for c in existing.values()}
    orig = fv.search
    fv.search = lambda query: [h for h in orig(query) if (h.get("foreign_landing_url") or h["url"]) not in taken]
    fv.BLOCKED = fv.BLOCKED + ("beer", "whisky", "wine", "brand", "menu board")
    for r in RESTOS:
        rel = photo_rel(r[0])
        if (fv.PHOTOS / rel).exists():
            continue
        fv.SOURCES = "stocksnap,rawpixel"
        try:
            fv.save(r[11], rel, (1200, 900))
        except SystemExit:
            fv.SOURCES = None
            fv.save(r[11], rel, (1200, 900))
        existing.update(fv.credits)  # saved after every photo, so an interrupted run loses nothing
        credits_path.write_text(json.dumps(existing, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


def generate():
    # Only restaurants whose cover photo has been fetched are included; run --fetch-photos to add the rest.
    missing = [r[0] for r in RESTOS if not (SEED / "photos" / photo_rel(r[0])).exists()]
    if missing:
        print("skipping (no photo yet):", ", ".join(missing))
    restos = [r for r in RESTOS if r[0] not in missing]
    out = []
    w = out.append

    def insert(table, cols, rows):
        if rows:
            w(f"INSERT INTO {table} ({', '.join(cols)}) VALUES")
            w(",\n".join("  (" + ", ".join(r) + ")" for r in rows) + ";")
            w("")

    w("-- BookEasy demo: more cafés, premium and family restaurants. Load after demo-movies-dining.sql.")
    w("-- GENERATED by backend/seed/generate_more_dining.py - edit the script, not this file. Fictional; DO NOT run in production.")
    w("SET NAMES utf8mb4;")
    w("START TRANSACTION;")
    w("CREATE TEMPORARY TABLE demo_resto_users AS SELECT id FROM users WHERE email LIKE 'demo.resto.%@example.com';")
    w("CREATE TEMPORARY TABLE demo_resto_orgs AS SELECT o.id FROM organizations o JOIN organization_owners oo ON oo.organization_id = o.id "
      "WHERE oo.user_id IN (SELECT id FROM demo_resto_users);")
    w("CREATE TEMPORARY TABLE demo_appts AS SELECT id FROM appointments WHERE organization_id IN (SELECT id FROM demo_resto_orgs) "
      "OR customer_id IN (SELECT id FROM demo_resto_users);")
    for t in ["refunds", "commission_records", "payments", "reviews"]:
        w(f"DELETE FROM {t} WHERE appointment_id IN (SELECT id FROM demo_appts);")
    w("DELETE FROM appointments WHERE id IN (SELECT id FROM demo_appts);")
    w("DELETE FROM organizations WHERE id IN (SELECT id FROM demo_resto_orgs);")
    w("DELETE FROM users WHERE id IN (SELECT id FROM demo_resto_users);")
    w("DELETE FROM uploaded_files WHERE storage_key LIKE 'public/demo/resto-%';")
    w("DROP TEMPORARY TABLE demo_appts;")
    w("DROP TEMPORARY TABLE demo_resto_orgs;")
    w("DROP TEMPORARY TABLE demo_resto_users;")
    for var, table in [("u", "users"), ("o", "organizations"), ("b", "organization_branches"), ("s", "services"),
                       ("f", "uploaded_files"), ("a", "appointments")]:
        w(f"SET @{var} = (SELECT COALESCE(MAX(id), 0) FROM {table});")
    w("")

    users, roles, orgs, owners, verifs, branches, hours, services, profiles, menu, offers = ([] for _ in range(11))
    files, plan, past, reviews = [], {}, [], []
    for n, (name, sub, tier, locality, cuisines, menu_key, cost, veg, cap, feats, hours_key, _qs, offer_list) in enumerate(restos, 1):
        city, pin, lat, lng = LOC[locality]
        lat += random.uniform(-0.004, 0.004)
        lng += random.uniform(-0.004, 0.004)
        first = name.split()[0]
        users.append([f"@u + {n}", q(f"{random.choice(base.FIRST)} {random.choice(base.LAST)}"),
                      q(f"demo.resto.{slugify(first)}{n}@example.com"), q(phone()), q(base.PASSWORD_HASH), "'ACTIVE'",
                      "UTC_TIMESTAMP(6)", "UTC_TIMESTAMP(6)", "'en'", "UTC_TIMESTAMP(6)", "UTC_TIMESTAMP(6)"])
        roles += [[f"@u + {n}", "1"], [f"@u + {n}", "2"]]
        slug = f"{slugify(name)}-{slugify(city)}"
        days_ago = random.randint(20, 300)
        org_phone = phone()
        desc = f"{cuisines} in {locality}, {city}. Book a table online and get up to {max(o[1] for o in offer_list)}% off your bill."
        orgs.append([f"@o + {n}", str(sub), q(name), q(slug), q(desc), q(f"demo.contact.{slug[:40]}@example.com"), q(org_phone),
                     "'APPROVED'", "'INSTANT'", f"UTC_TIMESTAMP(6) - INTERVAL {days_ago - 1} DAY",
                     f"UTC_TIMESTAMP(6) - INTERVAL {days_ago - 3} DAY", f"@u + {n}", f"UTC_TIMESTAMP(6) - INTERVAL {days_ago} DAY",
                     "UTC_TIMESTAMP(6)"])
        owners.append([f"@o + {n}", f"@u + {n}", "'PRIMARY'", "UTC_TIMESTAMP(6)"])
        verifs.append([f"@o + {n}", "'SUBMITTED'", "'DRAFT'", "'PENDING'", "NULL", f"UTC_TIMESTAMP(6) - INTERVAL {days_ago - 1} DAY"])
        verifs.append([f"@o + {n}", "'APPROVED'", "'PENDING'", "'APPROVED'", "'Documents verified (demo data)'",
                       f"UTC_TIMESTAMP(6) - INTERVAL {days_ago - 3} DAY"])
        street = random.choice(["Main Road", "Avinashi Road", "DB Road", "Cross Cut Road", "Trichy Road", "100 Feet Road"])
        branches.append([f"@b + {n}", f"@o + {n}", "'Main branch'", q(f"No. {random.randint(1, 250)}, {street}"), "NULL", q(locality),
                         q(city), "'Tamil Nadu'", q(pin), f"{lat:.6f}", f"{lng:.6f}", q(org_phone), "UTC_TIMESTAMP(6)", "UTC_TIMESTAMP(6)"])
        o_, c_, bs, be = HOURS[hours_key]
        for d in range(1, 8):
            hours.append([f"@b + {n}", str(d), q(o_), q(c_), q(bs), q(be)])
        services.append([f"@s + {n}", f"@o + {n}", "'Table booking'", "90", "0.00", "FALSE", "30", "0", "FALSE",
                         "UTC_TIMESTAMP(6)", "UTC_TIMESTAMP(6)"])
        profiles.append([f"@o + {n}", "'DINING'", f"@s + {n}", q(cuisines), f"{cost:.2f}", q(veg), str(cap), "NULL", q(feats), q(tier)])
        sort = 0
        for section, items in MENUS[menu_key]:
            for dish, price, is_veg, best, about in items:
                if veg and not is_veg:
                    continue
                sort += 1
                menu.append([f"@o + {n}", q(section), q(dish), q(about), f"{price:.2f}", q(is_veg), q(best), str(sort)])
        for title, percent, meal in offer_list:
            offers.append([f"@o + {n}", q(title), str(percent), q(meal), "UTC_TIMESTAMP(6)"])
        # Cover photo
        rel = photo_rel(name)
        data = (SEED / "photos" / rel).read_bytes()
        key = f"public/demo/resto-{slugify(name)}.jpg"
        files.append([f"@f + {n}", q(str(uuid.uuid5(uuid.NAMESPACE_URL, "bookeasy-demo:" + key))), q(key), q(Path(rel).name),
                      "'image/jpeg'", str(len(data)), q(hashlib.sha256(data).hexdigest()), "'PUBLIC'", "'ORGANIZATION_COVER'",
                      f"@u + {n}", "UTC_TIMESTAMP(6)"])
        plan[key] = "photos/" + rel
        # Past diners and reviews
        quality = random.choice([5, 4, 4, 5, 4])
        for _ in range(random.randint(4, 9)):
            guests = random.choice([2, 2, 3, 4, 5])
            days = random.randint(3, 90)
            start = f"TIMESTAMP(UTC_DATE() - INTERVAL {days} DAY, '{to_utc(random.choice(['13:00', '13:30', '20:00', '20:30', '21:00']))}:00')"
            cover_amt = (25 if tier == "PREMIUM" else 10) * guests
            diner = len(restos) + 1 + random.randrange(len(DINERS))
            ref = "BR" + "".join(random.choice(base.REF_ALPHABET) for _ in range(8))
            past.append([f"@a + {len(past) + 1}", q(ref), f"@u + {diner}", f"@o + {n}", f"@b + {n}", f"@s + {n}", start,
                         f"{start} + INTERVAL 90 MINUTE", f"{start} + INTERVAL 90 MINUTE", "'COMPLETED'", q(f"Table for {guests}"),
                         f"{cover_amt:.2f}", "0.00", f"{cover_amt:.2f}", str(guests), f"{start} - INTERVAL 5 MINUTE",
                         f"{start} + INTERVAL 90 MINUTE", f"{start} - INTERVAL 2 DAY", f"{start} + INTERVAL 90 MINUTE"])
            rating = max(3, min(5, quality + random.choice([0, 0, 0, -1, 1])))
            reviews.append([f"@a + {len(past)}", f"@o + {n}", f"@u + {diner}", str(rating), q(random.choice(base.DINING_REVIEWS[rating])),
                            f"{start} + INTERVAL 1 DAY", f"{start} + INTERVAL 1 DAY"])
    for i, name in enumerate(DINERS):
        n = len(restos) + 1 + i
        users.append([f"@u + {n}", q(name), q(f"demo.resto.diner{i + 1}@example.com"), q(phone()), q(base.PASSWORD_HASH), "'ACTIVE'",
                      "UTC_TIMESTAMP(6)", "UTC_TIMESTAMP(6)", "'en'", "UTC_TIMESTAMP(6)", "UTC_TIMESTAMP(6)"])
        roles.append([f"@u + {n}", "1"])

    insert("users", ["id", "full_name", "email", "phone_e164", "password_hash", "status", "email_verified_at", "phone_verified_at",
                     "preferred_language", "created_at", "updated_at"], users)
    insert("user_roles", ["user_id", "role_id"], roles)
    insert("organizations", ["id", "subcategory_id", "name", "slug", "description", "contact_email", "contact_phone",
                             "verification_status", "confirmation_mode", "submitted_at", "approved_at", "created_by", "created_at",
                             "updated_at"], orgs)
    insert("organization_owners", ["organization_id", "user_id", "role", "created_at"], owners)
    insert("organization_verifications", ["organization_id", "action", "from_status", "to_status", "reason", "created_at"], verifs)
    insert("organization_branches", ["id", "organization_id", "name", "address_line1", "address_line2", "locality", "city", "state",
                                     "pincode", "latitude", "longitude", "phone", "created_at", "updated_at"], branches)
    insert("working_hours", ["branch_id", "day_of_week", "opens_at", "closes_at", "break_start", "break_end"], hours)
    insert("services", ["id", "organization_id", "name", "duration_minutes", "price", "requires_staff", "slot_interval_minutes",
                        "min_notice_minutes", "is_active", "created_at", "updated_at"], services)
    insert("venue_profiles", ["organization_id", "kind", "booking_service_id", "cuisines", "cost_for_two", "veg_only",
                              "capacity_per_slot", "offer_text", "features", "dining_tier"], profiles)
    insert("menu_items", ["organization_id", "section", "name", "description", "price", "is_veg", "is_bestseller", "sort_order"], menu)
    insert("dining_offers", ["organization_id", "title", "percent", "meal", "created_at"], offers)
    insert("uploaded_files", ["id", "public_id", "storage_key", "original_name", "content_type", "size_bytes", "sha256", "visibility",
                              "purpose", "owner_user_id", "created_at"], files)
    for n in range(1, len(restos) + 1):
        w(f"UPDATE organizations SET cover_file_id = @f + {n} WHERE id = @o + {n};")
    insert("appointments", ["id", "reference", "customer_id", "organization_id", "branch_id", "service_id", "starts_at", "ends_at",
                            "blocked_until", "status", "service_name", "service_price", "platform_fee", "total_amount", "party_size",
                            "checked_in_at", "completed_at", "created_at", "updated_at"], past)
    insert("reviews", ["appointment_id", "organization_id", "customer_id", "rating", "comment", "created_at", "updated_at"], reviews)
    w("UPDATE organizations o JOIN (SELECT organization_id, ROUND(AVG(rating), 2) avg_r, COUNT(*) n FROM reviews "
      "WHERE status = 'VISIBLE' GROUP BY organization_id) r ON r.organization_id = o.id "
      "SET o.rating_avg = r.avg_r, o.review_count = r.n WHERE o.id > @o;")
    w("COMMIT;")
    w(f"-- {len(restos)} restaurants, {len(menu)} dishes, {len(offers)} offers, {len(reviews)} reviews")
    (SEED / "demo-dining-more.sql").write_text("\n".join(out) + "\n", encoding="utf-8")
    (SEED / "demo-resto-photo-files.json").write_text(json.dumps(plan, indent=1), encoding="utf-8")
    print(out[-1])


if __name__ == "__main__":
    if "--fetch-photos" in sys.argv:
        fetch_photos()
    generate()

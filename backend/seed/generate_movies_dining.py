"""Generates demo-movies-dining.sql: sample cinemas (with showtimes) and restaurants/cafés (with menus).

Load it after demo-tamilnadu.sql. Every movie, theatre, restaurant and person is fictional; localities and
coordinates are real so nearby search works. Output is deterministic (fixed random seed).

    python backend/seed/generate_movies_dining.py     # rewrites backend/seed/demo-movies-dining.sql

Showtimes are created for the 21 days after the file is loaded. Load it again to get fresh ones (this also
removes bookings made at the demo cinemas and restaurants). Demo owners sign in as demo.venue.*@example.com
with the password Demo@1234. Never run it in production.
"""
import random
from pathlib import Path

random.seed(1407)

PASSWORD_HASH = "$2a$12$.hqQmqoWu/3C6Q8L8h8cDuJcOinee8qIFn/Yu2qkPufDIGFSzx62K"  # Demo@1234
SHOW_DAYS = 21
CINEMA_SUB, RESTAURANT_SUB, CAFE_SUB = 12, 13, 14

# Invented titles; posters are drawn from the two colours, so no artwork is copied.
MOVIES = [
    ("Signal Thirteen", "Tamil, Telugu", "UA", "Thriller, Mystery", 142, "#0f766e", "#022c22",
     "A railway signalman in Tiruchirappalli notices a train that isn't on any timetable.", "Arjun Selvan, Meera Nandakumar"),
    ("Pearl City Nights", "Tamil", "U", "Romance, Drama", 136, "#be185d", "#4c0519",
     "Two pearl divers' children meet again at the Thoothukudi harbour festival.", "Kavin Raj, Ishaani Prabhu"),
    ("Rockfort Diaries", "Tamil, English", "UA", "Comedy, Drama", 128, "#c2410c", "#431407",
     "Five college friends reunite to save the tea shop where they used to bunk classes.", "Surya Prakash, Diya Menon"),
    ("The Monsoon Ferry", "Tamil, Malayalam", "U", "Family, Drama", 122, "#1d4ed8", "#172554",
     "A grandmother's last ferry ride across the backwaters brings a scattered family together.", "Lakshmi Iyer, Vetri Maran"),
    ("Starling and the Kite", "Tamil, Telugu, Hindi", "U", "Animation, Adventure", 104, "#7c3aed", "#2e1065",
     "A young starling teams up with a runaway kite to find its way home before Pongal.", "Voice cast: Nila Arun, Hari Kumar"),
    ("Midnight Toll Plaza", "Tamil, Telugu", "A", "Action, Crime", 158, "#b91c1c", "#1c1917",
     "One night at a highway toll plaza, a booth operator becomes the only witness to a heist.", "Dhruv Shankar, Anaya Pillai"),
    ("Boundary Line Boys", "Tamil", "U", "Sports, Drama", 145, "#15803d", "#052e16",
     "A village cricket team from Salem gets one shot at the state tournament.", "Mithran Das, Keerthi Ravi"),
    ("Orbit Café", "English, Tamil, Hindi", "UA", "Sci-fi, Comedy", 118, "#0369a1", "#082f49",
     "A Chennai café accidentally starts serving coffee to visitors from the future.", "Rohan Varma, Sana Iqbal"),
]

LAYOUTS = {
    "large": [*({"row": r, "tier": "CLASSIC", "seats": 18, "gapsAfter": [4, 14]} for r in "ABCDEFG"),
              *({"row": r, "tier": "PRIME", "seats": 18, "gapsAfter": [4, 14]} for r in "HIJK"),
              {"row": "L", "tier": "RECLINER", "seats": 10, "gapsAfter": [5]}],
    "medium": [*({"row": r, "tier": "CLASSIC", "seats": 14, "gapsAfter": [3, 11]} for r in "ABCDEF"),
               *({"row": r, "tier": "PRIME", "seats": 14, "gapsAfter": [3, 11]} for r in "GHI")],
    "small": [*({"row": r, "tier": "CLASSIC", "seats": 12, "gapsAfter": [6]} for r in "ABCD"),
              *({"row": r, "tier": "PRIME", "seats": 12, "gapsAfter": [6]} for r in "EFG")],
}
PRICES = {"CLASSIC": 160, "PRIME": 220, "RECLINER": 420}
SHOW_TIMES = ["10:30", "14:00", "18:00", "21:30"]  # India time; every film fits in a 3h15 gap

# (name, city, locality, lat, lng, [(screen name, layout, format)])
CINEMAS = [
    ("Marina Screens", "Chennai", "T. Nagar", 13.0412, 80.2335, [("Screen 1", "large", "2D"), ("Screen 2", "medium", "3D"), ("Screen 3", "small", "2D")]),
    ("Adyar Talkies", "Chennai", "Adyar", 13.0020, 80.2570, [("Audi 1", "medium", "2D"), ("Audi 2", "small", "2D")]),
    ("Anna Nagar Picture House", "Chennai", "Anna Nagar", 13.0862, 80.2110, [("Gold", "large", "IMAX"), ("Silver", "medium", "2D")]),
    ("Kovai Cinemas", "Coimbatore", "Gandhipuram", 11.0190, 76.9690, [("Screen 1", "large", "2D"), ("Screen 2", "medium", "2D")]),
    ("Vaigai Theatre", "Madurai", "Tallakulam", 9.9380, 78.1345, [("Main", "large", "2D"), ("Mini", "small", "2D")]),
    ("Rockfort Cineplex", "Tiruchirappalli", "Thillai Nagar", 10.8275, 78.6845, [("Screen 1", "medium", "2D"), ("Screen 2", "small", "3D")]),
    ("Steel City Screens", "Salem", "Fairlands", 11.6745, 78.1355, [("Screen 1", "medium", "2D"), ("Screen 2", "small", "2D")]),
    ("Pearl Talkies", "Thoothukudi", "Millerpuram", 8.7835, 78.1255, [("Screen 1", "medium", "2D")]),
]

MENUS = {
    "south": [("Tiffin", [("Ghee roast dosa", 120, True, True, "Crisp dosa roasted in ghee, with three chutneys"),
                          ("Mini idli sambar", 90, True, False, "Fourteen button idlis soaked in sambar"),
                          ("Pongal vada combo", 110, True, False, None)]),
              ("Meals", [("South Indian thali", 220, True, True, "Rice, sambar, rasam, two poriyal, kootu, curd, payasam"),
                         ("Chettinad chicken meals", 320, False, False, "Thali with Chettinad chicken gravy"),
                         ("Curd rice", 110, True, False, None)]),
              ("Sweets & drinks", [("Filter coffee", 40, True, True, None), ("Rava kesari", 70, True, False, None)])],
    "chettinad": [("Starters", [("Pepper chicken", 280, False, True, "Dry roast with black pepper and curry leaves"),
                                ("Kola urundai", 240, False, False, "Spiced minced mutton balls"),
                                ("Mushroom 65", 210, True, False, None)]),
                  ("Mains", [("Chettinad mutton curry", 380, False, True, None),
                             ("Nattu kozhi kuzhambu", 340, False, False, "Country chicken in a tamarind-pepper gravy"),
                             ("Kara kuzhambu", 190, True, False, None),
                             ("Kothu parotta", 220, False, False, None)]),
                  ("Desserts", [("Elaneer payasam", 140, True, False, "Tender coconut payasam")])],
    "biryani": [("Biryani", [("Seeraga samba chicken biryani", 290, False, True, "Dindigul style, with brinjal curry and raita"),
                             ("Mutton biryani", 380, False, True, None),
                             ("Vegetable biryani", 210, True, False, None)]),
                ("Sides", [("Chicken 65", 220, False, False, None), ("Bread halwa", 90, True, False, None)])],
    "seafood": [("From the sea", [("Vanjiram fish fry", 420, False, True, "Seer fish, masala-coated and pan fried"),
                                  ("Nethili fry", 260, False, False, "Crisp anchovies"),
                                  ("Prawn thokku", 360, False, True, None),
                                  ("Crab rasam", 180, False, False, None)]),
                ("Meals", [("Fish curry meals", 260, False, False, None), ("Veg meals", 170, True, False, None)])],
    "cafe": [("Coffee", [("Filter coffee", 50, True, True, None), ("Cappuccino", 160, True, False, None),
                         ("Cold coffee", 180, True, False, None)]),
             ("Bites", [("Paneer tikka sandwich", 190, True, True, None), ("Masala fries", 140, True, False, None),
                        ("Chicken puff", 70, False, False, None)]),
             ("Desserts", [("Jigarthanda", 120, True, True, "Madurai's chilled milk, almond gum and ice cream"),
                           ("Brownie with ice cream", 180, True, False, None)])],
}

# (name, sub, city, locality, lat, lng, cuisines, menu, cost for two, veg only, capacity, offer, features, hours)
RESTAURANTS = [
    ("Mylapore Tiffin Room", RESTAURANT_SUB, "Chennai", "Mylapore", 13.0372, 80.2680, "South Indian, Tiffin", "south", 350, True, 40,
     "Free filter coffee with any tiffin before 9 am", "Family seating, Air conditioned, Takeaway", "early"),
    ("Anna Nagar Chettinad Kitchen", RESTAURANT_SUB, "Chennai", "Anna Nagar", 13.0845, 80.2095, "Chettinad, South Indian", "chettinad", 900, False, 50,
     "15% off on weekday lunch", "Air conditioned, Parking, Private dining", "restaurant"),
    ("Velachery Biryani Point", RESTAURANT_SUB, "Chennai", "Velachery", 12.9820, 80.2185, "Biryani, Mughlai", "biryani", 700, False, 36,
     None, "Air conditioned, Home delivery", "restaurant"),
    ("Besant Brew Café", CAFE_SUB, "Chennai", "Adyar", 13.0005, 80.2560, "Café, Desserts, Coffee", "cafe", 500, False, 24,
     "Buy one get one on cold coffee after 4 pm", "Wi-Fi, Outdoor seating, Pet friendly", "cafe"),
    ("Kovai Kongunadu Mess", RESTAURANT_SUB, "Coimbatore", "R.S. Puram", 11.0085, 76.9510, "Kongunadu, South Indian", "chettinad", 600, False, 40,
     None, "Family seating, Parking", "restaurant"),
    ("Race Course Coffee House", CAFE_SUB, "Coimbatore", "Race Course", 11.0005, 76.9755, "Café, Bakery", "cafe", 450, True, 20,
     None, "Wi-Fi, Outdoor seating", "cafe"),
    ("Koodal Jigarthanda Café", CAFE_SUB, "Madurai", "Simmakkal", 9.9262, 78.1202, "Café, Desserts", "cafe", 300, True, 24,
     "Second jigarthanda half price", "Takeaway, Family seating", "cafe"),
    ("Vaigai Parotta Hall", RESTAURANT_SUB, "Madurai", "Goripalayam", 9.9334, 78.1265, "Madurai, Chettinad", "chettinad", 500, False, 60,
     None, "Late night, Family seating", "restaurant"),
    ("Pearl City Seafood Kitchen", RESTAURANT_SUB, "Thoothukudi", "Bryant Nagar", 8.7905, 78.1305, "Seafood, South Indian", "seafood", 800, False, 36,
     "Catch of the day at 10% off", "Air conditioned, Sea-facing terrace", "restaurant"),
    ("Rockfort Tiffin Centre", RESTAURANT_SUB, "Tiruchirappalli", "Cantonment", 10.8005, 78.6905, "South Indian, Tiffin", "south", 300, True, 30,
     None, "Takeaway, Family seating", "early"),
    ("Mango City Biryani House", RESTAURANT_SUB, "Salem", "Hasthampatti", 11.6705, 78.1555, "Biryani, Salem style", "biryani", 600, False, 40,
     None, "Air conditioned, Parking", "restaurant"),
    ("Shevaroy Hills Café", CAFE_SUB, "Salem", "Alagapuram", 11.6755, 78.1425, "Café, Coffee", "cafe", 400, True, 20,
     None, "Wi-Fi, Board games", "cafe"),
]
HOURS = {
    "restaurant": ("11:30", "23:00", "15:30", "18:30"),
    "early": ("06:30", "22:00", None, None),
    "cafe": ("08:00", "22:30", None, None),
    "cinema": ("09:30", "23:59", None, None),
}
PINS = {"T. Nagar": "600017", "Adyar": "600020", "Anna Nagar": "600040", "Mylapore": "600004", "Velachery": "600042",
        "Gandhipuram": "641012", "R.S. Puram": "641002", "Race Course": "641018", "Tallakulam": "625002",
        "Simmakkal": "625001", "Goripalayam": "625002", "Thillai Nagar": "620018", "Cantonment": "620001",
        "Fairlands": "636016", "Hasthampatti": "636007", "Alagapuram": "636004", "Millerpuram": "628008",
        "Bryant Nagar": "628008"}
FIRST = ["Arun", "Priya", "Karthik", "Lakshmi", "Senthil", "Divya", "Vignesh", "Meena", "Prakash", "Kavitha", "Balaji",
         "Revathi", "Ganesh", "Nandhini", "Hari", "Saranya", "Mohan", "Janani", "Ramesh", "Uma"]
LAST = ["Kumar", "Rajan", "Krishnan", "Natarajan", "Sundaram", "Pandian", "Iyer", "Pillai", "Selvaraj", "Durai"]


def q(s):
    if s is None:
        return "NULL"
    if isinstance(s, bool):
        return "TRUE" if s else "FALSE"
    if isinstance(s, (int, float)):
        return str(s)
    return "'" + str(s).replace("\\", "\\\\").replace("'", "''") + "'"


def slugify(s):
    out = "".join(c.lower() if c.isalnum() else "-" for c in s)
    while "--" in out:
        out = out.replace("--", "-")
    return out.strip("-")


def to_utc(hhmm_local):
    h, m = map(int, hhmm_local.split(":"))
    total = (h * 60 + m - 330) % (24 * 60)
    return f"{total // 60:02d}:{total % 60:02d}"


def phone():
    return "+91" + random.choice("6789") + "".join(random.choice("0123456789") for _ in range(9))


out = []
w = out.append


def insert(table, cols, rows):
    if rows:
        w(f"INSERT INTO {table} ({', '.join(cols)}) VALUES")
        w(",\n".join("  (" + ", ".join(r) + ")" for r in rows) + ";")
        w("")


w("-- BookEasy demo cinemas, showtimes, restaurants and cafés (Tamil Nadu). Load after demo-tamilnadu.sql.")
w("-- GENERATED by backend/seed/generate_movies_dining.py - edit the script, not this file.")
w("-- Everything here is fictional. Owners: demo.venue.*@example.com / Demo@1234. DO NOT run against production.")
w(f"-- Showtimes cover the {SHOW_DAYS} days after loading; load the file again for fresh ones.")
w("SET NAMES utf8mb4;")
w("START TRANSACTION;")
w("")
w("-- Remove earlier demo venues and movies")
w("CREATE TEMPORARY TABLE demo_venue_users AS SELECT id FROM users WHERE email LIKE 'demo.venue.%@example.com';")
w("CREATE TEMPORARY TABLE demo_venue_orgs AS SELECT o.id FROM organizations o JOIN organization_owners oo ON oo.organization_id = o.id "
  "WHERE oo.user_id IN (SELECT id FROM demo_venue_users);")
w("DELETE FROM appointments WHERE organization_id IN (SELECT id FROM demo_venue_orgs);")
w("DELETE FROM organizations WHERE id IN (SELECT id FROM demo_venue_orgs);")
w("DELETE FROM users WHERE id IN (SELECT id FROM demo_venue_users);")
w("DELETE FROM movies WHERE title IN (" + ", ".join(q(m[0]) for m in MOVIES) + ");")
w("DROP TEMPORARY TABLE demo_venue_users;")
w("DROP TEMPORARY TABLE demo_venue_orgs;")
w("")
for var, table in [("u", "users"), ("o", "organizations"), ("b", "organization_branches"), ("s", "services"),
                   ("m", "movies"), ("sc", "cinema_screens")]:
    w(f"SET @{var} = (SELECT COALESCE(MAX(id), 0) FROM {table});")
w("")

insert("movies", ["id", "title", "languages", "certificate", "genres", "duration_minutes", "synopsis", "cast_names",
                  "release_date", "poster_from", "poster_to", "is_active", "created_at"],
       [[f"@m + {i + 1}", q(t), q(lang), q(cert), q(gen), str(dur), q(syn), q(cast),
         f"UTC_DATE() - INTERVAL {random.randint(1, 20)} DAY", q(c1), q(c2), "TRUE", "UTC_TIMESTAMP(6)"]
        for i, (t, lang, cert, gen, dur, c1, c2, syn, cast) in enumerate(MOVIES)])

users, roles, orgs, owners, verifs, branches, hours, services, profiles, screens, shows, menu = ([] for _ in range(12))
venues = [("CINEMA", c) for c in CINEMAS] + [("DINING", r) for r in RESTAURANTS]
for n, (kind, v) in enumerate(venues, start=1):
    first, last = random.choice(FIRST), random.choice(LAST)
    users.append([f"@u + {n}", q(f"{first} {last}"), q(f"demo.venue.{slugify(first)}{n}@example.com"), q(phone()),
                  q(PASSWORD_HASH), "'ACTIVE'", "UTC_TIMESTAMP(6)", "UTC_TIMESTAMP(6)", "'en'", "UTC_TIMESTAMP(6)", "UTC_TIMESTAMP(6)"])
    roles += [[f"@u + {n}", "1"], [f"@u + {n}", "2"]]
    if kind == "CINEMA":
        name, city, locality, lat, lng, scr = v
        sub, desc = CINEMA_SUB, f"Neighbourhood multiplex in {locality} with {len(scr)} screen{'s' if len(scr) > 1 else ''}, online seat selection and snacks counter."
        hours_key = "cinema"
    else:
        name, sub, city, locality, lat, lng, cuisines, menu_key, cost, veg, cap, offer, feats, hours_key = v
        desc = f"{cuisines} in {locality}, {city}. Book a table online - it's free."
    slug = f"{slugify(name)}-{slugify(city)}"
    org_phone = phone()
    days_ago = random.randint(30, 200)
    orgs.append([f"@o + {n}", str(sub), q(name), q(slug), q(desc), q(f"demo.contact.{slug[:40]}@example.com"), q(org_phone),
                 "'APPROVED'", "'INSTANT'", f"UTC_TIMESTAMP(6) - INTERVAL {days_ago - 1} DAY",
                 f"UTC_TIMESTAMP(6) - INTERVAL {days_ago - 3} DAY", f"@u + {n}",
                 f"UTC_TIMESTAMP(6) - INTERVAL {days_ago} DAY", "UTC_TIMESTAMP(6)"])
    owners.append([f"@o + {n}", f"@u + {n}", "'PRIMARY'", "UTC_TIMESTAMP(6)"])
    verifs.append([f"@o + {n}", "'SUBMITTED'", "'DRAFT'", "'PENDING'", "NULL", f"UTC_TIMESTAMP(6) - INTERVAL {days_ago - 1} DAY"])
    verifs.append([f"@o + {n}", "'APPROVED'", "'PENDING'", "'APPROVED'", "'Documents verified (demo data)'",
                   f"UTC_TIMESTAMP(6) - INTERVAL {days_ago - 3} DAY"])
    street = random.choice(["Main Road", "Gandhi Street", "Nehru Street", "Kamarajar Salai", "Periyar Street", "Car Street"])
    branches.append([f"@b + {n}", f"@o + {n}", "'Main branch'", q(f"No. {random.randint(1, 250)}, {street}"), "NULL",
                     q(locality), q(city), "'Tamil Nadu'", q(PINS[locality]), str(lat), str(lng), q(org_phone),
                     "UTC_TIMESTAMP(6)", "UTC_TIMESTAMP(6)"])
    o_, c_, bs, be = HOURS[hours_key]
    for d in range(1, 8):
        hours.append([f"@b + {n}", str(d), q(o_), q(c_), q(bs), q(be)])
    services.append([f"@s + {n}", f"@o + {n}", q("Movie tickets" if kind == "CINEMA" else "Table booking"),
                     "180" if kind == "CINEMA" else "90", "0.00", "FALSE", "30", "0", "FALSE", "UTC_TIMESTAMP(6)", "UTC_TIMESTAMP(6)"])
    if kind == "CINEMA":
        profiles.append([f"@o + {n}", "'CINEMA'", f"@s + {n}", "NULL", "NULL", "FALSE", "NULL", "NULL",
                         q("Dolby sound, Parking, Wheelchair access, Food court")])
        for screen_name, layout, fmt in scr:
            screens.append((n, screen_name, layout, fmt))
    else:
        profiles.append([f"@o + {n}", "'DINING'", f"@s + {n}", q(cuisines), f"{cost:.2f}", q(veg), str(cap), q(offer), q(feats)])
        sort = 0
        for section, items in MENUS[menu_key]:
            for dish, price, is_veg, best, about in items:
                if veg and not is_veg:
                    continue
                sort += 1
                menu.append([f"@o + {n}", q(section), q(dish), q(about), f"{price:.2f}", q(is_veg), q(best), str(sort)])

insert("users", ["id", "full_name", "email", "phone_e164", "password_hash", "status", "email_verified_at",
                 "phone_verified_at", "preferred_language", "created_at", "updated_at"], users)
insert("user_roles", ["user_id", "role_id"], roles)
insert("organizations", ["id", "subcategory_id", "name", "slug", "description", "contact_email", "contact_phone",
                         "verification_status", "confirmation_mode", "submitted_at", "approved_at", "created_by",
                         "created_at", "updated_at"], orgs)
insert("organization_owners", ["organization_id", "user_id", "role", "created_at"], owners)
insert("organization_verifications", ["organization_id", "action", "from_status", "to_status", "reason", "created_at"], verifs)
insert("organization_branches", ["id", "organization_id", "name", "address_line1", "address_line2", "locality", "city",
                                 "state", "pincode", "latitude", "longitude", "phone", "created_at", "updated_at"], branches)
insert("working_hours", ["branch_id", "day_of_week", "opens_at", "closes_at", "break_start", "break_end"], hours)
insert("services", ["id", "organization_id", "name", "duration_minutes", "price", "requires_staff", "slot_interval_minutes",
                    "min_notice_minutes", "is_active", "created_at", "updated_at"], services)
insert("venue_profiles", ["organization_id", "kind", "booking_service_id", "cuisines", "cost_for_two", "veg_only",
                          "capacity_per_slot", "offer_text", "features"], profiles)
insert("menu_items", ["organization_id", "section", "name", "description", "price", "is_veg", "is_bestseller", "sort_order"], menu)

import json  # noqa: E402

screen_rows, show_rows = [], []
for i, (n, screen_name, layout, fmt) in enumerate(screens, start=1):
    screen_rows.append([f"@sc + {i}", f"@b + {n}", q(screen_name), q(json.dumps(LAYOUTS[layout], separators=(",", ":"))),
                        "UTC_TIMESTAMP(6)"])
    tiers = {r["tier"] for r in LAYOUTS[layout]}
    extra = {"2D": 0, "3D": 60, "IMAX": 140, "4DX": 200}[fmt]
    city_bump = 20 if venues[n - 1][1][1] == "Chennai" else 0
    prices = json.dumps({t: PRICES[t] + extra + city_bump for t in ["CLASSIC", "PRIME", "RECLINER"] if t in tiers})
    # Each screen plays two films a day; the line-up changes every few days.
    for day in range(SHOW_DAYS):
        week_slot = (i + day // 4) % len(MOVIES)
        films = [week_slot, (week_slot + 3) % len(MOVIES)]
        for k, local in enumerate(SHOW_TIMES):
            m_idx = films[0] if k in (0, 2) else films[1]
            title, langs = MOVIES[m_idx][0], MOVIES[m_idx][1]
            language = langs.split(", ")[0] if k != 3 else langs.split(", ")[-1]
            show_rows.append([f"@sc + {i}", f"@m + {m_idx + 1}",
                              f"TIMESTAMP(UTC_DATE() + INTERVAL {day} DAY, '{to_utc(local)}:00')", q(fmt), q(language),
                              q(prices), "UTC_TIMESTAMP(6)"])

insert("cinema_screens", ["id", "branch_id", "name", "layout", "created_at"], screen_rows)
for i in range(0, len(show_rows), 300):
    insert("showtimes", ["screen_id", "movie_id", "starts_at", "format", "language", "prices", "created_at"], show_rows[i:i + 300])

w("COMMIT;")
w(f"-- {len(MOVIES)} movies, {len(CINEMAS)} cinemas, {len(screen_rows)} screens, {len(show_rows)} shows, "
  f"{len(RESTAURANTS)} restaurants/cafés, {len(menu)} dishes")
Path(__file__).with_name("demo-movies-dining.sql").write_text("\n".join(out) + "\n", encoding="utf-8")
print(out[-1])

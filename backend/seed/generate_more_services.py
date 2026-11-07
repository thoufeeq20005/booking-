"""Generates demo-education-fitness.sql: more tuition/coaching centres, music & dance schools, gyms and yoga studios.

    python backend/seed/generate_more_services.py --fetch-photos   # once: CC0 cover photos from Openverse
    python backend/seed/generate_more_services.py                  # rewrites demo-education-fitness.sql

Load it after demo-tamilnadu.sql, then run install_demo_photos.py. Every business, person and review is fictional; only
the localities are real. Owners sign in as demo.svc.*@example.com / Demo@1234. Safe to load again: it replaces only its own
rows (and bookings made at these businesses). Never run it in production.
"""
import hashlib
import json
import random
import sys
import uuid
from pathlib import Path

SEED = Path(__file__).resolve().parent
sys.path.insert(0, str(SEED))
import generate_demo_data as main  # noqa: E402  (also regenerates demo-tamilnadu.sql, deterministically)

random.seed(3141)
q, slugify, to_utc = main.q, main.slugify, main.to_utc
TUITION, MUSIC, GYM, YOGA = 8, 9, 10, 11
TITLES = {TUITION: ("Mr.", "Ms."), MUSIC: ("Guru", "Guru"), GYM: ("Coach", "Coach"), YOGA: ("Yogi", "Yogini")}

# kind -> (subcategory, name suffixes, description, hours key, services [(name, minutes, ₹, online?)], specialisations, photo searches)
KINDS = {
    "neet": (TUITION, ["NEET & JEE Academy", "Entrance Coaching Centre"],
             "Small-batch coaching for NEET and JEE with weekly tests, doubt-clearing sessions and parent reviews.",
             "tuition", [("Free demo class", 45, 0, False), ("Physics doubt-clearing session", 60, 400, False),
                         ("Chemistry problem-solving session", 60, 400, False), ("Biology revision class", 60, 350, False),
                         ("Mock test review (online)", 45, 300, True)],
             ["Physics", "Chemistry", "Biology", "Mathematics"], ["students studying classroom", "classroom lecture"]),
    "school": (TUITION, ["Tuition Centre", "Learning Centre"],
               "After-school tuition for classes 6–12 (State Board and CBSE) in small groups of up to 8 students.",
               "tuition", [("Free assessment", 30, 0, False), ("Maths class - Class 10", 60, 300, False),
                           ("Science class - Class 10", 60, 300, False), ("Maths class - Class 12", 60, 400, False),
                           ("English grammar class", 45, 250, False)],
               ["Mathematics", "Science", "English", "Accountancy"], ["student homework desk", "tutor teaching child"]),
    "english": (TUITION, ["Spoken English Hub", "Communication Academy"],
                "Spoken English, interview preparation and IELTS basics for students and working professionals.",
                "tuition", [("Free speaking assessment", 30, 0, False), ("Spoken English class", 60, 350, False),
                            ("Interview practice session", 45, 500, False), ("IELTS speaking practice (online)", 45, 450, True)],
                ["Spoken English", "IELTS", "Soft skills"], ["group discussion students", "people talking meeting"]),
    "computer": (TUITION, ["Computer Academy", "Coding School"],
                 "Hands-on computer classes: MS Office, Tally, Python and web basics, with a laptop for every learner.",
                 "tuition", [("Free trial class", 45, 0, False), ("Python for beginners", 90, 500, False),
                             ("MS Excel & Office skills", 60, 350, False), ("Tally with GST", 60, 400, False),
                             ("Kids coding (Scratch)", 60, 350, False)],
                 ["Python", "Tally", "Web development"], ["laptop coding", "computer lab"]),
    "music": (MUSIC, ["Music Academy", "School of Music"],
              "Carnatic vocal, keyboard and guitar lessons for children and adults; exam preparation available.",
              "tuition", [("Free trial lesson", 30, 0, False), ("Carnatic vocal class", 60, 400, False),
                          ("Keyboard lesson", 45, 450, False), ("Guitar lesson", 45, 450, False)],
              ["Carnatic vocal", "Keyboard", "Guitar"], ["guitar lesson", "piano keyboard"]),
    "dance": (MUSIC, ["Dance Studio", "Natya School"],
              "Bharatanatyam and Western dance classes in batches by age; stage shows twice a year.",
              "tuition", [("Free trial class", 45, 0, False), ("Bharatanatyam class", 60, 350, False),
                          ("Western dance class", 60, 350, False), ("Kids dance (5–8 years)", 45, 300, False)],
              ["Bharatanatyam", "Contemporary", "Hip-hop"], ["dance class", "dancer studio"]),
    "gym": (GYM, ["Fitness Club", "Strength Gym", "Fitness Studio"],
            "Strength and cardio floor with certified trainers, body-composition checks and diet guidance.",
            "gym", [("Free trial workout", 45, 0, False), ("Gym day pass", 60, 250, False),
                    ("Personal training session", 60, 600, False), ("Body composition analysis", 20, 200, False)],
            ["Strength training", "Weight loss", "Functional training"], ["gym weights", "dumbbells gym"]),
    "crossfit": (GYM, ["CrossFit Box", "Functional Fitness"],
                 "High-intensity group workouts, kettlebells and mobility sessions in small coached batches.",
                 "gym", [("Free intro WOD", 45, 0, False), ("HIIT group class", 45, 300, False),
                         ("Kettlebell workshop", 60, 450, False), ("Personal training session", 60, 700, False)],
                 ["HIIT", "Kettlebells", "Mobility"], ["crossfit workout", "kettlebell training"]),
    "ladies": (GYM, ["Ladies Fitness Studio", "Women's Fitness Studio"],
               "A women-only studio with women trainers: strength, Zumba and post-natal fitness.",
               "gym", [("Free trial class", 45, 0, False), ("Zumba class", 45, 300, False),
                       ("Strength training for women", 60, 500, False), ("Post-natal fitness session", 45, 500, False)],
               ["Zumba", "Strength training", "Post-natal fitness"], ["aerobics class", "zumba dance fitness"]),
    "yoga": (YOGA, ["Yoga Studio", "Yoga & Wellness Centre"],
             "Hatha and power yoga, breathing and meditation in calm, small batches; online classes too.",
             "gym", [("Free trial class", 45, 0, False), ("Hatha yoga class", 60, 300, False),
                     ("Power yoga class", 60, 350, False), ("Private yoga session", 60, 800, False),
                     ("Online yoga class", 45, 250, True)],
             ["Hatha yoga", "Power yoga", "Pranayama"], ["yoga class", "yoga mat meditation"]),
}
# Each city gets 5 education and 5 fitness places.
EDU = ["neet", "school", "english", "computer", "music", "dance"]
FIT = ["gym", "crossfit", "ladies", "yoga", "gym", "yoga"]
PREFIXES = {
    "Chennai": ["Marina", "Adyar", "Mylai", "Velachery", "Anna Nagar", "Egmore"],
    "Coimbatore": ["Kovai", "Brindhavan", "Race Course", "Peelamedu", "Noyyal", "Siruvani"],
    "Madurai": ["Vaigai", "Koodal", "Meenakshi", "Tallakulam", "Alagar", "Thirumalai"],
    "Tiruchirappalli": ["Rockfort", "Kaveri", "Srirangam", "Thillai", "Malaikottai", "Ponmalai"],
    "Salem": ["Steel City", "Yercaud", "Shevaroy", "Fairlands", "Mango City", "Kolli"],
    "Thoothukudi": ["Pearl City", "Bay View", "Muthu", "Tuticorin", "Harbour", "Kayal"],
}
STEMS = ["Bright", "Summit", "Elevate", "Prime", "Ascent", "Pinnacle", "Lotus", "Nova", "Orbit", "Zenith", "Vetri", "Sigaram"]
FIT_STEMS = ["Iron", "Pulse", "Core", "Titan", "Flex", "Stride", "Prana", "Shanthi", "Atlas", "Vigor", "Surya", "Kinetic"]
EDU_REVIEWS = {5: ["Teachers are patient and explain every doubt. My marks improved a lot.",
                   "Small batches, regular tests and honest feedback to parents. Highly recommended.",
                   "Booking a trial class here was easy and the session was really useful."],
               4: ["Good teaching and a calm place to study. Timings could be more flexible.",
                   "Helpful faculty and clear notes. Parking is a little difficult in the evening."],
               3: ["Decent classes, but the batch felt a bit crowded on weekends."]}
FIT_REVIEWS = {5: ["Trainers are knowledgeable and push you safely. Clean equipment and changing rooms.",
                   "Lost 6 kg in three months with their plan. Great energy in the group classes.",
                   "Peaceful studio and a very kind instructor. My back pain is much better."],
               4: ["Good gym with all the basic equipment. Gets busy around 7 pm.",
                   "Nice classes and friendly staff. AC could be stronger in summer."],
               3: ["Equipment is fine but the evening batch is too crowded."]}


def photo_rel(slug):
    return f"svc/{slug}.jpg"


def plan_businesses():
    random.seed(3141)
    out, used = [], set()
    for city, locs in main.CITIES.items():
        edu = random.sample(EDU, 5)
        fit = random.sample(FIT, 5)
        for kind in edu + fit:
            sub, suffixes, *_ = KINDS[kind]
            stems = FIT_STEMS if sub in (GYM, YOGA) else STEMS
            while True:
                name = f"{random.choice(PREFIXES[city] + stems)} {random.choice(suffixes)}"
                if name not in used:
                    used.add(name)
                    break
            out.append((city, kind, name, random.choice(locs)))
    return out


def fetch_photos():
    import fetch_venue_photos as fv
    import time
    credits_path = fv.PHOTOS / "CREDITS.json"
    existing = json.loads(credits_path.read_text(encoding="utf-8"))
    taken = {c["source"] for c in existing.values()}
    orig = fv.search

    def paced(query):
        time.sleep(3.5)  # Openverse allows 20 anonymous searches a minute
        # Photographs only: no paintings, engravings or clip art.
        return [h for h in orig(query) if (h.get("foreign_landing_url") or h["url"]) not in taken
                and h.get("category") in (None, "photograph")]

    fv.search = paced
    fv.BLOCKED = fv.BLOCKED + ("logo", "brand", "nike", "adidas", "reebok", "gold's", "university", "school bus", "precor",
                              "painting", "drawing", "cartoon", "clipart", "clip art", "illustration", "engraving", "horse",
                              "aqua", "pool", "madrasa", "burqa")
    (fv.PHOTOS / "svc").mkdir(exist_ok=True)
    for city, kind, name, _loc in plan_businesses():
        rel = photo_rel(f"{slugify(name)}-{slugify(city)}")
        if (fv.PHOTOS / rel).exists():
            continue
        searches = KINDS[kind][6]
        fv.SOURCES = "stocksnap,rawpixel"
        broad = ["fitness training", "workout", "exercise"] if KINDS[kind][0] in (GYM, YOGA) else ["studying", "classroom", "learning"]
        try:
            fv.save(searches, rel, (1200, 900))
        except SystemExit:
            fv.SOURCES = None
            try:
                fv.save(searches + broad, rel, (1200, 900))
            except SystemExit:
                print("  no photo yet for", rel)  # left out of the SQL until one is found
                continue
        existing.update(fv.credits)
        taken.update(c["source"] for c in fv.credits.values())
        credits_path.write_text(json.dumps(existing, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


def generate():
    random.seed(2718)
    plan = [b for b in plan_businesses() if (SEED / "photos" / photo_rel(f"{slugify(b[2])}-{slugify(b[0])}")).exists()]
    skipped = len(plan_businesses()) - len(plan)
    out = []
    w = out.append

    def insert(table, cols, rows):
        for i in range(0, len(rows), 200):
            w(f"INSERT INTO {table} ({', '.join(cols)}) VALUES")
            w(",\n".join("  (" + ", ".join(r) + ")" for r in rows[i:i + 200]) + ";")
            w("")

    w("-- BookEasy demo: more tuition/coaching centres, music & dance schools, gyms and yoga studios (Tamil Nadu).")
    w("-- GENERATED by backend/seed/generate_more_services.py - edit the script, not this file. Fictional; DO NOT run in production.")
    w("SET NAMES utf8mb4;")
    w("START TRANSACTION;")
    w("CREATE TEMPORARY TABLE demo_svc_users AS SELECT id FROM users WHERE email LIKE 'demo.svc.%@example.com';")
    w("CREATE TEMPORARY TABLE demo_svc_orgs AS SELECT o.id FROM organizations o JOIN organization_owners oo ON oo.organization_id = o.id "
      "WHERE oo.user_id IN (SELECT id FROM demo_svc_users);")
    w("CREATE TEMPORARY TABLE demo_appts AS SELECT id FROM appointments WHERE organization_id IN (SELECT id FROM demo_svc_orgs) "
      "OR customer_id IN (SELECT id FROM demo_svc_users);")
    for t in ["refunds", "commission_records", "payments", "reviews"]:
        w(f"DELETE FROM {t} WHERE appointment_id IN (SELECT id FROM demo_appts);")
    w("DELETE FROM appointments WHERE id IN (SELECT id FROM demo_appts);")
    w("DELETE FROM organizations WHERE id IN (SELECT id FROM demo_svc_orgs);")
    w("DELETE FROM users WHERE id IN (SELECT id FROM demo_svc_users);")
    w("DELETE FROM uploaded_files WHERE storage_key LIKE 'public/demo/svc-%';")
    for tmp in ["demo_appts", "demo_svc_orgs", "demo_svc_users"]:
        w(f"DROP TEMPORARY TABLE {tmp};")
    for var, table in [("u", "users"), ("o", "organizations"), ("b", "organization_branches"), ("s", "services"),
                       ("st", "staff_profiles"), ("a", "appointments"), ("f", "uploaded_files")]:
        w(f"SET @{var} = (SELECT COALESCE(MAX(id), 0) FROM {table});")
    w("")

    users, roles, orgs, owners, verifs, branches, hours, services, branch_services = ([] for _ in range(9))
    staff, staff_branches, staff_services, staff_avail, files, past, reviews = ([] for _ in range(7))
    copy_plan = {}
    uid = sid = stid = 0
    phones = set()

    def phone():
        while True:
            p = "+91" + random.choice("6789") + "".join(random.choice("0123456789") for _ in range(9))
            if p not in phones:
                phones.add(p)
                return p

    def new_user(full, kind, role_ids, with_phone=True):
        nonlocal uid
        uid += 1
        users.append([f"@u + {uid}", q(full), q(f"demo.svc.{kind}.{slugify(full.split()[0])}{uid}@example.com"),
                      q(phone()) if with_phone else "NULL", q(main.PASSWORD_HASH), "'ACTIVE'", "UTC_TIMESTAMP(6)",
                      "UTC_TIMESTAMP(6)" if with_phone else "NULL", "'en'", "UTC_TIMESTAMP(6)", "UTC_TIMESTAMP(6)"])
        roles.extend([[f"@u + {uid}", str(r)] for r in role_ids])
        return uid

    learners = [new_user(main.person()[1], "learner", [1]) for _ in range(20)]
    for n, (city, kind, name, loc) in enumerate(plan, start=1):
        sub, _suffixes, desc, hours_key, svc_list, specs, _qs = KINDS[kind]
        locality, pin, lat, lng = loc
        lat += random.uniform(-0.006, 0.006)
        lng += random.uniform(-0.006, 0.006)
        owner = new_user(main.person()[1], "owner", [1, 2])
        slug = f"{slugify(name)}-{slugify(city)}"
        days_ago = random.randint(30, 400)
        org_phone = phone()
        orgs.append([f"@o + {n}", str(sub), q(name), q(slug), q(f"{desc} {locality}, {city}."),
                     q(f"demo.contact.{slug[:40]}@example.com"), q(org_phone), "'APPROVED'", "'INSTANT'",
                     f"UTC_TIMESTAMP(6) - INTERVAL {days_ago - 1} DAY", f"UTC_TIMESTAMP(6) - INTERVAL {days_ago - 3} DAY",
                     f"@u + {owner}", f"UTC_TIMESTAMP(6) - INTERVAL {days_ago} DAY", "UTC_TIMESTAMP(6)"])
        owners.append([f"@o + {n}", f"@u + {owner}", "'PRIMARY'", "UTC_TIMESTAMP(6)"])
        verifs.append([f"@o + {n}", "'SUBMITTED'", "'DRAFT'", "'PENDING'", "NULL", f"UTC_TIMESTAMP(6) - INTERVAL {days_ago - 1} DAY"])
        verifs.append([f"@o + {n}", "'APPROVED'", "'PENDING'", "'APPROVED'", "'Documents verified (demo data)'",
                       f"UTC_TIMESTAMP(6) - INTERVAL {days_ago - 3} DAY"])
        street = random.choice(["Main Road", "Gandhi Street", "Nehru Street", "2nd Cross Street", "Kamarajar Salai", "100 Feet Road"])
        branches.append([f"@b + {n}", f"@o + {n}", "'Main branch'", q(f"No. {random.randint(1, 250)}, {street}"), "NULL",
                         q(locality), q(city), "'Tamil Nadu'", q(pin), f"{lat:.6f}", f"{lng:.6f}", q(org_phone),
                         "UTC_TIMESTAMP(6)", "UTC_TIMESTAMP(6)"])
        week = main.HOURS[hours_key]
        for day, (o_, c_, bs, be) in week.items():
            hours.append([f"@b + {n}", str(day), q(o_), q(c_), q(bs), q(be)])
        org_services = []
        for sname, minutes, price, online in svc_list:
            sid += 1
            price = round(price * random.choice([0.9, 1.0, 1.0, 1.1]) / 10) * 10
            services.append([f"@s + {sid}", f"@o + {n}", q(sname), q(f"{sname} at {name}."), str(minutes), f"{price:.2f}",
                             q("ONLINE" if online else "IN_PERSON"), "TRUE", "0", "15" if minutes <= 30 else "30",
                             "UTC_TIMESTAMP(6)", "UTC_TIMESTAMP(6)"])
            branch_services.append([f"@b + {n}", f"@s + {sid}"])
            org_services.append((sid, sname, price, minutes))
        org_staff = []
        for i in range(2):
            gender, sname = main.person()
            title = TITLES[sub][0 if gender == "M" else 1]
            staff_user = new_user(sname, "staff", [1, 3], with_phone=False)
            stid += 1
            staff.append([f"@st + {stid}", f"@o + {n}", f"@u + {staff_user}", q(sname), q(title), q(specs[i % len(specs)]),
                          q('["CAN_CONFIRM","CAN_CANCEL"]' if i == 0 else "[]"), "'ACTIVE'", "UTC_TIMESTAMP(6)", "UTC_TIMESTAMP(6)"])
            staff_branches.append([f"@st + {stid}", f"@b + {n}"])
            for day, (o_, c_, _bs, _be) in week.items():
                if day == 7 and i > 0:
                    continue
                staff_avail.append([f"@st + {stid}", f"@b + {n}", str(day), q(o_), q(c_)])
            for s in org_services:
                staff_services.append([f"@st + {stid}", f"@s + {s[0]}"])
            org_staff.append(stid)
        # Cover photo
        rel = photo_rel(slug)
        data = (SEED / "photos" / rel).read_bytes()
        key = f"public/demo/svc-{slug}.jpg"
        files.append([f"@f + {n}", q(str(uuid.uuid5(uuid.NAMESPACE_URL, "bookeasy-demo:" + key))), q(key), q(Path(rel).name),
                      "'image/jpeg'", str(len(data)), q(hashlib.sha256(data).hexdigest()), "'PUBLIC'", "'ORGANIZATION_COVER'",
                      f"@u + {owner}", "UTC_TIMESTAMP(6)"])
        copy_plan[key] = "photos/" + rel
        # Past sessions and reviews
        quality = random.choice([5, 5, 4, 4, 4])
        pool = FIT_REVIEWS if sub in (GYM, YOGA) else EDU_REVIEWS
        for _ in range(random.randint(4, 9)):
            svc = random.choice([s for s in org_services if s[2] > 0] or org_services)
            days = random.randint(3, 80)
            local = random.choice(["06:30", "07:00", "18:00", "19:00"] if hours_key == "gym" else ["16:30", "17:30", "18:30"])
            start = f"TIMESTAMP(UTC_DATE() - INTERVAL {days} DAY, '{to_utc(local)}:00')"
            fee = round(svc[2] * 0.05, 2)
            ref = "BS" + "".join(random.choice(main.REF_ALPHABET) for _ in range(8))
            learner = random.choice(learners)
            st = random.choice(org_staff)
            past.append([f"@a + {len(past) + 1}", q(ref), f"@u + {learner}", f"@o + {n}", f"@b + {n}", f"@s + {svc[0]}", f"@st + {st}",
                         start, f"{start} + INTERVAL {svc[3]} MINUTE", f"{start} + INTERVAL {svc[3]} MINUTE", "'COMPLETED'",
                         q(svc[1]), f"{svc[2]:.2f}", f"{fee:.2f}", f"{svc[2] + fee:.2f}", f"{start} - INTERVAL 5 MINUTE",
                         f"{start} + INTERVAL {svc[3]} MINUTE", f"{start} - INTERVAL 2 DAY", f"{start} + INTERVAL {svc[3]} MINUTE"])
            rating = max(3, min(5, quality + random.choice([0, 0, 0, -1, 1])))
            reviews.append([f"@a + {len(past)}", f"@o + {n}", f"@u + {learner}", f"@st + {st}", str(rating),
                            q(random.choice(pool[rating])), f"{start} + INTERVAL 1 DAY", f"{start} + INTERVAL 1 DAY"])

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
    insert("services", ["id", "organization_id", "name", "description", "duration_minutes", "price", "consultation_type",
                        "requires_staff", "buffer_minutes", "slot_interval_minutes", "created_at", "updated_at"], services)
    insert("branch_services", ["branch_id", "service_id"], branch_services)
    insert("staff_profiles", ["id", "organization_id", "user_id", "display_name", "title", "specialisation", "permissions",
                              "status", "created_at", "updated_at"], staff)
    insert("staff_branches", ["staff_id", "branch_id"], staff_branches)
    insert("staff_services", ["staff_id", "service_id"], staff_services)
    insert("staff_availability", ["staff_id", "branch_id", "day_of_week", "start_time", "end_time"], staff_avail)
    insert("uploaded_files", ["id", "public_id", "storage_key", "original_name", "content_type", "size_bytes", "sha256",
                              "visibility", "purpose", "owner_user_id", "created_at"], files)
    for n in range(1, len(plan) + 1):
        w(f"UPDATE organizations SET cover_file_id = @f + {n} WHERE id = @o + {n};")
    insert("appointments", ["id", "reference", "customer_id", "organization_id", "branch_id", "service_id", "staff_id",
                            "starts_at", "ends_at", "blocked_until", "status", "service_name", "service_price", "platform_fee",
                            "total_amount", "checked_in_at", "completed_at", "created_at", "updated_at"], past)
    insert("reviews", ["appointment_id", "organization_id", "customer_id", "staff_id", "rating", "comment", "created_at",
                       "updated_at"], reviews)
    w("UPDATE organizations o JOIN (SELECT organization_id, ROUND(AVG(rating), 2) avg_r, COUNT(*) n FROM reviews "
      "WHERE status = 'VISIBLE' GROUP BY organization_id) r ON r.organization_id = o.id "
      "SET o.rating_avg = r.avg_r, o.review_count = r.n WHERE o.id > @o;")
    w("COMMIT;")
    w(f"-- {len(plan)} businesses ({skipped} waiting for photos), {len(services)} services, {len(staff)} staff, "
      f"{len(reviews)} reviews")
    (SEED / "demo-education-fitness.sql").write_text("\n".join(out) + "\n", encoding="utf-8")
    (SEED / "demo-svc-photo-files.json").write_text(json.dumps(copy_plan, indent=1), encoding="utf-8")
    print(out[-1])


if __name__ == "__main__":
    if "--fetch-photos" in sys.argv:
        fetch_photos()
    generate()

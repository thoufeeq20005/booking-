"""Generates demo-tamilnadu.sql: sample businesses for six Tamil Nadu cities.

All businesses, people, phone numbers and reviews are fictional. Localities, PIN codes and
coordinates are real so nearby search works. The output is deterministic (fixed random seed).

    python backend/seed/generate_demo_data.py      # rewrites backend/seed/demo-tamilnadu.sql

Every demo account uses an @example.com address starting with "demo." and the password Demo@1234.
The SQL first removes any earlier demo data, so it can be run again safely. Never run it in production.

Photos (CC0, from StockSnap via Openverse; see photos/CREDITS.json) are copied into file storage by
install_demo_photos.py, using demo-photo-files.json written by this script.
"""
import hashlib
import json
import random
import uuid
from pathlib import Path

random.seed(2026)

PASSWORD_HASH = "$2a$12$.hqQmqoWu/3C6Q8L8h8cDuJcOinee8qIFn/Yu2qkPufDIGFSzx62K"  # Demo@1234
REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
COMMISSION = 0.05

# city -> (state, [(locality, pincode, lat, lng)], landmark prefixes for business names)
CITIES = {
    "Chennai": [
        ("T. Nagar", "600017", 13.0418, 80.2341), ("Anna Nagar", "600040", 13.0850, 80.2101),
        ("Adyar", "600020", 13.0012, 80.2565), ("Velachery", "600042", 12.9815, 80.2180),
        ("Mylapore", "600004", 13.0368, 80.2676), ("Nungambakkam", "600034", 13.0569, 80.2425),
        ("Porur", "600116", 13.0382, 80.1565), ("Tambaram", "600045", 12.9249, 80.1000),
    ],
    "Coimbatore": [
        ("R.S. Puram", "641002", 11.0081, 76.9505), ("Gandhipuram", "641012", 11.0183, 76.9681),
        ("Peelamedu", "641004", 11.0262, 77.0220), ("Saibaba Colony", "641011", 11.0260, 76.9419),
        ("Race Course", "641018", 11.0001, 76.9750), ("Singanallur", "641005", 10.9989, 77.0329),
        ("Saravanampatti", "641035", 11.0790, 77.0007),
    ],
    "Madurai": [
        ("Anna Nagar", "625020", 9.9218, 78.1475), ("K.K. Nagar", "625020", 9.9300, 78.1400),
        ("Tallakulam", "625002", 9.9375, 78.1340), ("Simmakkal", "625001", 9.9259, 78.1198),
        ("Goripalayam", "625002", 9.9330, 78.1260), ("Bypass Road", "625010", 9.9170, 78.0980),
        ("Thirunagar", "625006", 9.8800, 78.0700),
    ],
    "Thoothukudi": [
        ("Millerpuram", "628008", 8.7830, 78.1250), ("Bryant Nagar", "628008", 8.7900, 78.1300),
        ("Polepettai", "628002", 8.8000, 78.1400), ("Palayamkottai Road", "628003", 8.7700, 78.1300),
        ("Anna Nagar", "628003", 8.7860, 78.1390), ("Tiruchendur Road", "628002", 8.7500, 78.1350),
    ],
    "Tiruchirappalli": [
        ("Thillai Nagar", "620018", 10.8270, 78.6840), ("Cantonment", "620001", 10.8000, 78.6900),
        ("Srirangam", "620006", 10.8620, 78.6930), ("K.K. Nagar", "620021", 10.7650, 78.7050),
        ("Woraiyur", "620003", 10.8280, 78.6780), ("Thennur", "620017", 10.8180, 78.6880),
    ],
    "Salem": [
        ("Fairlands", "636016", 11.6740, 78.1350), ("Hasthampatti", "636007", 11.6700, 78.1550),
        ("Alagapuram", "636004", 11.6750, 78.1420), ("Ammapet", "636003", 11.6510, 78.1800),
        ("Suramangalam", "636005", 11.6700, 78.1150), ("Shevapet", "636002", 11.6530, 78.1600),
    ],
}

CITY_PREFIXES = {
    "Chennai": ["Marina", "Kapaleeshwarar", "Adyar River", "Chennai Central", "Elliot's"],
    "Coimbatore": ["Kovai", "Nilgiri View", "Siruvani", "Marudhamalai", "Noyyal"],
    "Madurai": ["Meenakshi", "Vaigai", "Thirumalai", "Koodal", "Alagar"],
    "Thoothukudi": ["Pearl City", "Muthu", "Bay View", "Tuticorin", "Kattabomman"],
    "Tiruchirappalli": ["Rockfort", "Kaveri", "Srirangam", "Malaikottai", "Trichy"],
    "Salem": ["Steel City", "Yercaud", "Shevaroy", "Mango City", "Thirumani"],
}
COMMON_PREFIXES = ["Sri Murugan", "Annai", "Thamarai", "Kurinji", "Vel", "Amudham", "Sakthi", "Malligai",
                   "Thendral", "Kalaimagal", "Arul", "Nila", "Senthil", "Ponni", "Vetri", "Kayal"]

# subcategory id -> template
TEMPLATES = {
    1: dict(suffixes=["Family Clinic", "Multispeciality Clinic", "Health Centre"], hours="clinic",
            desc="Friendly neighbourhood clinic for everyday illness, check-ups and follow-ups. Walk-ins welcome, booking preferred.",
            staff_title="Dr.", specialisations=["General Physician", "Paediatrician", "Diabetologist", "Family Medicine"],
            services=[("General consultation", 15, 300), ("Fever & infection consultation", 15, 350),
                      ("Diabetes check-up", 20, 500), ("Child health consultation", 15, 400),
                      ("Online teleconsultation", 15, 250, "ONLINE")]),
    2: dict(suffixes=["Dental Care", "Smile Dental Clinic", "Dental Studio"], hours="clinic",
            desc="Modern dental clinic with digital X-ray and sterilised instruments for painless treatment.",
            staff_title="Dr.", specialisations=["Dental Surgeon (BDS)", "Endodontist", "Orthodontist"],
            services=[("Dental check-up", 20, 300), ("Scaling & polishing", 45, 1200), ("Tooth filling", 40, 1500),
                      ("Root canal consultation", 30, 500), ("Teeth whitening", 60, 4500)]),
    3: dict(suffixes=["Eye Care", "Eye Clinic", "Vision Centre"], hours="clinic",
            desc="Complete eye check-ups, spectacle prescriptions and specialist consultations.",
            staff_title="Dr.", specialisations=["Ophthalmologist", "Optometrist", "Paediatric Ophthalmologist"],
            services=[("Comprehensive eye examination", 20, 400), ("Contact lens fitting", 30, 800),
                      ("Cataract consultation", 20, 600), ("Kids vision screening", 20, 350)]),
    4: dict(suffixes=["Diagnostics", "Scans & Labs", "Diagnostic Centre"], hours="lab", requires_staff=False,
            desc="NABL-standard lab tests and scans with home-ready reports on WhatsApp and email.",
            services=[("Full body check-up", 60, 2499), ("Thyroid profile", 15, 550), ("Fasting blood sugar", 10, 150),
                      ("Lipid profile", 15, 600), ("Digital X-ray", 15, 450)]),
    5: dict(suffixes=["Unisex Salon", "Hair Studio", "Family Salon"], hours="salon",
            desc="Trendy cuts, colour and grooming for men, women and kids in a hygienic, air-conditioned salon.",
            staff_title="Stylist", specialisations=["Senior hair stylist", "Colour expert", "Grooming specialist"],
            services=[("Men's haircut", 30, 250), ("Women's haircut", 45, 500), ("Beard styling", 20, 150),
                      ("Global hair colour", 90, 1500), ("Hair spa", 60, 900), ("Fruit facial", 60, 1200)]),
    6: dict(suffixes=["Beauty Parlour", "Bridal Studio", "Herbal Beauty Parlour"], hours="salon",
            desc="Ladies beauty parlour for bridal makeup, threading, facials and mehendi.",
            staff_title="Beautician", specialisations=["Bridal makeup artist", "Skin care expert", "Mehendi artist"],
            services=[("Eyebrow threading", 15, 60), ("Bridal makeup", 180, 12000), ("Party makeup", 90, 3500),
                      ("Full arms waxing", 30, 400), ("Mehendi (both hands)", 60, 800), ("Clean-up", 45, 600)]),
    7: dict(suffixes=["Ayurvedic Spa", "Wellness Spa", "Spa & Wellness"], hours="salon",
            desc="Relaxing therapies by certified therapists, including traditional Kerala and Ayurvedic massages.",
            staff_title="Therapist", specialisations=["Ayurvedic therapist", "Massage therapist", "Reflexologist"],
            services=[("Swedish massage", 60, 2200), ("Ayurvedic abhyanga", 75, 2800), ("Foot reflexology", 45, 1200),
                      ("Head & shoulder massage", 30, 800)]),
    8: dict(suffixes=["Tuition Centre", "Academy", "Coaching Centre"], hours="tuition",
            desc="Small-batch coaching for State Board and CBSE students with weekly tests and parent updates.",
            staff_title="Mr.", specialisations=["Mathematics", "Physics", "Chemistry", "Spoken English", "Biology (NEET)"],
            services=[("Maths class - Class 10", 60, 400), ("Physics class - Class 12", 60, 450),
                      ("NEET biology session", 90, 600), ("Spoken English", 60, 350), ("Online doubt-clearing", 45, 250, "ONLINE")]),
    9: dict(suffixes=["Kalakshetra", "Music & Dance School", "Fine Arts Academy"], hours="tuition",
            desc="Classical and western music and dance classes for all ages, taught by experienced gurus.",
            staff_title="Guru", specialisations=["Bharatanatyam", "Carnatic vocal", "Keyboard", "Veena", "Western dance"],
            services=[("Bharatanatyam class", 60, 500), ("Carnatic vocal class", 45, 450), ("Keyboard lesson", 45, 500),
                      ("Veena lesson", 60, 600), ("Western dance class", 60, 400)]),
    10: dict(suffixes=["Fitness Studio", "Gym", "Power Gym"], hours="gym",
             desc="Fully equipped gym with cardio, strength and functional training zones and certified trainers.",
             staff_title="Coach", specialisations=["Strength & conditioning", "Weight-loss coach", "CrossFit trainer"],
             services=[("Gym day pass", 60, 200), ("Personal training session", 60, 800),
                       ("Body composition analysis", 30, 300), ("CrossFit session", 45, 350)]),
    11: dict(suffixes=["Yoga Studio", "Yoga Shala", "Yoga & Meditation Centre"], hours="gym",
             desc="Traditional hatha yoga, pranayama and meditation in a calm, airy studio.",
             staff_title="Yogacharya", specialisations=["Hatha yoga", "Prenatal yoga", "Pranayama & meditation"],
             services=[("Hatha yoga class", 60, 300), ("Prenatal yoga", 60, 450), ("Pranayama & meditation", 45, 250),
                       ("Private yoga session", 60, 900), ("Online yoga class", 60, 200, "ONLINE")]),
}

# hours profile -> {day: (open, close, break_start, break_end)}; day 1 = Monday, missing day = closed
WEEK = range(1, 7)
HOURS = {
    "clinic": {**{d: ("09:00", "20:30", "13:30", "16:00") for d in WEEK}, 7: ("09:00", "13:00", None, None)},
    "lab": {**{d: ("07:00", "20:00", None, None) for d in WEEK}, 7: ("07:00", "12:00", None, None)},
    "salon": {d: ("09:30", "21:00", None, None) for d in range(1, 8)},
    "tuition": {**{d: ("16:00", "20:30", None, None) for d in range(1, 6)}, 6: ("09:00", "18:00", "13:00", "14:00"),
                7: ("09:00", "13:00", None, None)},
    "gym": {d: ("05:30", "21:30", "11:00", "16:00") for d in WEEK},
}

# Which subcategories each city gets (a mix so every city has variety).
CITY_PLAN = {
    "Chennai": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 2, 5],
    "Coimbatore": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
    "Madurai": [1, 2, 3, 4, 5, 6, 8, 9, 10, 11],
    "Thoothukudi": [1, 2, 4, 5, 6, 8, 10, 11],
    "Tiruchirappalli": [1, 2, 3, 4, 5, 6, 8, 9, 10],
    "Salem": [1, 2, 3, 4, 5, 7, 8, 10, 11],
}
MULTI_BRANCH = {"Chennai", "Coimbatore"}

MALE = ["Arun", "Karthik", "Senthil", "Vignesh", "Prakash", "Saravanan", "Balaji", "Manikandan", "Ramesh", "Suresh",
        "Dinesh", "Gokul", "Harish", "Muthu", "Rajesh", "Vijay", "Ashwin", "Naveen", "Sathish", "Murugan",
        "Kumaravel", "Thirumalai", "Anand", "Ganesh", "Hari", "Selvam", "Pandian", "Raghavan", "Venkatesh", "Mohan"]
FEMALE = ["Priya", "Lakshmi", "Divya", "Kavitha", "Meena", "Anitha", "Deepa", "Revathi", "Sangeetha", "Nandhini",
          "Gayathri", "Janani", "Keerthana", "Saranya", "Vidhya", "Bhuvana", "Malathi", "Pavithra", "Swathi", "Yamuna",
          "Abirami", "Indhu", "Kalaivani", "Mahalakshmi", "Shalini", "Sowmya", "Thenmozhi", "Uma", "Vasuki", "Selvi"]
SURNAMES = ["Kumar", "Raj", "Rajan", "Krishnan", "Subramanian", "Natarajan", "Sundaram", "Pandian", "Murugan",
            "Venkatesan", "Balasubramanian", "Ramasamy", "Chandran", "Shanmugam", "Palani", "Arumugam", "Iyer",
            "Pillai", "Mudaliar", "Gounder", "Nadar", "Thevar", "Selvaraj", "Durai", "Mani"]

REVIEWS = {
    5: ["Excellent service, very professional and on time. Highly recommended!",
        "Booking was smooth and there was no waiting at all. Super experience.",
        "Very clean place and friendly staff. Will definitely come again.",
        "Romba nalla irundhuchu! Staff explained everything patiently.",
        "Best in the area. Worth every rupee.",
        "Got my slot exactly on time, the QR check-in was quick."],
    4: ["Good service, slightly crowded on the weekend but still on time.",
        "Nice experience overall. Parking is a bit difficult.",
        "Staff were polite and the price was reasonable.",
        "Good place, waited about 10 minutes but the service was worth it."],
    3: ["Service was okay. Could improve the waiting area.",
        "Average experience, nothing special but nothing bad either."],
    2: ["Had to wait 30 minutes even after booking. Needs better time management."],
}
OWNER_REPLIES = ["Thank you for your kind words! See you again soon.",
                 "Thanks for the feedback, we're glad you liked it.",
                 "Sorry about the wait - we're adding more staff on weekends. Thanks for letting us know."]


def q(s):
    if s is None:
        return "NULL"
    if isinstance(s, (int, float)):
        return str(s)
    return "'" + str(s).replace("\\", "\\\\").replace("'", "''") + "'"


def ago(days, hhmm_utc="05:00"):
    return f"TIMESTAMP(UTC_DATE() - INTERVAL {days} DAY, '{hhmm_utc}:00')"


def slugify(s):
    out = "".join(c.lower() if c.isalnum() else "-" for c in s)
    while "--" in out:
        out = out.replace("--", "-")
    return out.strip("-")


def person(gender=None):
    gender = gender or random.choice("MF")
    first = random.choice(MALE if gender == "M" else FEMALE)
    return gender, f"{first} {random.choice(SURNAMES)}"


phones = set()


def phone():
    while True:
        p = "+91" + random.choice("6789") + "".join(random.choice("0123456789") for _ in range(9))
        if p not in phones:
            phones.add(p)
            return p


def to_utc(hhmm_local):
    h, m = map(int, hhmm_local.split(":"))
    total = (h * 60 + m - 330) % (24 * 60)
    return f"{total // 60:02d}:{total % 60:02d}"


refs = set()


def reference():
    while True:
        r = "BE" + "".join(random.choice(REF_ALPHABET) for _ in range(8))
        if r not in refs:
            refs.add(r)
            return r


users, user_roles, orgs, owners, verifs, branches, hours_rows = [], [], [], [], [], [], []
services, branch_services, staff, staff_branches, staff_services, staff_avail = [], [], [], [], [], []
appointments, reviews = [], []

uid = oid = bid = sid = stid = aid = rid = 0
email_counter = {}


def new_user(name, kind, roles, with_phone=True):
    global uid
    uid += 1
    first = slugify(name.split()[0])
    key = f"{kind}.{first}"
    email_counter[key] = email_counter.get(key, 0) + 1
    email = f"demo.{key}{email_counter[key]}@example.com"
    users.append((uid, name, email, phone() if with_phone else None))
    for r in roles:
        user_roles.append((uid, r))
    return uid


# Customers spread across cities
customers = []
for i in range(40):
    _, name = person()
    customers.append(new_user(name, "customer", [1]))

used_names = set()
for city, plan in CITY_PLAN.items():
    locs = CITIES[city]
    for idx, subcat in enumerate(plan):
        t = TEMPLATES[subcat]
        while True:
            prefix = random.choice(CITY_PREFIXES[city] + COMMON_PREFIXES)
            name = f"{prefix} {random.choice(t['suffixes'])}"
            if name not in used_names:
                used_names.add(name)
                break
        oid += 1
        _, owner_name = person()
        owner_id = new_user(owner_name, "owner", [1, 2])
        slug = f"{slugify(name)}-{slugify(city)}"
        created_days = random.randint(60, 300)
        conf = "APPROVAL" if subcat in (6,) and random.random() < 0.5 else "INSTANT"
        org_phone = phone()
        orgs.append(dict(id=oid, sub=subcat, name=name, slug=slug,
                         desc=f"{t['desc']} Serving {city} since {random.randint(2004, 2022)}.",
                         email=f"demo.contact.{slug[:40]}@example.com", phone=org_phone,
                         conf=conf, created=created_days))
        owners.append((oid, owner_id))
        verifs.append((oid, created_days))

        # Branches
        n_branches = 2 if city in MULTI_BRANCH and random.random() < 0.35 else 1
        org_branch_ids = []
        for b_i, loc in enumerate(random.sample(locs, n_branches)):
            bid += 1
            locality, pin, lat, lng = loc
            lat += random.uniform(-0.006, 0.006)
            lng += random.uniform(-0.006, 0.006)
            door = random.randint(1, 250)
            street = random.choice(["Main Road", "Gandhi Street", "Nehru Street", "2nd Cross Street", "Bazaar Street",
                                    "Kamarajar Salai", "Periyar Street", "Temple Street", "1st Main Road", "Car Street"])
            bname = "Main branch" if b_i == 0 else f"{locality} branch"
            branches.append((bid, oid, bname, f"No. {door}, {street}", f"Near {random.choice(['bus stop', 'post office', 'temple', 'park', 'bank', 'school'])}",
                             locality, city, pin, round(lat, 6), round(lng, 6), org_phone if b_i == 0 else phone()))
            for day, (o, c, bs, be) in HOURS[t["hours"]].items():
                hours_rows.append((bid, day, o, c, bs, be))
            org_branch_ids.append(bid)

        # Services
        org_service_ids = []
        requires_staff = t.get("requires_staff", True)
        for svc in t["services"]:
            sname, dur, price = svc[:3]
            ctype = svc[3] if len(svc) > 3 else "IN_PERSON"
            price = round(price * random.choice([0.9, 1.0, 1.0, 1.1, 1.2]) / 10) * 10
            sid += 1
            interval = 15 if dur <= 30 else 30
            buffer = 5 if t["hours"] in ("clinic", "salon") else 0
            services.append((sid, oid, sname, f"{sname} at {name}.", dur, price, ctype, requires_staff, buffer, interval))
            org_service_ids.append((sid, sname, price, dur, buffer))
            for b in org_branch_ids:
                branch_services.append((b, sid))

        # Staff
        org_staff = []
        if requires_staff:
            n_staff = random.randint(2, 3)
            specs = random.sample(t["specialisations"], min(n_staff, len(t["specialisations"])))
            for i in range(n_staff):
                gender, sname = person()
                title = t["staff_title"]
                if title == "Mr." and gender == "F":
                    title = "Ms."
                staff_user = new_user(sname, "staff", [1, 3], with_phone=False)
                stid += 1
                spec = specs[i % len(specs)]
                perms = '["CAN_CONFIRM","CAN_CANCEL"]' if i == 0 else '[]'
                staff.append((stid, oid, staff_user, f"{title} {sname}" if title in ("Dr.", "Mr.", "Ms.") else sname,
                              title, spec, perms))
                branch_for_staff = org_branch_ids if i == 0 else [org_branch_ids[i % len(org_branch_ids)]]
                for b in branch_for_staff:
                    staff_branches.append((stid, b))
                    for day, (o, c, _, _) in HOURS[t["hours"]].items():
                        if day == 7 and i > 0:
                            continue  # only the senior staff member works Sundays
                        staff_avail.append((stid, b, day, o, c))
                for s in org_service_ids:
                    staff_services.append((stid, s[0]))
                org_staff.append((stid, branch_for_staff[0]))

        # Past completed appointments + reviews
        n_appts = random.randint(3, 9)
        quality = random.choice([5, 5, 4, 4, 4, 3])
        for _ in range(n_appts):
            aid += 1
            svc_id, sname, price, dur, buffer = random.choice(org_service_ids)
            if org_staff:
                st, branch_id = random.choice(org_staff)
            else:
                st, branch_id = None, random.choice(org_branch_ids)
            days = random.randint(2, 50)
            open_h, close_h = HOURS[t["hours"]][1][:2]
            start_local_h = random.randint(int(open_h[:2]) + 1, max(int(open_h[:2]) + 1, int(close_h[:2]) - 2))
            if t["hours"] in ("clinic", "gym") and 11 <= start_local_h < 16:
                start_local_h = 10
            start_utc = to_utc(f"{start_local_h:02d}:{random.choice(['00', '30'])}")
            customer = random.choice(customers)
            fee = round(price * COMMISSION, 2)
            appointments.append(dict(id=aid, ref=reference(), customer=customer, org=oid, branch=branch_id, svc=svc_id,
                                     staff=st, days=days, start=start_utc, dur=dur, buffer=buffer, sname=sname,
                                     price=price, fee=fee, total=round(price + fee, 2)))
            if random.random() < 0.8:
                rating = max(2, min(5, quality + random.choice([0, 0, 0, 1, -1])))
                rid += 1
                reply = random.choice(OWNER_REPLIES) if random.random() < 0.4 else None
                reviews.append((rid, aid, oid, customer, st, rating, random.choice(REVIEWS[rating]), reply, max(days - 1, 1)))


# ---------------------------------------------------------------------------------------------------------
out = []
w = out.append
w("-- BookEasy demo data for Tamil Nadu (Chennai, Coimbatore, Madurai, Thoothukudi, Tiruchirappalli, Salem).")
w("-- GENERATED by backend/seed/generate_demo_data.py - edit the script, not this file.")
w("-- All businesses, people and reviews are fictional. Demo logins: demo.*@example.com / Demo@1234")
w("-- Safe to re-run: earlier demo rows are removed first. DO NOT run against production.")
w("SET NAMES utf8mb4;")
w("START TRANSACTION;")
w("")
w("-- Remove earlier demo data")
w("CREATE TEMPORARY TABLE demo_users AS SELECT id FROM users WHERE email LIKE 'demo.%@example.com';")
w("CREATE TEMPORARY TABLE demo_orgs AS SELECT o.id FROM organizations o JOIN organization_owners oo ON oo.organization_id = o.id "
  "WHERE oo.user_id IN (SELECT id FROM demo_users);")
w("DELETE FROM reviews WHERE organization_id IN (SELECT id FROM demo_orgs) OR customer_id IN (SELECT id FROM demo_users);")
w("DELETE FROM appointments WHERE organization_id IN (SELECT id FROM demo_orgs) OR customer_id IN (SELECT id FROM demo_users);")
w("DELETE FROM organizations WHERE id IN (SELECT id FROM demo_orgs);")
w("DELETE FROM users WHERE id IN (SELECT id FROM demo_users);")
w("DELETE FROM uploaded_files WHERE storage_key LIKE 'public/demo/%';")
w("DROP TEMPORARY TABLE demo_users;")
w("DROP TEMPORARY TABLE demo_orgs;")
w("")
w("-- ID offsets so demo rows never collide with real ones")
for var, table in [("u", "users"), ("o", "organizations"), ("b", "organization_branches"), ("s", "services"),
                   ("st", "staff_profiles"), ("a", "appointments"), ("r", "reviews")]:
    w(f"SET @{var} = (SELECT COALESCE(MAX(id), 0) FROM {table});")
w("")


def insert(table, cols, rows, chunk=200):
    for i in range(0, len(rows), chunk):
        w(f"INSERT INTO {table} ({', '.join(cols)}) VALUES")
        w(",\n".join("  (" + ", ".join(r) + ")" for r in rows[i:i + chunk]) + ";")
    w("")


insert("users", ["id", "full_name", "email", "phone_e164", "password_hash", "status", "email_verified_at",
                 "phone_verified_at", "preferred_language", "created_at", "updated_at"],
       [[f"@u + {i}", q(n), q(e), q(p), q(PASSWORD_HASH), "'ACTIVE'", ago(200), ago(200) if p else "NULL",
         q(random.choice(["en", "en", "ta"])), ago(200), ago(200)] for i, n, e, p in users])
insert("user_roles", ["user_id", "role_id"], [[f"@u + {u}", str(r)] for u, r in user_roles])

insert("organizations", ["id", "subcategory_id", "name", "slug", "description", "contact_email", "contact_phone",
                         "verification_status", "confirmation_mode", "submitted_at", "approved_at", "created_by",
                         "created_at", "updated_at"],
       [[f"@o + {o['id']}", str(o["sub"]), q(o["name"]), q(o["slug"]), q(o["desc"]), q(o["email"]), q(o["phone"]),
         "'APPROVED'", q(o["conf"]), ago(o["created"] - 1), ago(o["created"] - 3),
         f"@u + {owners[o['id'] - 1][1]}", ago(o["created"]), ago(o["created"] - 3)] for o in orgs])
insert("organization_owners", ["organization_id", "user_id", "role", "created_at"],
       [[f"@o + {o}", f"@u + {u}", "'PRIMARY'", "UTC_TIMESTAMP(6)"] for o, u in owners])
insert("organization_verifications", ["organization_id", "action", "from_status", "to_status", "reason", "created_at"],
       [[f"@o + {o}", "'SUBMITTED'", "'DRAFT'", "'PENDING'", "NULL", ago(d - 1)] for o, d in verifs]
       + [[f"@o + {o}", "'APPROVED'", "'PENDING'", "'APPROVED'", "'Documents verified (demo data)'", ago(d - 3)] for o, d in verifs])

insert("organization_branches", ["id", "organization_id", "name", "address_line1", "address_line2", "locality", "city",
                                 "state", "pincode", "latitude", "longitude", "phone", "created_at", "updated_at"],
       [[f"@b + {b}", f"@o + {o}", q(n), q(a1), q(a2), q(loc), q(c), "'Tamil Nadu'", q(pin), str(la), str(ln), q(p),
         "UTC_TIMESTAMP(6)", "UTC_TIMESTAMP(6)"] for b, o, n, a1, a2, loc, c, pin, la, ln, p in branches])
insert("working_hours", ["branch_id", "day_of_week", "opens_at", "closes_at", "break_start", "break_end"],
       [[f"@b + {b}", str(d), q(o), q(c), q(bs), q(be)] for b, d, o, c, bs, be in hours_rows])

insert("services", ["id", "organization_id", "name", "description", "duration_minutes", "price", "consultation_type",
                    "requires_staff", "buffer_minutes", "slot_interval_minutes", "created_at", "updated_at"],
       [[f"@s + {s}", f"@o + {o}", q(n), q(d), str(du), f"{p:.2f}", q(ct), "TRUE" if rs else "FALSE", str(bf), str(iv),
         "UTC_TIMESTAMP(6)", "UTC_TIMESTAMP(6)"] for s, o, n, d, du, p, ct, rs, bf, iv in services])
insert("branch_services", ["branch_id", "service_id"], [[f"@b + {b}", f"@s + {s}"] for b, s in branch_services])

insert("staff_profiles", ["id", "organization_id", "user_id", "display_name", "title", "specialisation", "permissions",
                          "status", "created_at", "updated_at"],
       [[f"@st + {s}", f"@o + {o}", f"@u + {u}", q(dn), q(t), q(sp), q(pm), "'ACTIVE'", "UTC_TIMESTAMP(6)",
         "UTC_TIMESTAMP(6)"] for s, o, u, dn, t, sp, pm in staff])
insert("staff_branches", ["staff_id", "branch_id"], [[f"@st + {s}", f"@b + {b}"] for s, b in staff_branches])
insert("staff_services", ["staff_id", "service_id"], [[f"@st + {s}", f"@s + {v}"] for s, v in staff_services])
insert("staff_availability", ["staff_id", "branch_id", "day_of_week", "start_time", "end_time"],
       [[f"@st + {s}", f"@b + {b}", str(d), q(o), q(c)] for s, b, d, o, c in staff_avail])


def appt_row(a):
    start = ago(a["days"], a["start"])
    return [f"@a + {a['id']}", q(a["ref"]), f"@u + {a['customer']}", f"@o + {a['org']}", f"@b + {a['branch']}",
            f"@s + {a['svc']}", f"@st + {a['staff']}" if a["staff"] else "NULL", start,
            f"{start} + INTERVAL {a['dur']} MINUTE", f"{start} + INTERVAL {a['dur'] + a['buffer']} MINUTE",
            "'COMPLETED'", q(a["sname"]), f"{a['price']:.2f}", f"{a['fee']:.2f}", f"{a['total']:.2f}",
            f"{start} - INTERVAL 5 MINUTE", f"{start} + INTERVAL {a['dur']} MINUTE",
            f"{start} - INTERVAL {random.randint(1, 6)} DAY", f"{start} + INTERVAL {a['dur']} MINUTE"]


insert("appointments", ["id", "reference", "customer_id", "organization_id", "branch_id", "service_id", "staff_id",
                        "starts_at", "ends_at", "blocked_until", "status", "service_name", "service_price",
                        "platform_fee", "total_amount", "checked_in_at", "completed_at", "created_at", "updated_at"],
       [appt_row(a) for a in appointments])
insert("reviews", ["id", "appointment_id", "organization_id", "customer_id", "staff_id", "rating", "comment",
                   "owner_reply", "replied_at", "created_at", "updated_at"],
       [[f"@r + {r}", f"@a + {a}", f"@o + {o}", f"@u + {c}", f"@st + {s}" if s else "NULL", str(rt), q(cm), q(rp),
         ago(max(d - 1, 0)) if rp else "NULL", ago(d), ago(d)] for r, a, o, c, s, rt, cm, rp, d in reviews])

# ----- photos: every business and service gets its own file row (owners can replace them independently) -----
SEED_DIR = Path(__file__).parent
manifest = json.loads((SEED_DIR / "photos" / "manifest.json").read_text(encoding="utf-8"))
photo_info = {}


def info(rel):
    if rel not in photo_info:
        data = (SEED_DIR / "photos" / rel).read_bytes()
        photo_info[rel] = (len(data), hashlib.sha256(data).hexdigest())
    return photo_info[rel]


file_rows, cover_of, image_of, copy_plan = [], {}, {}, {}
fid = 0
owner_of = dict(owners)
cover_counter = {}


def add_file(rel, key, purpose, owner, original):
    global fid
    fid += 1
    size, sha = info(rel)
    file_rows.append((fid, str(uuid.uuid5(uuid.NAMESPACE_URL, "bookeasy-demo:" + key)), key, original, size, sha,
                      purpose, owner))
    copy_plan[key] = "photos/" + rel
    return fid


for o in orgs:
    pool = manifest["covers"][str(o["sub"])]
    n = cover_counter.get(o["sub"], 0)
    cover_counter[o["sub"]] = n + 1
    rel = pool[n % len(pool)]
    cover_of[o["id"]] = add_file(rel, f"public/demo/cover-{o['slug']}.jpg", "ORGANIZATION_COVER",
                                 owner_of[o["id"]], "shop.jpg")
org_slug = {o["id"]: o["slug"] for o in orgs}
for sv in services:
    s_id, o_id, sname = sv[0], sv[1], sv[2]
    rel = manifest["services"].get(sname)
    if rel:
        image_of[s_id] = add_file(rel, f"public/demo/service-{org_slug[o_id]}-{slugify(sname)}.jpg", "SERVICE_IMAGE",
                                  owner_of[o_id], slugify(sname) + ".jpg")

w("SET @f = (SELECT COALESCE(MAX(id), 0) FROM uploaded_files);")
insert("uploaded_files", ["id", "public_id", "storage_key", "original_name", "content_type", "size_bytes", "sha256",
                          "visibility", "purpose", "owner_user_id", "created_at"],
       [[f"@f + {f}", q(pid), q(key), q(orig), "'image/jpeg'", str(size), q(sha), "'PUBLIC'", q(purpose),
         f"@u + {owner}", "UTC_TIMESTAMP(6)"] for f, pid, key, orig, size, sha, purpose, owner in file_rows])
for o_id, f in cover_of.items():
    w(f"UPDATE organizations SET cover_file_id = @f + {f} WHERE id = @o + {o_id};")
for s_id, f in image_of.items():
    w(f"UPDATE services SET image_file_id = @f + {f} WHERE id = @s + {s_id};")
w("")
(SEED_DIR / "demo-photo-files.json").write_text(json.dumps(copy_plan, indent=1), encoding="utf-8")

w("-- Recalculate each demo business's rating from its reviews")
w("UPDATE organizations o JOIN (SELECT organization_id, ROUND(AVG(rating), 2) avg_r, COUNT(*) n FROM reviews "
  "WHERE status = 'VISIBLE' GROUP BY organization_id) r ON r.organization_id = o.id "
  f"SET o.rating_avg = r.avg_r, o.review_count = r.n WHERE o.id > @o;")
w("")
w("COMMIT;")
w(f"-- {len(orgs)} businesses, {len(branches)} branches, {len(services)} services, {len(staff)} staff, "
  f"{len(customers)} customers, {len(appointments)} past bookings, {len(reviews)} reviews, {len(file_rows)} photos")

Path(__file__).with_name("demo-tamilnadu.sql").write_text("\n".join(out) + "\n", encoding="utf-8")
print(out[-1])

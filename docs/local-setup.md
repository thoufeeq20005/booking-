# Local Development Setup

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 17+ | `JAVA_HOME` **must** point at it. The build fails fast with a clear message on older JDKs. |
| Node.js | 22+ | Frontend tooling |
| MySQL | 8.0+ | Local service on port 3306 |

Maven is not required – use the wrapper (`./mvnw` / `mvnw.cmd`).

> **This machine:** the system `JAVA_HOME` currently points at JDK 8 (`C:\Program Files\Java\jdk1.8.0_301`).
> Either change it to `C:\Program Files\Java\jdk-17` in *System Properties → Environment Variables*,
> or set it per terminal:
> - PowerShell: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'`
> - Git Bash: `export JAVA_HOME="/c/Program Files/Java/jdk-17"`

## 1. Configure

```bash
cp .env.example .env
```

Minimum to fill in: `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (`openssl rand -base64 64`) and
`DATA_ENCRYPTION_KEY` (`openssl rand -base64 32`). With no email/SMS provider configured, the dev profile writes
verification links and OTP codes to the backend log (`[DEV EMAIL]`, `[DEV SMS]`), so every sign-in flow works offline.

Recommended: a dedicated MySQL user rather than `root`:

```sql
CREATE USER 'bookeasy_app'@'localhost' IDENTIFIED BY '<strong password>';
GRANT ALL PRIVILEGES ON bookeasy.* TO 'bookeasy_app'@'localhost';
GRANT ALL PRIVILEGES ON bookeasy_test.* TO 'bookeasy_app'@'localhost';
```

The databases `bookeasy` and `bookeasy_test` are created automatically on first run.
`bookeasy_test` is **wiped on every test run**.

## 2. Backend

```bash
cd backend
./mvnw spring-boot:run        # http://localhost:8080
./mvnw test                   # unit + MySQL integration tests
```

- Open the app at http://localhost:5173. Vite proxies `/api` to the backend, so cookies are same-origin.
- Health: http://localhost:8080/api/v1/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- Flyway applies migrations from `src/main/resources/db/migration` at startup.

## 3. Frontend

```bash
cd frontend
npm install
npm run dev                   # http://localhost:5173
npm test                      # Vitest
npm run build                 # type-check + production bundle
```

The frontend reads `VITE_*` variables from the root `.env`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `class, interface, or enum expected` / enforcer "needs JDK 17" | `JAVA_HOME` points at an old JDK |
| `Access denied for user` | check `DB_USERNAME` / `DB_PASSWORD` in `.env` |
| Browser shows CORS error | add the page origin to `CORS_ALLOWED_ORIGINS` |
| `Found non-empty schema(s) without schema history table` | `DB_NAME` points at a database not created by Flyway (e.g. the old `booking_db`); use a fresh name |

## Demo data (Tamil Nadu)

To fill an empty dev database with sample businesses in Chennai, Coimbatore, Madurai, Thoothukudi,
Tiruchirappalli and Salem (clinics, dentists, salons, spas, tuition, gyms, yoga, with services, staff,
opening hours, past bookings and reviews):

```bash
mysql -uroot -p --default-character-set=utf8mb4 bookeasy < backend/seed/demo-tamilnadu.sql
python backend/seed/install_demo_photos.py   # copies the shop and service photos into file storage
```

Start the backend once first so the database schema (including the photo columns) exists.
The photos are CC0 stock photos from StockSnap (via Openverse); sources are listed in
`backend/seed/photos/CREDITS.json`.

All names, phone numbers and reviews are fictional; localities, PIN codes and map coordinates are real.
Every demo login is `demo.<role>.<name><n>@example.com` with password `Demo@1234`. Re-running the file
replaces the earlier demo rows. To change the data, edit `backend/seed/generate_demo_data.py` and run it
to regenerate the SQL. Never run it against production.

### Movies and dining demo

Load this after `demo-tamilnadu.sql` to add 8 fictional movies, 8 cinemas (16 screens with seat maps and
four shows a day) and 14 restaurants and cafés with menus, offers, café/premium cover charges and reviews:

```bash
mysql -uroot -p --default-character-set=utf8mb4 bookeasy < backend/seed/demo-movies-dining.sql
mysql -uroot -p --default-character-set=utf8mb4 bookeasy < backend/seed/demo-dining-more.sql   # more cafés and premium restaurants
mysql -uroot -p --default-character-set=utf8mb4 bookeasy < backend/seed/demo-education-fitness.sql   # 5 education + 5 fitness places per city
python backend/seed/install_demo_photos.py   # copies all demo photos, including posters and venue covers
```

`demo-dining-more.sql` only replaces its own restaurants (owners `demo.resto.*@example.com`), so it can be loaded
again without touching cinema bookings. `generate_more_dining.py` lists more cafés, premium and family restaurants than
it includes: a restaurant is added once its CC0 cover photo exists (`python backend/seed/generate_more_dining.py
--fetch-photos`, then run it again without the flag). `demo-education-fitness.sql` (from `generate_more_services.py`) adds
fictional coaching centres, tuition classes, music and dance schools, gyms and yoga studios in each demo city, with
courses, fees, trainers, weekly timings and reviews; owners are `demo.svc.*@example.com`. It works the same way.

Showtimes cover the 21 days after you load the file. Load it again for fresh shows (this also removes
bookings made at the demo cinemas and restaurants). Demo owners sign in as `demo.venue.<name><n>@example.com`
with password `Demo@1234`. Movie titles, plots, cast and venues are invented; posters without a photo are drawn from two
colours, so no artwork is copied. Posters, cinema and restaurant covers are CC0 photos from Openverse
(sources in `backend/seed/photos/CREDITS.json`, fetched once by `fetch_venue_photos.py`); the title is set over
the photo by the app. Admins can upload a poster for any movie on Admin → Movies. To change the data, edit `backend/seed/generate_movies_dining.py` and run it.

# Security overview

What protects BookEasy and where it lives. Reviewed at the end of Phase 11.

## Identity & sessions
- BCrypt (cost 12) passwords; lockout after 5 failures for 15 minutes; per-IP and per-account rate limits on sign-in,
  OTP, reset and bootstrap (stored in MySQL, so they hold across instances).
- Short-lived HS512 access tokens in memory only; rotating `HttpOnly; Secure; SameSite=Strict` refresh cookie scoped
  to `/api/v1/auth`, hashed at rest, with reuse detection that revokes the whole family.
- Roles and status are re-read on every request, so suspensions, role changes and logout-all apply immediately.
- TOTP 2FA with AES-GCM encrypted secrets and replay protection; admin reset requires a recorded reason and is audited.

## Authorization & tenant isolation
- URL gates per role (`/admin`, `/owner`, `/staff`, `/customer`), deny-by-default for everything else.
- Every owner call resolves the organization through `organization_owners`; other businesses answer **404** so ids
  can't be probed. Branch, document, service, staff, holiday and booking ids are always looked up inside the
  organization in the path. Staff actions check the person's own profile and permissions (`CAN_CONFIRM`,
  `CAN_CANCEL`, `CAN_EDIT_HOURS`). Customers only ever see their own bookings, family members and requests.
- Tests: `TenantIsolationIT`, `TeamIT`, `BookingIT`, `RoleAccessIT`.

## Money
- Amounts are `DECIMAL(12,2)` / paise `BIGINT`, rounded HALF_UP on the server; the browser never decides a price.
- A booking is confirmed only after the Checkout signature (HMAC-SHA256 with the key secret) or a signed webhook;
  webhooks are de-duplicated by event id. Late payments are refunded automatically.
- Double booking is impossible at the database level (lock row per 5-minute unit, primary key) — tested with
  concurrent requests.

## Files
- Type detected from magic bytes, never from name or Content-Type; generated storage keys; size limits per
  requirement; private documents only through ownership-checked endpoints with `no-store` and a sandbox CSP.

## Web
- API: stateless, CSRF not applicable to Bearer tokens; cookie endpoints require SameSite=Strict + a custom header;
  CORS allow-list; `frame-ancestors 'none'`, nosniff, strict referrer; errors never include stack traces; Swagger off
  in prod.
- SPA (Vercel): strict CSP without inline scripts (theme bootstrap is a static file), HSTS, X-Frame-Options DENY,
  permissions policy limiting geolocation/payment to the needed origins. Owner-entered websites are stored only as
  `http(s)` URLs and rendered with `rel="noopener noreferrer nofollow"`.

## Abuse limits (per window)
Sign-in, OTP, reset (Phase 2) · bookings 20/h per customer · payment orders 30/h per customer · staff invitations
50/day per business · review reports 20/day per user · support requests 5/h per user · business applications 3 open
per person.

## Audit
Append-only `audit_logs` for approvals, suspensions, document reviews, catalogue changes, staff permission changes,
invitations, business cancellations/declines/no-shows, review moderation, settlements, payout accounts and 2FA resets;
viewable and filterable in Admin → Audit log.

## Data protection
Retention window for customer history (`retention.booking_history_years`), nightly anonymisation of old personal
notes, deletion of old notifications/emails/webhook ids; uploads never public unless they are logos.

## Known gaps / next steps
- Public search and slot endpoints are read-only and not rate-limited in the app; rely on the platform (Render /
  Vercel) or add an edge rate limit if scraping becomes a problem.
- `DATA_ENCRYPTION_KEY` rotation is not automated.
- No malware scanning of uploads (only PDF/JPEG/PNG/WebP are accepted, served with a sandbox CSP).

# Deployment

## Topology

```
Browser ──HTTPS──▶ Vercel (SPA, CSP + security headers) ── rewrite /api/* ──▶ Render (Spring Boot, Docker)
                                                                               ├── Managed MySQL 8 (TLS)
                                                                               ├── Persistent disk /var/data (uploads)
                                                                               ├── Razorpay (orders, refunds, Route)  ◀── signed webhooks
                                                                               └── SMTP / MSG91
```

The SPA and API share one origin (the Vercel domain). The browser never talks to Render directly, so the refresh
cookie stays first-party (`SameSite=Strict`) and no CORS is involved for the app itself.

## 1. Database (managed MySQL 8)

1. Create a MySQL 8.0 instance in or near Mumbai/Singapore with TLS required and automated daily backups (keep ≥ 7 days,
   test a restore once before launch).
2. Create the schema `bookeasy` with `utf8mb4` / `utf8mb4_unicode_ci` and a dedicated user with rights on that schema
   only (no SUPER). Flyway creates every table on first start (`V1`…`V10`); never edit an applied migration.
3. Time zone: the app works in UTC on its own connection; no server setting is required.

## 2. Backend on Render

1. New → Blueprint → this repository; `render.yaml` creates `bookeasy-api` (Docker, `starter` plan, Singapore,
   5 GB disk at `/var/data`, readiness check `/actuator/health/readiness`).
2. Fill every `sync: false` variable in the dashboard:

| Variable | Value |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | managed MySQL |
| `JWT_SECRET` | `openssl rand -base64 64` |
| `DATA_ENCRYPTION_KEY` | `openssl rand -base64 32` — **back it up**; losing it invalidates 2FA secrets |
| `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` | `https://<your-vercel-domain>` |
| `BACKEND_URL` | the Render URL |
| `GOOGLE_OAUTH_CLIENT_ID` | see [google-sign-in.md](integrations/google-sign-in.md) |
| `EMAIL_PROVIDER`, `SMTP_*`, `EMAIL_FROM_ADDRESS` | see [email.md](integrations/email.md) |
| `SMS_PROVIDER`, `SMS_API_KEY`, `SMS_OTP_TEMPLATE_ID` | see [sms-otp.md](integrations/sms-otp.md) |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | see [razorpay.md](integrations/razorpay.md); leave empty for pay-at-venue |
| `RAZORPAY_ROUTE_ENABLED` | `true` only after Razorpay activates Route |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_TOKEN` | once, then delete — [admin-setup.md](admin-setup.md) |

3. The `prod` profile forces Secure cookies, requires DB TLS, disables Swagger, turns off log-only notifications and
   shuts down gracefully (25 s) on redeploys.
4. **Client IP:** `CLIENT_IP_XFF_INDEX=2` matches Vercel → Render. Verify after the first deploy: set `LOG_LEVEL=DEBUG`,
   make one request, read the `Client IP inputs:` log line, then set the level back. A wrong index makes every user
   share one rate-limit bucket.

### Several instances

All background work is safe to run on more than one instance: payment expiry and refund/outbox dispatch lock rows
with `FOR UPDATE SKIP LOCKED`, reminders are de-duplicated by a primary key, settlement re-checks rows under lock and
retention is idempotent. Uploaded files live on one persistent disk, so **scale vertically until `FileStorage` is
moved to S3-compatible storage** (one class to add).

## 3. Frontend on Vercel

1. Import the repo with root `frontend/` (framework Vite, build `npm run build`, output `dist`).
2. Edit `frontend/vercel.json`: set the `/api/:path*` rewrite destination to your Render URL (Vercel can't read env
   vars there).
3. Environment: `VITE_GOOGLE_OAUTH_CLIENT_ID` (same client id), optionally `VITE_GOOGLE_MAPS_API_KEY`. Never put secrets
   in `VITE_*`.
4. Headers already configured: CSP (self + Razorpay Checkout + Google Sign-In + Google Fonts), HSTS, `X-Frame-Options:
   DENY`, `nosniff`, referrer policy, permissions policy. If you add a third-party script, extend the CSP.

## 4. Razorpay

Create the webhook (`/api/v1/public/webhooks/razorpay`) with the events listed in
[razorpay.md](integrations/razorpay.md). Run a full test-mode booking → pay → cancel → refund before switching to live
keys.

## 5. After deploy

- `GET /api/v1/health` → `UP`; `/actuator/health/readiness` → `UP`.
- Create the first Super Admin, then remove the bootstrap variables and redeploy.
- Walk through [production-checklist.md](production-checklist.md).

## Scheduled jobs (all in the API process, India time)

| Job | When |
|---|---|
| Expire unpaid bookings | every minute |
| Notification outbox dispatch | every 30 s (and right after each commit) |
| Refund dispatch (retries) | every 5 min (and right after each commit) |
| Appointment reminders | every 5 min |
| Settlements | 02:00 daily |
| Data retention | 03:30 daily |
| Auth token cleanup | 03:30 daily |

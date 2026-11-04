# Production checklist

Tick every item before announcing the launch.

## Configuration
- [ ] `SPRING_PROFILES_ACTIVE=prod`; all secrets set in Render, none in git; `.env` not deployed
- [ ] `JWT_SECRET` ≥ 64 random bytes; `DATA_ENCRYPTION_KEY` 32 random bytes, stored in a password manager
- [ ] `FRONTEND_URL` / `CORS_ALLOWED_ORIGINS` = the real HTTPS domain; Vercel rewrite points at the Render URL
- [ ] DB over TLS (`DB_SSL_MODE=REQUIRED` default in prod), dedicated least-privilege user, daily backups, restore tested
- [ ] Persistent disk mounted at `/var/data`, `FILE_STORAGE_PATH=/var/data/uploads`
- [ ] `CLIENT_IP_XFF_INDEX` verified from logs after the first deploy
- [ ] Bootstrap Super Admin created; `BOOTSTRAP_ADMIN_*` removed; admin has 2FA on

## Providers
- [ ] Email: SPF, DKIM and DMARC for the sending domain; test verification, reset, booking and reminder emails
- [ ] SMS: DLT entity + OTP template approved; test sign-in OTP on a real number
- [ ] Google Sign-In: production origin added to the OAuth client
- [ ] Razorpay: KYC complete, live keys, live webhook with secret and events; test-mode end-to-end run done
      (book → pay → confirm → cancel → refund processed); Route activated before setting `RAZORPAY_ROUTE_ENABLED=true`
- [ ] Support email/phone and hours filled in (`support_settings`) — shown in the footer

## Content
- [ ] Categories, subcategories and document requirements reviewed in Admin → Categories
- [ ] Platform settings reviewed: commission %, refund windows, reschedule limit, reminder offsets, retention years
- [ ] Terms of service, privacy policy and refund policy pages published (legal review — not part of this codebase)

## Operations
- [ ] Health checks green: `/api/v1/health`, `/actuator/health/readiness`
- [ ] Log retention / alerts on Render (5xx rate, restart loops); uptime monitor on the health URL
- [ ] Someone owns the review queue, support queue and manual settlements (Admin → Businesses / Support / Settlements)
- [ ] Incident contacts and Razorpay dashboard access shared with at least two people

## Verification run (staging or production with test keys)
- [ ] Register customer → verify email → search → book → pay → QR shown
- [ ] Owner sees the booking, checks the customer in, completes it; customer reviews it
- [ ] Customer cancels another paid booking → refund requested → webhook marks it processed
- [ ] Business registration → documents → admin approval → listed in search
- [ ] All six languages render (switch in the header); dark mode; phone-width layout

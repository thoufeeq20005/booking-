# BookEasy Development Checklist

Legend: `[x]` done and tested · `[ ]` not started · `[~]` in progress

## Phase 1 – Project foundation ✅
- [x] Inspect existing project (Node/Express prototype → moved to `legacy/`, git-ignored)
- [x] Monorepo layout: `backend/`, `frontend/`, `docs/`
- [x] Spring Boot 3.5.14 + Maven wrapper, JDK 17 enforced
- [x] MySQL connection from root `.env`, UTC everywhere
- [x] Flyway V1: roles, platform settings, support settings, audit log
- [x] Global error envelope + exception handler, JSON 401/403
- [x] Spring Security deny-by-default, CORS allow-list, security headers
- [x] OpenAPI / Swagger UI (off in prod)
- [x] Health: `/api/v1/health` (DB check) + `/actuator/health/readiness`
- [x] Public config API (currency, 5% commission, languages, support details from DB)
- [x] Audit service (must run inside caller transaction)
- [x] React 18 + TS + Vite + Tailwind design tokens (violet, light/dark)
- [x] Theme toggle persisted, no flash on load
- [x] i18n: en, ta, hi, ml, te, kn with key-parity test; Noto Sans Indic fonts
- [x] API client with typed envelope/errors; TanStack Query
- [x] Public layout (header, mobile menu, footer with live support info), Home, Status, 404, error boundary
- [x] INR / Indian number / IST date formatting helpers
- [x] Vercel config (SPA rewrites, headers), Render blueprint + Dockerfile + persistent disk
- [x] Tests: backend 12 (unit + MySQL integration), frontend 32
- [x] 0 npm audit vulnerabilities

## Phase 2 – Authentication ✅
- [x] Flyway V2: users, user_roles, refresh_tokens, email_verifications, password_reset_tokens, otp_verifications, oauth_accounts, rate_limit_counters
- [x] Email + password registration (BCrypt 12, password policy), email verification (24 h, single use)
- [x] Login with per-IP/per-email rate limits and 5-strike / 15-min lockout, no account enumeration
- [x] Access JWT (HS512, 15 min, revocable via token_version) + rotating HttpOnly refresh cookie with reuse detection and multi-tab grace
- [x] CSRF defence for cookie endpoints (SameSite=Strict + required header); same-origin API via Vite proxy / Vercel rewrite
- [x] Mobile OTP sign-in/sign-up (HMAC-hashed codes, expiry, attempts, cooldown, hourly cap); verify phone on account
- [x] Google Sign-In (ID-token verification, safe account linking)
- [x] Forgot/reset password (30 min, signs out everywhere); signed-in password change/set
- [x] TOTP 2FA (RFC 6238, AES-GCM encrypted secrets, replay protection, challenge flow for every sign-in method)
- [x] Logout, logout-all; suspension takes effect immediately
- [x] RBAC URL gates for all four roles; admin user search + suspend/reactivate (audited)
- [x] Secure Super Admin bootstrap (one-time token, no default password)
- [x] Email (SMTP) and SMS (MSG91) adapters; dev log delivery; honest failure in prod
- [x] Localised emails in 6 languages; 123 new UI strings in 6 languages
- [x] Frontend: auth context, sign in (email / OTP / Google / 2FA), register, verify email, forgot/reset, account security page, route guard, open-redirect protection
- [x] Tests: backend 78 (all four roles, unauthorised access, token rotation/reuse, lockout, OTP limits, Google forgery, 2FA replay, RFC vectors), frontend 46; real-browser check of HttpOnly cookie
- [ ] Not yet: 2FA recovery codes; admin action to reset a user's 2FA (Phase 4)

## Phase 3 – Organizations & verification ✅
- [x] Flyway V3: uploaded_files, categories, subcategories, category_document_requirements, organizations, organization_owners, organization_branches (generated `POINT SRID 4326` + spatial index), working_hours, holidays, organization_documents, organization_verifications; starter catalogue in 6 languages
- [x] Dynamic categories/subcategories + document requirements: admin CRUD, deactivate-not-delete when in use, audited
- [x] File storage abstraction (`FileStorage` / local disk), magic-byte type detection, generated keys, SHA-256, rollback-safe cleanup; private documents streamed only through owner/admin endpoints, public logos by random id
- [x] Owner business registration wizard: details → branches & hours & holidays → documents → review checklist → submit (customer gains BUSINESS_OWNER immediately)
- [x] Edit rules per status (identity locked after approval, everything locked while pending/suspended), row locks on every state change
- [x] Admin review queue (oldest first), per-document accept/reject with notes, approve/reject/suspend/reinstate with reasons, self-review blocked, audit + verification history
- [x] Notification hook: `OrganizationStatusChanged` event → owner email after commit, in 6 languages
- [x] Frontend: register business, my businesses, setup tabs, admin queue/review, admin categories; 150+ new strings in 6 languages
- [x] Tests: backend 103 (approval/rejection/resubmission, tenant isolation for every owner endpoint, private file access, upload spoofing/size, spatial column, hours/holiday rules, category admin), frontend 58; live upload/download smoke test through the Vite proxy
- [ ] Not yet: map picker & geocoding (Phase 6), gallery images, co-owners, payout/bank details (Phase 8), org-wide holidays (set per branch for now)

## Phase 4 – Dashboards
- [ ] Customer, Owner, Staff, Admin shells and overview pages on real data

## Phase 5 – Services & availability
- [ ] Services, staff invites/permissions, availability, leaves
- [ ] Slot computation + tests for conflicts

## Phase 6 – Location & discovery
- [ ] GPS/manual location, Google Maps, nearby search (spatial index), filters, sorting, suggestions, map sync

## Phase 7 – Booking
- [ ] Booking wizard (category workflows), instant/approval modes, state machine
- [ ] Slot-lock double-booking guard + concurrency test
- [ ] Booking reference, QR token, check-in, reschedule, cancel with refund preview

## Phase 8 – Payments
- [ ] Razorpay orders, signature verification, webhooks (idempotent), refunds
- [ ] Commission records, Route eligibility & settlements, receipts/invoices

## Phase 9 – Reviews & notifications
- [ ] Reviews (completed only), replies, reports, moderation
- [ ] Outbox + in-app/email/SMS/WhatsApp adapters, reminders, preferences

## Phase 10 – Additional features
- [ ] Family members, favourites, comparison, addresses, support, retention jobs

## Phase 11 – Production readiness
- [ ] Security review, integration/performance tests, deployment guides, production checklist

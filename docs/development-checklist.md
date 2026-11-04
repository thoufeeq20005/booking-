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
- [x] Admin reset of a user's 2FA (Phase 4)
- [ ] Not yet: 2FA recovery codes

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

## Phase 4 – Dashboards ✅
- [x] One sidebar shell for every area (`/customer`, `/owner`, `/staff`, `/admin`), area switcher for multi-role users, `/dashboard` sends each person to their most powerful role; sign-in lands there
- [x] Customer overview: account readiness checklist, bookings empty state (bookings arrive in Phase 7), business CTA
- [x] Owner overview (`GET /owner/dashboard`): status counts, per-business progress (branches, hours, required documents, next step), upcoming holidays across branches
- [x] Staff overview: empty state until invitations (Phase 5)
- [x] Admin overview (`GET /admin/dashboard`): review queue (count, oldest, documents to review), users (total, new in 7 days, suspended, by role), businesses by status, recent activity
- [x] Admin users page: search/filter, suspend/reactivate with reason, reset two-step verification (reason required, signs out everywhere, audited, security email in 6 languages)
- [x] Audit log viewer (`GET /admin/audit-logs`, filter by action/entity/actor/time, details shown readably)
- [x] Tests: backend 107 (owner dashboard isolation, admin counts, audit filters, 2FA reset), frontend 71
- [ ] Not on real data yet (nothing to show until later phases): bookings, revenue, staff schedules, reviews

## Phase 5 – Services & availability ✅
- [x] Flyway V4: services (duration, ₹ price, buffer, slot interval, booking window, needs-staff), branch_services (per-branch price), staff_profiles (+ branches, services, permissions), staff_availability (split shifts), staff_leaves, staff_invitations (hashed tokens)
- [x] Owner services CRUD; staff invite by email (7-day link, resend, withdraw), "add myself" for solo practitioners, assignments, permissions (confirm / cancel / edit own hours), activate/deactivate — all tenant-isolated and audited
- [x] Invitation accept: must be signed in with the invited email; accepting verifies that email and grants STAFF
- [x] Staff area: workplaces, services, availability, leave; staff with CAN_EDIT_HOURS edit their own availability/leave
- [x] Slot engine (`SlotCalculator`): branch hours ∩ staff availability − break − holiday − leave − busy time (+ buffer), clock-aligned starts, minimum notice, booking window, merges staff per start time; `BusyTimeProvider` hook for Phase 7 bookings
- [x] Owner slot preview (any status) and public slots (approved businesses only)
- [x] Tests: backend 121 (9 slot-engine unit tests incl. conflicts/buffer/leave/notice, end-to-end slots, invitations, isolation), frontend 75
- [ ] Service groupings (categories within a business) dropped for now; deleting a service will become deactivate-only once bookings exist (Phase 7)

## Phase 6 – Location & discovery ✅
- [x] Nearby search (`GET /public/search`): bounding box on lat/lng index, then `ST_Distance_Sphere` on the generated POINT; filters (category, subcategory, text across business/subcategory/service names, min rating), sort by distance/rating/price, "from" price per branch
- [x] Suggestions (`GET /public/suggest`) across business names and subcategory names in every language
- [x] Public business profile (`GET /public/organizations/{slug}`): branches + hours, services with per-branch prices, active staff (approved businesses only)
- [x] Frontend: search page (GPS or city presets, remembered locally), category chips, business page, "Open in Google Maps" links
- [x] Tests: backend 123, frontend 77
- [ ] Embedded interactive map and address geocoding need `VITE_GOOGLE_MAPS_API_KEY` / `GOOGLE_MAPS_SERVER_API_KEY`; deferred until keys exist (links work without keys)

## Phase 7 – Booking ✅
- [x] Flyway V6: appointments (reference, snapshot of service name/price, platform fee, total, status, buffer-aware `blocked_until`), appointment_slot_locks, refund/reschedule settings
- [x] Booking wizard: service → branch → staff (or anyone) → day → time → note; prices and fee from the server; instant vs approval businesses
- [x] State machine: PENDING_PAYMENT → PENDING_APPROVAL/CONFIRMED → COMPLETED/NO_SHOW; CANCELLED, DECLINED, EXPIRED
- [x] Double-booking guard: one lock row per 5-minute unit per resource with a primary key; concurrency test (6 parallel requests → exactly 1 booking); slot engine now subtracts real bookings
- [x] Reference (BE + 8 chars), HMAC-signed QR pass, owner check-in by pass, staff/owner confirm, decline, cancel (reasons, audited), complete, no-show with permission checks
- [x] Customer cancel with refund preview (100% ≥24 h, 50% ≥2 h, else 0; platform fee kept), reschedule (≥2 h ahead, max 2)
- [x] Customer bookings list/detail, owner appointments day view with pass check-in, staff "today" list
- [x] Tests: backend 127, frontend 80
- [ ] Customers book only for themselves until family members (Phase 10); camera QR scanning uses any scanner app + paste

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

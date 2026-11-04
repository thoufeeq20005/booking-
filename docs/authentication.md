# Authentication & Access Control

## Sign-in methods

| Method | Endpoints | Notes |
|---|---|---|
| Email + password | `POST /auth/register`, `POST /auth/login` | BCrypt cost 12. Policy: ≥ 8 chars, a letter and a digit, ≤ 72 bytes (BCrypt limit). Registration signs in immediately and emails a verification link. |
| Mobile + SMS OTP | `POST /auth/otp/send`, `POST /auth/otp/login` | Indian mobiles only (normalised to `+91XXXXXXXXXX`). A new number becomes a customer account after the user supplies a name (`FULL_NAME_REQUIRED` is returned first, without consuming the code). |
| Google | `POST /auth/google` | Frontend uses Google Identity Services; the backend verifies the ID token signature (Google JWKS), issuer, audience = `GOOGLE_OAUTH_CLIENT_ID`, and expiry. It links to an existing account only if Google says the email is verified. |
| Two-step (TOTP) | `POST /auth/login/two-factor` | Any sign-in method returns `TWO_FACTOR_REQUIRED` + a 5-minute challenge token when 2FA is on. RFC 6238, ±30 s drift, replay of a used code is rejected. |

Account self-service (signed in, `/api/v1/account/**`): profile, resend verification, add/verify mobile, change/set password, 2FA setup/enable/disable, sign out everywhere.

Recovery: `POST /auth/password/forgot` (always 202, so it can't be used to discover accounts) → emailed link (30 min, single use) → `POST /auth/password/reset`. A reset signs out every session.

## Tokens and sessions

```
Browser ──(Authorization: Bearer <access JWT, 15 min, memory only>)──▶ API
Browser ──(Cookie be_rt: opaque refresh token, 14 days, HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth)──▶ /auth/refresh
```

- **Access token**: HS512 JWT with `sub`, `typ=access`, `roles`, `tv` (token version). Every request re-reads the user, so suspension, role changes and logout-all apply immediately.
- **Refresh token**: 256-bit random, stored as SHA-256 only. Each refresh rotates it. Presenting an already-rotated token more than 10 s later is treated as theft and revokes the whole token family. Within 10 s it is refused but harmless, to tolerate two tabs racing. The SPA also serialises refreshes across tabs with the Web Locks API.
- **CSRF**: access tokens never travel in cookies. The refresh/logout endpoints need the cookie **and** the `X-BookEasy-Client` header. A cross-site page can't add that header without a CORS preflight, and the allow-list rejects it.
- **Same origin**: the SPA calls `/api/v1/...` on its own origin (Vite proxy in dev, Vercel rewrite in prod). That keeps the cookie first-party and `SameSite=Strict` works.
- **Logout**: `POST /auth/logout` revokes this device's token family. `POST /account/logout-all` bumps `token_version` and revokes every refresh token.

## Abuse protection

| Control | Limit |
|---|---|
| Login per IP / per email | 30 / 10 per 10 min |
| Account lockout | 5 wrong passwords → locked 15 min (same generic error for unknown email; constant-time dummy hash check) |
| Registration per IP | 10 per hour |
| OTP send | 60 s cooldown and 5 per hour per number; 20 per hour per IP |
| OTP verify | 5 attempts per code, 5-minute expiry, codes stored as keyed HMAC |
| 2FA verify | 5 per 10 min per user |
| Password reset | 3 per hour per email, 10 per hour per IP |
| Resend verification | 3 per hour per user |
| Super Admin bootstrap | 5 per hour per IP |

Counters live in MySQL (`rate_limit_counters`) so limits hold across instances. The client IP comes from `ClientIpResolver`, which trusts `X-Forwarded-For` only from the right-hand side (`CLIENT_IP_XFF_INDEX`). A daily job (03:30 IST) purges expired tokens, codes and counters.

## Roles and permissions (enforced on the server)

| Path prefix | Allowed roles |
|---|---|
| `/api/v1/health`, `/api/v1/public/**`, `/api/v1/auth/**` | anyone |
| `/api/v1/account/**` and any other path | any signed-in user |
| `/api/v1/customer/**` | `CUSTOMER` |
| `/api/v1/owner/**` | `BUSINESS_OWNER` |
| `/api/v1/staff/**` | `STAFF` |
| `/api/v1/admin/**` | `SUPER_ADMIN` |

URL rules are the coarse gate. Services additionally check resource ownership (owner owns the organization, staff is assigned to the branch, customer owns the booking) as each module lands. Self-service sign-up always creates `CUSTOMER`. `BUSINESS_OWNER` is granted when a signed-in user starts a business application (`POST /business/organizations`, see [organizations.md](organizations.md)). `STAFF` comes from owner invitations (Phase 5). `SUPER_ADMIN` comes from the bootstrap procedure ([admin-setup.md](admin-setup.md)).

Admin user management (Phase 2): `GET /admin/users?q=&status=&role=&page=&size=` and `PATCH /admin/users/{id}/status` (suspend/reactivate, audited, self-change refused, suspension signs the user out everywhere).

## Known limitations (tracked)

- No 2FA recovery codes yet. A user who loses their authenticator needs support to clear 2FA (admin action to be added in Phase 4).
- Changing `DATA_ENCRYPTION_KEY` invalidates enrolled authenticators. Key rotation is not implemented.
- Email verification isn't required to sign in. Booking (Phase 7) will require a verified email or mobile.

# BookEasy

Location-based, multi-category appointment booking for India – hospitals, clinics, salons, colleges, gyms and more.
Customers discover verified businesses nearby, book a real free slot, pay securely (service fee + platform fee) and check
in with a QR pass. Businesses register, get verified, manage branches, services, staff and availability, and get paid.
Everything is available in English, தமிழ், हिन्दी, മലയാളം, తెలుగు and ಕನ್ನಡ.

| Part | Stack | Hosting |
|---|---|---|
| `frontend/` | React 18, TypeScript, Vite, Tailwind, React Router 7, TanStack Query, i18next | Vercel |
| `backend/` | Java 17, Spring Boot 3.5, Spring Security, JPA, Flyway | Render (Docker + persistent disk) |
| Database | MySQL 8 (spatial index for nearby search) | Managed MySQL |
| Payments | Razorpay Checkout, webhooks, refunds, Route | |

## Features

- **Customers:** email / mobile OTP / Google sign-in with optional 2FA · nearby search with filters, sorting and
  comparison · business pages with hours, services, staff and reviews · booking wizard (for yourself or a family
  member) · online payment, receipts, QR pass · reschedule and cancel with refund preview · reminders and an inbox ·
  favourites, saved places, support requests.
- **Businesses:** registration with documents and verification · branches, opening hours, holidays · services with
  per-branch prices · staff invitations, permissions, availability and leave · instant or approval-based bookings ·
  day view with QR check-in · reviews and replies · earnings and payouts.
- **Super Admins:** verification queue, categories and document rules, users (suspend, 2FA reset), review moderation,
  settlements, support queue, audit log, data retention.

## Quick start

```bash
cp .env.example .env          # fill in DB credentials
cd backend && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

Full instructions: [docs/local-setup.md](docs/local-setup.md). Tests: `./mvnw test` (needs local MySQL) and `npm test`.

## Documentation

- [Architecture](docs/architecture.md) · [Database design](docs/database-schema.md) · [Security overview](docs/security.md)
- [Authentication & roles](docs/authentication.md) · [Organizations & verification](docs/organizations.md) · [First Super Admin](docs/admin-setup.md)
- Integrations: [email](docs/integrations/email.md) · [SMS/OTP](docs/integrations/sms-otp.md) · [Google sign-in](docs/integrations/google-sign-in.md) · [Razorpay](docs/integrations/razorpay.md)
- [Deployment](docs/deployment.md) · [Production checklist](docs/production-checklist.md)
- [Development checklist](docs/development-checklist.md)
- API reference: Swagger UI at `/swagger-ui.html` (dev)

## Status

All 11 phases are built. See the [checklist](docs/development-checklist.md) for details and the remaining items that
need external accounts (Google Maps keys, SMS/WhatsApp templates, GST invoicing, S3 storage for multi-instance).

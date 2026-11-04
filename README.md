# BookEasy

Location-based, multi-category appointment booking for India – hospitals, clinics, salons, colleges, gyms and more.
Customers discover verified organizations nearby, book a slot, pay securely (service fee + 5% platform fee) and check in with a QR pass.

| Part | Stack | Hosting |
|---|---|---|
| `frontend/` | React 18, TypeScript, Vite, Tailwind, React Router 7, TanStack Query, i18next | Vercel |
| `backend/` | Java 17, Spring Boot 3.5, Spring Security, JPA, Flyway | Render (Docker + persistent disk) |
| Database | MySQL 8 | Managed MySQL |

## Quick start

```bash
cp .env.example .env          # fill in DB credentials
cd backend && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

Full instructions: [docs/local-setup.md](docs/local-setup.md)

## Documentation

- [Architecture](docs/architecture.md)
- [Database design](docs/database-schema.md)
- [Authentication & roles](docs/authentication.md)
- [First Super Admin](docs/admin-setup.md)
- Integrations: [email](docs/integrations/email.md) · [SMS/OTP](docs/integrations/sms-otp.md) · [Google sign-in](docs/integrations/google-sign-in.md)
- [Deployment notes](docs/deployment.md)
- [Development checklist](docs/development-checklist.md)
- API reference: Swagger UI at `/swagger-ui.html` (dev)

## Status

Phases 1 (foundation) and 2 (authentication) complete. See the [checklist](docs/development-checklist.md) for what is built and what is next.

# Organizations, categories and verification (Phase 3)

## Lifecycle

```
DRAFT ──submit──▶ PENDING ──approve──▶ APPROVED ──suspend──▶ SUSPENDED
  ▲                  │                    ▲                        │
  │               reject                  └───────reinstate────────┘
  │                  ▼
  └──(owner edits)── REJECTED ──submit──▶ PENDING
```

| Status | Owner can change | Notes |
|---|---|---|
| DRAFT, REJECTED | everything: name, category, documents, contacts, branches, hours, holidays | Changing to a different category drops documents of the old category |
| PENDING | nothing | The reviewer sees a stable application |
| APPROVED | contacts, description, confirmation mode, logo, branches, hours, holidays | Name/category need support; branches can be deactivated, not deleted |
| SUSPENDED | nothing | Owner sees the reason |

Submitting requires: a contact (business mobile or email), at least one active branch, opening hours for every active branch, every active mandatory document uploaded and not rejected, and a verified email or mobile on the primary owner's account. Missing steps are returned together as `ORGANIZATION_INCOMPLETE` field errors (`CONTACT`, `BRANCH`, `WORKING_HOURS`, `DOCUMENTS`, `ACCOUNT_VERIFICATION`).

Each person may have at most 3 businesses in DRAFT/PENDING/REJECTED at once, and 50 branches per business.

## API

| Method & path | Who |
|---|---|
| `GET /public/categories` | anyone: active categories with active subcategories |
| `GET /public/files/{publicId}` | anyone: PUBLIC files (logos) only |
| `POST /business/organizations` | any signed-in user: creates a DRAFT and grants `BUSINESS_OWNER` |
| `GET/PUT /owner/organizations[/{id}]`, `POST …/submit`, `POST/DELETE …/logo` | owner of that business |
| `POST/PUT/DELETE …/branches[/{branchId}]`, `PUT …/hours`, `GET/POST/DELETE …/holidays[/{id}]` | owner |
| `POST/DELETE …/documents/{requirementId}`, `GET …/documents/{requirementId}/file` | owner |
| `GET /admin/organizations?status=&q=`, `GET /admin/organizations/{id}` | Super Admin |
| `GET …/documents/{documentId}/file`, `PATCH …/documents/{documentId}` | Super Admin |
| `POST /admin/organizations/{id}/approve · reject · suspend · reinstate` | Super Admin (reason required for reject/suspend) |
| `GET/POST/PUT/DELETE /admin/categories…` (+ `/subcategories`, `/document-requirements`) | Super Admin |

Tenant isolation: every owner call first checks `organization_owners`; a business you don't own returns **404**, identical to one that doesn't exist. Branch, holiday and document ids are always resolved inside the organization in the path. Super Admins can't approve or reject a business they own.

Concurrency: every state-changing call takes `SELECT … FOR UPDATE` on the organization row, so a review can't interleave with an owner edit.

## Files

- `FileStorage` interface; `LocalDiskFileStorage` writes under `FILE_STORAGE_PATH` (Render: persistent disk). Keys look like `private/2026/11/<uuid>.pdf` and never contain user input.
- The real type comes from magic bytes (PDF, JPEG, PNG, WebP); the browser's Content-Type and file name are ignored. Each requirement sets its allowed types and size (≤ 10 MB); logos are images ≤ 2 MB.
- Bytes written for a transaction that rolls back are deleted; replaced or removed files are deleted after commit.
- Private documents are only streamed through the owner/admin endpoints above with `Cache-Control: no-store` and a sandboxing CSP. The UI opens them from a blob, so no shareable link exists.

## Notifications

State changes publish `OrganizationStatusChanged`. `OrganizationNotifier` emails every owner after commit (submitted, approved, rejected, suspended, reinstated) in their preferred language; delivery failures are logged and never undo the decision. Phase 9's outbox will consume the same event.

## Audit

`ORGANIZATION_CREATED`, `ORGANIZATION_SUBMITTED`, `ORGANIZATION_DOCUMENT_UPLOADED` (with SHA-256), `ORGANIZATION_DOCUMENT_REVIEWED`, `ORGANIZATION_APPROVED|REJECTED|SUSPENDED|REINSTATED`, and `CATEGORY_* / SUBCATEGORY_* / DOC_REQUIREMENT_*` for catalogue changes. Owners see their own history (`organization_verifications`) on the review tab.

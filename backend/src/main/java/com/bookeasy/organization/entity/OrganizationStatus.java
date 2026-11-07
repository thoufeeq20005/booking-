package com.bookeasy.organization.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Verification lifecycle:
 * <pre>
 * DRAFT ──submit──▶ PENDING ──approve──▶ APPROVED ──suspend──▶ SUSPENDED
 *   ▲                  │                    ▲                       │
 *   │               reject                  └──────reinstate────────┘
 *   │                  ▼
 *   └── (edit) ── REJECTED ──submit──▶ PENDING
 * </pre>
 */
public enum OrganizationStatus {
    DRAFT,
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED;

    /** Statuses that count towards the per-owner limit of unfinished applications. */
    public static final Set<OrganizationStatus> IN_PROGRESS = EnumSet.of(DRAFT, PENDING, REJECTED);

    /** Name, category and documents can change only before approval and outside review. */
    public boolean identityEditable() {
        return this == DRAFT || this == REJECTED;
    }

    /** Contact details, branches, hours and holidays. Locked while under review and while suspended. */
    public boolean operationsEditable() {
        return this == DRAFT || this == REJECTED || this == APPROVED;
    }
}

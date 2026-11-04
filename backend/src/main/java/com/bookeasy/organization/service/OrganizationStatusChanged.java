package com.bookeasy.organization.service;

import com.bookeasy.organization.entity.OrganizationVerification;

/**
 * Published inside the transaction that changes an organization's verification status. Listeners that talk to
 * the outside world must run after commit. This is the hook the Phase 9 notification outbox will consume.
 */
public record OrganizationStatusChanged(long organizationId, OrganizationVerification.Action action, String reason) {
}

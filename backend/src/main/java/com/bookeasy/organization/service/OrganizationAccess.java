package com.bookeasy.organization.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.repository.OrganizationOwnerRepository;
import com.bookeasy.organization.repository.OrganizationRepository;

/**
 * Tenant isolation for owner endpoints. A business the caller doesn't own is reported as "not found", exactly
 * like one that doesn't exist, so ids can't be probed.
 */
@Component
public class OrganizationAccess {

    private final OrganizationRepository organizations;
    private final OrganizationOwnerRepository owners;

    public OrganizationAccess(OrganizationRepository organizations, OrganizationOwnerRepository owners) {
        this.organizations = organizations;
        this.owners = owners;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Organization owned(long userId, long organizationId) {
        requireOwner(userId, organizationId);
        return organizations.findById(organizationId).orElseThrow(OrganizationAccess::notFound);
    }

    /** Same as {@link #owned} but takes a row lock for the rest of the transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Organization ownedForUpdate(long userId, long organizationId) {
        requireOwner(userId, organizationId);
        return organizations.findByIdForUpdate(organizationId).orElseThrow(OrganizationAccess::notFound);
    }

    /** Locked organization whose identity (name, category, documents) may change in its current status. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Organization ownedForIdentityEdit(long userId, long organizationId) {
        Organization org = ownedForUpdate(userId, organizationId);
        if (!org.getStatus().identityEditable()) {
            throw new ApiException(ErrorCode.ORGANIZATION_NOT_EDITABLE);
        }
        return org;
    }

    /** Locked organization whose operational details (contact, branches, hours) may change. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Organization ownedForOperationsEdit(long userId, long organizationId) {
        Organization org = ownedForUpdate(userId, organizationId);
        if (!org.getStatus().operationsEditable()) {
            throw new ApiException(ErrorCode.ORGANIZATION_NOT_EDITABLE);
        }
        return org;
    }

    private void requireOwner(long userId, long organizationId) {
        if (!owners.existsByOrganizationIdAndUserId(organizationId, userId)) {
            throw notFound();
        }
    }

    static ApiException notFound() {
        return ApiException.notFound("Business");
    }
}

package com.bookeasy.common.security;

import java.util.Set;

import com.bookeasy.user.entity.RoleCode;

/** Principal placed in the SecurityContext for every authenticated request. */
public record AuthenticatedUser(long id, Set<RoleCode> roles) {

    public boolean hasRole(RoleCode role) {
        return roles.contains(role);
    }

    /** Highest-privilege role, used for audit attribution. */
    public String primaryRole() {
        for (RoleCode r : new RoleCode[]{RoleCode.SUPER_ADMIN, RoleCode.BUSINESS_OWNER, RoleCode.STAFF, RoleCode.CUSTOMER}) {
            if (roles.contains(r)) {
                return r.name();
            }
        }
        return null;
    }
}

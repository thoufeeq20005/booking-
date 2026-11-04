package com.bookeasy.user.entity;

import java.util.Arrays;

/** Mirrors the seeded `roles` table (ids are fixed by migration V1). */
public enum RoleCode {
    CUSTOMER((short) 1),
    BUSINESS_OWNER((short) 2),
    STAFF((short) 3),
    SUPER_ADMIN((short) 4);

    private final short id;

    RoleCode(short id) {
        this.id = id;
    }

    public short id() {
        return id;
    }

    /** Spring Security authority name, e.g. ROLE_CUSTOMER. */
    public String authority() {
        return "ROLE_" + name();
    }

    public static RoleCode fromId(short id) {
        return Arrays.stream(values()).filter(r -> r.id == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown role id " + id));
    }
}

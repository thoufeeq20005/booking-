package com.bookeasy.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthenticatedUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
}

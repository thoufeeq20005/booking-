package com.bookeasy.auth.service;

import java.time.Clock;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.auth.dto.AuthRequests;
import com.bookeasy.auth.dto.AuthResponses.TwoFactorSetup;
import com.bookeasy.auth.dto.AuthResponses.UserView;
import com.bookeasy.auth.twofactor.TwoFactorService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;

/** The signed-in user's own account: profile basics and two-factor settings. */
@Service
public class AccountService {

    private final UserRepository users;
    private final TwoFactorService twoFactorService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountService(UserRepository users, TwoFactorService twoFactorService, PasswordEncoder passwordEncoder,
                          Clock clock) {
        this.users = users;
        this.twoFactorService = twoFactorService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserView me(long userId) {
        return UserView.of(load(userId));
    }

    @Transactional
    public UserView updateProfile(long userId, AuthRequests.UpdateProfile req) {
        User user = load(userId);
        if (req.fullName() != null) {
            String name = req.fullName().trim();
            if (name.isEmpty()) {
                throw ApiException.invalidField("fullName", "NotBlank", "Enter your name");
            }
            user.setFullName(name);
        }
        if (req.preferredLanguage() != null) {
            user.setPreferredLanguage(req.preferredLanguage());
        }
        user.touch(clock.instant());
        return UserView.of(user);
    }

    @Transactional
    public TwoFactorSetup beginTwoFactorSetup(long userId) {
        return twoFactorService.beginSetup(load(userId));
    }

    @Transactional
    public UserView enableTwoFactor(long userId, String code) {
        User user = load(userId);
        twoFactorService.enable(user, code);
        return UserView.of(user);
    }

    /** Needs a current TOTP code, plus the password when the account has one. */
    @Transactional
    public UserView disableTwoFactor(long userId, AuthRequests.DisableTwoFactor req) {
        User user = load(userId);
        if (user.getPasswordHash() != null
                && (req.password() == null || !passwordEncoder.matches(req.password(), user.getPasswordHash()))) {
            throw ApiException.invalidField("password", "PASSWORD_INCORRECT", "Password is incorrect");
        }
        twoFactorService.disable(user, req.code());
        return UserView.of(user);
    }

    private User load(long userId) {
        return users.findWithRolesById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }
}

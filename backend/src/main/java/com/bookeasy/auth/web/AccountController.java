package com.bookeasy.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.auth.dto.AuthRequests;
import com.bookeasy.auth.dto.AuthResponses.OtpSent;
import com.bookeasy.auth.dto.AuthResponses.Session;
import com.bookeasy.auth.dto.AuthResponses.TwoFactorSetup;
import com.bookeasy.auth.dto.AuthResponses.UserView;
import com.bookeasy.auth.service.AccountService;
import com.bookeasy.auth.service.EmailVerificationService;
import com.bookeasy.auth.service.PasswordAuthService;
import com.bookeasy.auth.service.PasswordResetService;
import com.bookeasy.auth.service.PhoneAuthService;
import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The signed-in user's own account. Every endpoint acts only on the caller's user id. */
@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Account")
public class AccountController {

    private final AccountService accounts;
    private final EmailVerificationService emailVerification;
    private final PasswordResetService passwordReset;
    private final PhoneAuthService phoneAuth;
    private final PasswordAuthService passwordAuth;
    private final AuthWebSupport web;

    public AccountController(AccountService accounts, EmailVerificationService emailVerification,
                             PasswordResetService passwordReset, PhoneAuthService phoneAuth,
                             PasswordAuthService passwordAuth, AuthWebSupport web) {
        this.accounts = accounts;
        this.emailVerification = emailVerification;
        this.passwordReset = passwordReset;
        this.phoneAuth = phoneAuth;
        this.passwordAuth = passwordAuth;
        this.web = web;
    }

    @GetMapping("/me")
    @Operation(summary = "Current user")
    public ApiResponse<UserView> me() {
        return ApiResponse.ok(accounts.me(CurrentUser.require().id()));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update name and preferred language")
    public ApiResponse<UserView> update(@Valid @RequestBody AuthRequests.UpdateProfile req) {
        return ApiResponse.ok(accounts.updateProfile(CurrentUser.require().id(), req));
    }

    @PostMapping("/email/resend-verification")
    @Operation(summary = "Send the email verification link again")
    public ApiResponse<Void> resendVerification() {
        emailVerification.resend(CurrentUser.require().id());
        return ApiResponse.ok(null);
    }

    @PostMapping("/password")
    @Operation(summary = "Change (or set a first) password; other sessions are signed out")
    public ResponseEntity<ApiResponse<Session>> changePassword(@Valid @RequestBody AuthRequests.ChangePassword req,
                                                               HttpServletRequest http) {
        return web.sessionResponse(passwordReset.change(CurrentUser.require().id(), req, web.context(http)));
    }

    @PostMapping("/phone/send-code")
    @Operation(summary = "Send a code to add or verify a mobile number")
    public ApiResponse<OtpSent> sendPhoneCode(@Valid @RequestBody AuthRequests.OtpSend req) {
        return ApiResponse.ok(phoneAuth.sendVerificationCode(CurrentUser.require().id(), req.phone()));
    }

    @PostMapping("/phone/verify")
    @Operation(summary = "Confirm the mobile number with the SMS code")
    public ApiResponse<UserView> verifyPhone(@Valid @RequestBody AuthRequests.PhoneVerify req) {
        return ApiResponse.ok(phoneAuth.verifyPhone(CurrentUser.require().id(), req));
    }

    @PostMapping("/two-factor/setup")
    @Operation(summary = "Start authenticator-app setup (returns secret and otpauth URI)")
    public ApiResponse<TwoFactorSetup> setupTwoFactor() {
        return ApiResponse.ok(accounts.beginTwoFactorSetup(CurrentUser.require().id()));
    }

    @PostMapping("/two-factor/enable")
    @Operation(summary = "Turn on 2FA after confirming a code from the app")
    public ApiResponse<UserView> enableTwoFactor(@Valid @RequestBody AuthRequests.TwoFactorCode req) {
        return ApiResponse.ok(accounts.enableTwoFactor(CurrentUser.require().id(), req.code()));
    }

    @PostMapping("/two-factor/disable")
    @Operation(summary = "Turn off 2FA (needs a current code and the password)")
    public ApiResponse<UserView> disableTwoFactor(@Valid @RequestBody AuthRequests.DisableTwoFactor req) {
        return ApiResponse.ok(accounts.disableTwoFactor(CurrentUser.require().id(), req));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Sign out of every device")
    public ResponseEntity<Void> logoutAll() {
        passwordAuth.logoutAll(CurrentUser.require().id());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, web.clearCookieHeader()).build();
    }
}

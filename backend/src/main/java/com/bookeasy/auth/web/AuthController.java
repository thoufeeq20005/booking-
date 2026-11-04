package com.bookeasy.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.auth.dto.AuthRequests;
import com.bookeasy.auth.dto.AuthResponses.OtpSent;
import com.bookeasy.auth.dto.AuthResponses.Registered;
import com.bookeasy.auth.dto.AuthResponses.Session;
import com.bookeasy.auth.service.EmailVerificationService;
import com.bookeasy.auth.service.GoogleAuthService;
import com.bookeasy.auth.service.PasswordAuthService;
import com.bookeasy.auth.service.PasswordResetService;
import com.bookeasy.auth.service.PhoneAuthService;
import com.bookeasy.common.api.ApiError;
import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.exception.ApiException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Public sign-in endpoints. The refresh token is only ever set as an HttpOnly cookie. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
@SecurityRequirements
public class AuthController {

    private final PasswordAuthService passwordAuth;
    private final PhoneAuthService phoneAuth;
    private final GoogleAuthService googleAuth;
    private final EmailVerificationService emailVerification;
    private final PasswordResetService passwordReset;
    private final AuthWebSupport web;

    public AuthController(PasswordAuthService passwordAuth, PhoneAuthService phoneAuth, GoogleAuthService googleAuth,
                          EmailVerificationService emailVerification, PasswordResetService passwordReset,
                          AuthWebSupport web) {
        this.passwordAuth = passwordAuth;
        this.phoneAuth = phoneAuth;
        this.googleAuth = googleAuth;
        this.emailVerification = emailVerification;
        this.passwordReset = passwordReset;
        this.web = web;
    }

    @PostMapping("/register")
    @Operation(summary = "Create a customer account with email and password (signs in immediately)")
    public ResponseEntity<ApiResponse<Registered>> register(@Valid @RequestBody AuthRequests.Register req,
                                                            HttpServletRequest http) {
        PasswordAuthService.RegisterResult result = passwordAuth.register(req, web.context(http));
        return web.withRefreshCookie(result.refreshToken(), result.body());
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password; may return TWO_FACTOR_REQUIRED")
    public ResponseEntity<ApiResponse<Session>> login(@Valid @RequestBody AuthRequests.Login req,
                                                      HttpServletRequest http) {
        return web.sessionResponse(passwordAuth.login(req, web.context(http)));
    }

    @PostMapping("/login/two-factor")
    @Operation(summary = "Complete sign-in with an authenticator-app code")
    public ResponseEntity<ApiResponse<Session>> twoFactor(@Valid @RequestBody AuthRequests.TwoFactorLogin req,
                                                          HttpServletRequest http) {
        return web.sessionResponse(passwordAuth.completeTwoFactor(req, web.context(http)));
    }

    @PostMapping("/otp/send")
    @Operation(summary = "Send a sign-in code to an Indian mobile number")
    public ApiResponse<OtpSent> sendOtp(@Valid @RequestBody AuthRequests.OtpSend req, HttpServletRequest http) {
        return ApiResponse.ok(phoneAuth.sendLoginCode(req.phone(), web.context(http)));
    }

    @PostMapping("/otp/login")
    @Operation(summary = "Sign in (or sign up, with fullName) using the SMS code")
    public ResponseEntity<ApiResponse<Session>> otpLogin(@Valid @RequestBody AuthRequests.OtpLogin req,
                                                         HttpServletRequest http) {
        return web.sessionResponse(phoneAuth.login(req, web.context(http)));
    }

    @PostMapping("/google")
    @Operation(summary = "Sign in with a Google Identity Services ID token")
    public ResponseEntity<ApiResponse<Session>> google(@Valid @RequestBody AuthRequests.GoogleLogin req,
                                                       HttpServletRequest http) {
        return web.sessionResponse(googleAuth.login(req.idToken(), web.context(http)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh cookie for a new access token (rotates the cookie)")
    public ResponseEntity<ApiResponse<Session>> refresh(HttpServletRequest http) {
        String raw = web.refreshTokenFrom(http);
        try {
            return web.sessionResponse(passwordAuth.refresh(raw, web.context(http)));
        } catch (ApiException e) {
            // Drop a dead cookie so the browser stops presenting it.
            return ResponseEntity.status(e.errorCode().status())
                    .header(HttpHeaders.SET_COOKIE, web.clearCookieHeader())
                    .body(ApiResponse.fail(new ApiError(e.errorCode().name(), e.getMessage())));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out this device")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        passwordAuth.logout(web.refreshTokenFrom(http));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, web.clearCookieHeader()).build();
    }

    @PostMapping("/email/verify")
    @Operation(summary = "Confirm an email address using the emailed token")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody AuthRequests.Token req) {
        emailVerification.verify(req.token());
        return ApiResponse.ok(null);
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Email a reset link if the account exists (always 202)")
    public ApiResponse<Void> forgot(@Valid @RequestBody AuthRequests.ForgotPassword req, HttpServletRequest http) {
        passwordReset.requestReset(req.email(), web.context(http));
        return ApiResponse.ok(null);
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Set a new password using the emailed token; signs out all sessions")
    public ApiResponse<Void> reset(@Valid @RequestBody AuthRequests.ResetPassword req) {
        passwordReset.reset(req);
        return ApiResponse.ok(null);
    }
}

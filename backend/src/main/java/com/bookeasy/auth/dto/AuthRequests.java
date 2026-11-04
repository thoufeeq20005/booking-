package com.bookeasy.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies for the auth API. Password strength is enforced by PasswordPolicy in the service. */
public final class AuthRequests {

    public static final String LANGUAGE_PATTERN = "en|ta|hi|ml|te|kn";

    private AuthRequests() {
    }

    public record Register(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 128) String password,
            @Pattern(regexp = LANGUAGE_PATTERN) String preferredLanguage) {
    }

    public record Login(@NotBlank @Size(max = 255) String email, @NotBlank @Size(max = 128) String password) {
    }

    public record TwoFactorLogin(@NotBlank String challengeToken, @NotBlank @Pattern(regexp = "\\d{6}") String code) {
    }

    public record Token(@NotBlank @Size(max = 200) String token) {
    }

    public record ForgotPassword(@NotBlank @Email @Size(max = 255) String email) {
    }

    public record ResetPassword(@NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 128) String newPassword) {
    }

    public record ChangePassword(@Size(max = 128) String currentPassword, @NotBlank @Size(max = 128) String newPassword) {
    }

    public record OtpSend(@NotBlank @Size(max = 20) String phone) {
    }

    public record OtpLogin(
            @NotBlank @Size(max = 20) String phone,
            @NotBlank @Pattern(regexp = "\\d{4,8}") String code,
            @Size(max = 120) String fullName,
            @Pattern(regexp = LANGUAGE_PATTERN) String preferredLanguage) {
    }

    public record PhoneVerify(@NotBlank @Size(max = 20) String phone, @NotBlank @Pattern(regexp = "\\d{4,8}") String code) {
    }

    public record GoogleLogin(@NotBlank @Size(max = 4096) String idToken) {
    }

    public record TwoFactorCode(@NotBlank @Pattern(regexp = "\\d{6}") String code) {
    }

    public record DisableTwoFactor(@NotBlank @Pattern(regexp = "\\d{6}") String code, @Size(max = 128) String password) {
    }

    public record UpdateProfile(@Size(min = 1, max = 120) String fullName,
                                @Pattern(regexp = LANGUAGE_PATTERN) String preferredLanguage) {
    }

    public record BootstrapAdmin(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 128) String password) {
    }
}

package com.bookeasy.auth.twofactor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.OptionalLong;

import org.springframework.stereotype.Service;

import com.bookeasy.auth.dto.AuthResponses.TwoFactorSetup;
import com.bookeasy.common.crypto.CryptoService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.user.entity.User;

/** TOTP enrolment and verification. Callers own the transaction that persists the user changes. */
@Service
public class TwoFactorService {

    private static final String ISSUER = "BookEasy";
    private static final int SECRET_BYTES = 20;

    private final CryptoService crypto;
    private final Clock clock;

    public TwoFactorService(CryptoService crypto, Clock clock) {
        this.crypto = crypto;
        this.clock = clock;
    }

    /** Generates a new pending secret; 2FA stays off until {@link #enable} confirms a code from the app. */
    public TwoFactorSetup beginSetup(User user) {
        if (user.isTwoFactorEnabled()) {
            throw new ApiException(ErrorCode.TWO_FACTOR_ALREADY_ENABLED);
        }
        byte[] secret = crypto.randomBytes(SECRET_BYTES);
        user.setTwoFactorSecretEnc(crypto.encrypt(secret));
        user.setTwoFactorLastStep(null);
        user.touch(clock.instant());
        String secretB32 = Base32.encode(secret);
        String account = user.getEmail() != null ? user.getEmail() : user.getPhone();
        String label = enc(ISSUER) + ":" + enc(account);
        String uri = "otpauth://totp/" + label + "?secret=" + secretB32 + "&issuer=" + enc(ISSUER)
                + "&algorithm=SHA1&digits=" + Totp.DIGITS + "&period=" + Totp.STEP_SECONDS;
        return new TwoFactorSetup(secretB32, uri);
    }

    public void enable(User user, String code) {
        if (user.isTwoFactorEnabled()) {
            throw new ApiException(ErrorCode.TWO_FACTOR_ALREADY_ENABLED);
        }
        if (user.getTwoFactorSecretEnc() == null) {
            throw new ApiException(ErrorCode.TWO_FACTOR_SETUP_REQUIRED);
        }
        requireValidCode(user, code);
        user.setTwoFactorEnabled(true);
    }

    public void disable(User user, String code) {
        if (!user.isTwoFactorEnabled()) {
            throw new ApiException(ErrorCode.TWO_FACTOR_NOT_ENABLED);
        }
        requireValidCode(user, code);
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecretEnc(null);
        user.setTwoFactorLastStep(null);
    }

    /** Verifies a code and records its time step so the same code can't be replayed. */
    public void requireValidCode(User user, String code) {
        byte[] secret = crypto.decrypt(user.getTwoFactorSecretEnc());
        OptionalLong step = Totp.verify(secret, code, clock.instant());
        Long last = user.getTwoFactorLastStep();
        if (step.isEmpty() || (last != null && step.getAsLong() <= last)) {
            throw new ApiException(ErrorCode.TWO_FACTOR_INVALID);
        }
        user.setTwoFactorLastStep(step.getAsLong());
        user.touch(clock.instant());
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

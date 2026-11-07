package com.bookeasy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.bookeasy.notification.channel.EmailSender.EmailMessage;
import com.bookeasy.support.AuthTestBase;

class EmailCodeAuthIT extends AuthTestBase {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    private String lastEmailedCode(String email) {
        List<EmailMessage> mine = channels.emailsTo(email);
        assertThat(mine).as("emails to " + email).isNotEmpty();
        Matcher m = CODE.matcher(mine.get(mine.size() - 1).textBody());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void existingAccountSignsInWithTheEmailedCode() throws Exception {
        String email = uniqueEmail();
        register(email);
        channels.clear();

        mvc.perform(postJson("/api/v1/auth/email-code/send", Map.of("email", email.toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.data.demoCode").doesNotExist()); // a real channel never echoes the code
        String code = lastEmailedCode(email);
        assertThat(channels.emailsTo(email).get(0).subject()).contains(code);

        String wrong = code.equals("000000") ? "111111" : "000000";
        mvc.perform(postJson("/api/v1/auth/email-code/login", Map.of("email", email, "code", wrong)))
                .andExpect(jsonPath("$.error.code").value("OTP_INVALID"));

        mvc.perform(postJson("/api/v1/auth/email-code/login", Map.of("email", email, "code", code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.emailVerified").value(true));

        // Single use.
        mvc.perform(postJson("/api/v1/auth/email-code/login", Map.of("email", email, "code", code)))
                .andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
    }

    @Test
    void unknownEmailGetsTheSameAnswerButNoEmail() throws Exception {
        String email = uniqueEmail();
        mvc.perform(postJson("/api/v1/auth/email-code/send", Map.of("email", email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300));
        assertThat(channels.emailsTo(email)).isEmpty();
        mvc.perform(postJson("/api/v1/auth/email-code/login", Map.of("email", email, "code", "123456")))
                .andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
    }

    @Test
    void resendIsRateLimitedLikeSms() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(postJson("/api/v1/auth/email-code/send", Map.of("email", email))).andExpect(status().isOk());
        mvc.perform(postJson("/api/v1/auth/email-code/send", Map.of("email", email)))
                .andExpect(jsonPath("$.error.code").value("OTP_RESEND_TOO_SOON"));
    }
}

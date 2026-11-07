package com.bookeasy.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.bookeasy.auth.google.GoogleIdTokenVerifier;
import com.bookeasy.auth.google.TestGoogleTokens;

/** Replaces time, outbound channels and Google verification with controllable test doubles. */
@TestConfiguration
public class TestSupportConfig {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }

    /** Implements both EmailSender and SmsSender, so one primary bean overrides both channels. */
    @Bean
    @Primary
    RecordingChannels recordingChannels() {
        return new RecordingChannels();
    }

    @Bean
    TestGoogleTokens testGoogleTokens(MutableClock clock) {
        return new TestGoogleTokens(clock);
    }

    @Bean
    @Primary
    GoogleIdTokenVerifier testGoogleVerifier(TestGoogleTokens tokens) {
        return tokens.verifier();
    }
}

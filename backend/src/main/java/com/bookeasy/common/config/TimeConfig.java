package com.bookeasy.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class TimeConfig {

    /** Inject this instead of calling Instant.now() so expiry rules are testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

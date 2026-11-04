package com.bookeasy;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// The in-memory default user is excluded: all accounts live in MySQL and authenticate via JWT.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class BookEasyApplication {

    public static void main(String[] args) {
        // All persisted instants are UTC; wall-clock types (TIME/DATE) must not be shifted by the host zone.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(BookEasyApplication.class, args);
    }
}

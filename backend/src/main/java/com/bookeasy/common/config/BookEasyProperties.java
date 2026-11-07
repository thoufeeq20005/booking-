package com.bookeasy.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bookeasy")
public record BookEasyProperties(String frontendUrl, String backendUrl, Cors cors, Storage storage) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Storage(String rootPath) {
    }
}

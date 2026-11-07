package com.bookeasy.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.bookeasy.common.api.ApiError;
import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Writes 401/403 responses from the security filter chain in the standard {@link ApiResponse} shape.
 */
@Component
public class RestAuthHandlers {

    private final ObjectMapper objectMapper;

    public RestAuthHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> write(response, ErrorCode.UNAUTHENTICATED);
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(response, ErrorCode.ACCESS_DENIED);
    }

    private void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.fail(new ApiError(code.name(), code.defaultMessage())));
    }
}

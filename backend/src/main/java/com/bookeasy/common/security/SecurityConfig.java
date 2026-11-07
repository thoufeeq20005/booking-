package com.bookeasy.common.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.bookeasy.auth.web.AuthWebSupport;
import com.bookeasy.common.config.BookEasyProperties;
import com.bookeasy.user.entity.RoleCode;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Endpoints reachable without a token. Everything else requires authentication. */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/health",
            "/api/v1/public/**",
            "/api/v1/auth/**",
            "/actuator/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RestAuthHandlers authHandlers,
                                            CorsConfigurationSource corsConfigurationSource, JwtDecoder jwtDecoder,
                                            AccessTokenAuthenticationConverter tokenConverter) throws Exception {
        http
                // Access tokens travel in the Authorization header, never in cookies, so CSRF can't ride on them.
                // The refresh cookie endpoints are protected separately (SameSite + required custom header).
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("frame-ancestors 'none'; object-src 'none'; base-uri 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole(RoleCode.SUPER_ADMIN.name())
                        .requestMatchers("/api/v1/owner/**").hasRole(RoleCode.BUSINESS_OWNER.name())
                        .requestMatchers("/api/v1/staff/**").hasRole(RoleCode.STAFF.name())
                        .requestMatchers("/api/v1/customer/**").hasRole(RoleCode.CUSTOMER.name())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .bearerTokenResolver(publicPathAwareTokenResolver())
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(tokenConverter))
                        .authenticationEntryPoint(authHandlers.entryPoint())
                        .accessDeniedHandler(authHandlers.accessDeniedHandler()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authHandlers.entryPoint())
                        .accessDeniedHandler(authHandlers.accessDeniedHandler()));
        return http.build();
    }

    /**
     * Ignores the Authorization header on public endpoints, so a stale access token can't break sign-in or
     * the refresh call that exists precisely to replace it.
     */
    private static BearerTokenResolver publicPathAwareTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        List<PathPatternRequestMatcher> publicMatchers = Arrays.stream(PUBLIC_PATHS)
                .map(p -> PathPatternRequestMatcher.withDefaults().matcher(p)).toList();
        return request -> publicMatchers.stream().anyMatch(m -> m.matches(request)) ? null : delegate.resolve(request);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(BookEasyProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language", "X-Requested-With",
                AuthWebSupport.CLIENT_HEADER));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}

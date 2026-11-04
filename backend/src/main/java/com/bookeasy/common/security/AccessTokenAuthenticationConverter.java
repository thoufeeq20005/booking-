package com.bookeasy.common.security;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import com.bookeasy.auth.token.JwtTokenService;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.entity.UserStatus;
import com.bookeasy.user.repository.UserRepository;

/**
 * Turns a signature-verified access JWT into an {@link AuthenticatedUser}. Roles and status are re-read
 * from the database on every request, so suspensions, role changes and logout-all apply immediately
 * rather than when the token expires.
 */
@Component
public class AccessTokenAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public AccessTokenAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (!JwtTokenService.TYPE_ACCESS.equals(jwt.getClaimAsString(JwtTokenService.CLAIM_TYPE))) {
            throw new InvalidBearerTokenException("Not an access token");
        }
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidBearerTokenException("Invalid subject");
        }
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new InvalidBearerTokenException("Unknown user"));
        Number tokenVersion = jwt.getClaim(JwtTokenService.CLAIM_TOKEN_VERSION);
        if (user.getStatus() != UserStatus.ACTIVE || tokenVersion == null
                || tokenVersion.intValue() != user.getTokenVersion()) {
            throw new InvalidBearerTokenException("Token revoked");
        }
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.roleSet());
        List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                .map(r -> new SimpleGrantedAuthority(r.authority())).toList();
        return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
    }
}

package com.bookeasy.auth.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.bookeasy.auth.dto.AuthResponses.Session;
import com.bookeasy.auth.service.RequestContext;
import com.bookeasy.auth.service.SessionIssuer.AuthResult;
import com.bookeasy.auth.token.RefreshCookies;
import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.web.ClientIpResolver;

/** Shared controller plumbing: request context, refresh cookie, and the anti-CSRF header check. */
@Component
public class AuthWebSupport {

    /**
     * Endpoints authenticated by the refresh cookie require this header. Browsers can't add custom headers to
     * cross-site requests without a CORS preflight, which our allow-list rejects.
     */
    public static final String CLIENT_HEADER = "X-BookEasy-Client";

    private final ClientIpResolver ipResolver;
    private final RefreshCookies refreshCookies;

    public AuthWebSupport(ClientIpResolver ipResolver, RefreshCookies refreshCookies) {
        this.ipResolver = ipResolver;
        this.refreshCookies = refreshCookies;
    }

    public RequestContext context(HttpServletRequest request) {
        return new RequestContext(ipResolver.resolve(request), request.getHeader(HttpHeaders.USER_AGENT));
    }

    public String ip(HttpServletRequest request) {
        return ipResolver.resolve(request);
    }

    public String refreshTokenFrom(HttpServletRequest request) {
        if (request.getHeader(CLIENT_HEADER) == null) {
            throw new ApiException(ErrorCode.CSRF_HEADER_MISSING);
        }
        return refreshCookies.read(request);
    }

    public ResponseEntity<ApiResponse<Session>> sessionResponse(AuthResult result) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (result.refreshToken() != null) {
            builder.header(HttpHeaders.SET_COOKIE, refreshCookies.create(result.refreshToken()).toString());
        }
        return builder.body(ApiResponse.ok(result.session()));
    }

    public <T> ResponseEntity<ApiResponse<T>> withRefreshCookie(String refreshToken, T body) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookies.create(refreshToken).toString())
                .body(ApiResponse.ok(body));
    }

    public String clearCookieHeader() {
        return refreshCookies.clear().toString();
    }
}

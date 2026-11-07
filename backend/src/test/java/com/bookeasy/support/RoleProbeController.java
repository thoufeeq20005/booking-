package com.bookeasy.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.security.CurrentUser;

/**
 * Test-only endpoints, one under each role-gated prefix, to prove the URL rules in SecurityConfig.
 * Lives in test sources, so it never ships.
 */
@RestController
public class RoleProbeController {

    @GetMapping({"/api/v1/customer/_probe", "/api/v1/owner/_probe", "/api/v1/staff/_probe", "/api/v1/admin/_probe"})
    public ApiResponse<Long> probe() {
        return ApiResponse.ok(CurrentUser.require().id());
    }
}

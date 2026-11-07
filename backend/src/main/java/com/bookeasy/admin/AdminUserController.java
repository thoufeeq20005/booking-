package com.bookeasy.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.auth.dto.AuthResponses.UserView;
import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.api.PageResponse;
import com.bookeasy.common.security.CurrentUser;
import com.bookeasy.common.web.ClientIpResolver;
import com.bookeasy.user.entity.RoleCode;
import com.bookeasy.user.entity.UserStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Super Admin user management. Access to /api/v1/admin/** is restricted to SUPER_ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin – Users")
public class AdminUserController {

    private final AdminUserService service;
    private final ClientIpResolver ipResolver;

    public AdminUserController(AdminUserService service, ClientIpResolver ipResolver) {
        this.service = service;
        this.ipResolver = ipResolver;
    }

    public record StatusChange(@NotNull UserStatus status, @Size(max = 500) String reason) {
    }

    public record ResetTwoFactor(@Size(max = 500) String reason) {
    }

    @PostMapping("/{id}/two-factor/reset")
    @Operation(summary = "Turn off a user's two-step verification after confirming their identity (audited)")
    public ApiResponse<UserView> resetTwoFactor(@PathVariable long id, @Valid @RequestBody ResetTwoFactor req,
                                                HttpServletRequest http) {
        return ApiResponse.ok(service.resetTwoFactor(CurrentUser.require(), id, req.reason(), ipResolver.resolve(http)));
    }

    @GetMapping
    @Operation(summary = "Search users by name, email or phone; filter by status and role")
    public ApiResponse<PageResponse<UserView>> search(@RequestParam(required = false) String q,
                                                      @RequestParam(required = false) UserStatus status,
                                                      @RequestParam(required = false) RoleCode role,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(q, status, role, page, size));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Suspend or reactivate a user (audited)")
    public ApiResponse<UserView> changeStatus(@PathVariable long id, @Valid @RequestBody StatusChange req,
                                              HttpServletRequest http) {
        return ApiResponse.ok(service.changeStatus(CurrentUser.require(), id, req.status(), req.reason(),
                ipResolver.resolve(http)));
    }
}

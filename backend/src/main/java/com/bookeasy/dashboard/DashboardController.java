package com.bookeasy.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.security.CurrentUser;
import com.bookeasy.dashboard.AdminDashboardService.AdminDashboard;
import com.bookeasy.dashboard.OwnerDashboardService.OwnerDashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Role overviews. The URL prefixes are role-gated in SecurityConfig; the owner view only covers the caller's own businesses. */
@RestController
@Tag(name = "Dashboards")
public class DashboardController {

    private final AdminDashboardService admin;
    private final OwnerDashboardService owner;

    public DashboardController(AdminDashboardService admin, OwnerDashboardService owner) {
        this.admin = admin;
        this.owner = owner;
    }

    @GetMapping("/api/v1/admin/dashboard")
    @Operation(summary = "Platform overview for Super Admins")
    public ApiResponse<AdminDashboard> admin() {
        return ApiResponse.ok(admin.overview());
    }

    @GetMapping("/api/v1/owner/dashboard")
    @Operation(summary = "Overview of the caller's businesses")
    public ApiResponse<OwnerDashboard> owner() {
        return ApiResponse.ok(owner.overview(CurrentUser.require().id()));
    }
}

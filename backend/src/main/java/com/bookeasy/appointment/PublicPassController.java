package com.bookeasy.appointment;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.ratelimit.RateLimiter;
import com.bookeasy.common.web.ClientIpResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Opened when someone scans a booking QR with a phone camera. */
@RestController
@RequestMapping("/api/v1/public/passes")
@Tag(name = "Bookings")
@SecurityRequirements
public class PublicPassController {

    private final BookingService bookings;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver ip;

    public PublicPassController(BookingService bookings, RateLimiter rateLimiter, ClientIpResolver ip) {
        this.bookings = bookings;
        this.rateLimiter = rateLimiter;
        this.ip = ip;
    }

    @GetMapping("/{code}")
    @Operation(summary = "Booking details for a scanned, signed booking pass (limited, no contact details)")
    public ApiResponse<BookingPass> pass(@PathVariable String code, HttpServletRequest http) {
        rateLimiter.hit("pass:ip:" + ip.resolve(http), 60, Duration.ofMinutes(10));
        return ApiResponse.ok(bookings.pass(code));
    }
}

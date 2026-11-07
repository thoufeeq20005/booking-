package com.bookeasy.review;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.security.CurrentUser;
import com.bookeasy.common.web.ClientIpResolver;
import com.bookeasy.notification.NotificationService;
import com.bookeasy.notification.NotificationService.InboxItem;
import com.bookeasy.notification.NotificationService.Preferences;
import com.bookeasy.review.ReviewService.ReviewView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Reviews and the signed-in user's notifications. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reviews & notifications")
public class EngagementController {

    private final ReviewService reviews;
    private final NotificationService notifications;
    private final ClientIpResolver ip;

    public EngagementController(ReviewService reviews, NotificationService notifications, ClientIpResolver ip) {
        this.reviews = reviews;
        this.notifications = notifications;
        this.ip = ip;
    }

    public record ReviewBody(@Min(1) @Max(5) int rating, @Size(max = 2000) String comment) {
    }

    public record TextBody(@Size(max = 1000) String text) {
    }

    private static long me() {
        return CurrentUser.require().id();
    }

    // ----- reviews -----

    @PostMapping("/customer/appointments/{id}/review")
    @Operation(summary = "Review a completed booking (once)")
    public ApiResponse<ReviewView> review(@PathVariable long id, @Valid @RequestBody ReviewBody b) {
        return ApiResponse.ok(reviews.create(me(), id, b.rating(), b.comment()));
    }

    @GetMapping("/public/organizations/{slug}/reviews")
    @SecurityRequirements
    public ApiResponse<List<ReviewView>> publicReviews(@PathVariable String slug, @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(reviews.forBusinessPage(slug, limit));
    }

    @GetMapping("/owner/organizations/{orgId}/reviews")
    public ApiResponse<List<ReviewView>> ownerReviews(@PathVariable long orgId) {
        return ApiResponse.ok(reviews.forOwner(me(), orgId));
    }

    @PostMapping("/owner/organizations/{orgId}/reviews/{reviewId}/reply")
    public ApiResponse<ReviewView> reply(@PathVariable long orgId, @PathVariable long reviewId, @Valid @RequestBody TextBody b) {
        return ApiResponse.ok(reviews.reply(me(), orgId, reviewId, b.text()));
    }

    @PostMapping("/reviews/{reviewId}/report")
    @Operation(summary = "Report a review to the moderators (once per person)")
    public ApiResponse<Void> report(@PathVariable long reviewId, @Valid @RequestBody TextBody b) {
        reviews.report(me(), reviewId, b.text());
        return ApiResponse.ok(null);
    }

    @GetMapping("/admin/reviews")
    public ApiResponse<List<ReviewView>> moderation(@RequestParam(defaultValue = "true") boolean reported) {
        return ApiResponse.ok(reviews.moderationQueue(reported));
    }

    @PostMapping("/admin/reviews/{reviewId}/{action}")
    @Operation(summary = "hide | restore")
    public ApiResponse<ReviewView> moderate(@PathVariable long reviewId, @PathVariable String action,
                                            @Valid @RequestBody(required = false) TextBody b, HttpServletRequest http) {
        if (!action.equals("hide") && !action.equals("restore")) {
            throw com.bookeasy.common.exception.ApiException.notFound("Action");
        }
        return ApiResponse.ok(reviews.moderate(CurrentUser.require(), reviewId, action.equals("hide"),
                b == null ? null : b.text(), ip.resolve(http)));
    }

    // ----- notifications -----

    @GetMapping("/account/notifications")
    public ApiResponse<List<InboxItem>> inbox(@RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(notifications.inbox(me(), limit));
    }

    @GetMapping("/account/notifications/unread-count")
    public ApiResponse<Long> unread() {
        return ApiResponse.ok(notifications.unread(me()));
    }

    @PostMapping("/account/notifications/{id}/read")
    public ApiResponse<Void> read(@PathVariable long id) {
        notifications.markRead(me(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/account/notifications/read-all")
    public ApiResponse<Void> readAll() {
        notifications.markRead(me(), null);
        return ApiResponse.ok(null);
    }

    @GetMapping("/account/notification-preferences")
    public ApiResponse<Preferences> preferences() {
        return ApiResponse.ok(notifications.preferences(me()));
    }

    @PutMapping("/account/notification-preferences")
    public ApiResponse<Preferences> savePreferences(@RequestBody Preferences p) {
        return ApiResponse.ok(notifications.savePreferences(me(), p));
    }
}

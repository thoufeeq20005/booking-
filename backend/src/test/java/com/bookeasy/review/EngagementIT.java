package com.bookeasy.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bookeasy.notification.BookingNotifications;
import com.bookeasy.support.BookingTestBase;
import com.fasterxml.jackson.databind.JsonNode;

class EngagementIT extends BookingTestBase {

    @Autowired
    BookingNotifications bookingNotifications;
    @Autowired
    com.bookeasy.notification.NotificationService notifications;

    /** Emails go out on a background thread: send what's due now and wait briefly for it to be recorded. */
    private java.util.List<com.bookeasy.notification.channel.EmailSender.EmailMessage> emailsTo(String to) throws InterruptedException {
        for (int i = 0; i < 50 && channels.emailsTo(to).isEmpty(); i++) {
            notifications.dispatch();
            Thread.sleep(100);
        }
        return channels.emailsTo(to);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void bookingNotifiesCustomerAndBusinessInAppAndByEmail() throws Exception {
        Biz b = approvedClinic("INSTANT");
        Person c = verifiedCustomer();
        channels.clear();
        book(c, b, "09:00").andExpect(status().isOk());

        mvc.perform(authed(get("/api/v1/account/notifications"), c.token()))
                .andExpect(jsonPath("$.data[0].type").value("BOOKING_CONFIRMED"))
                .andExpect(jsonPath("$.data[0].title").value("Booking confirmed: Consultation"));
        assertThat(emailsTo(c.email())).singleElement()
                .satisfies(m -> assertThat(m.textBody()).contains("Consultation", "/customer/bookings/"));
        mvc.perform(authed(get("/api/v1/account/notifications"), b.owner().token()))
                .andExpect(jsonPath("$.data[0].type").value("BOOKING_NEW"))
                .andExpect(jsonPath("$.data[0].body").value(org.hamcrest.Matchers.containsString("Asha Kumar")));

        mvc.perform(authed(get("/api/v1/account/notifications/unread-count"), c.token())).andExpect(jsonPath("$.data").value(1));
        mvc.perform(authed(post("/api/v1/account/notifications/read-all"), c.token())).andExpect(status().isOk());
        mvc.perform(authed(get("/api/v1/account/notifications/unread-count"), c.token())).andExpect(jsonPath("$.data").value(0));

        // Email off: still in the inbox, no email.
        mvc.perform(jsonRequest(put("/api/v1/account/notification-preferences"), c.token(), Map.of("emailEnabled", false,
                "smsEnabled", true, "whatsappEnabled", false, "remindersEnabled", true))).andExpect(jsonPath("$.data.emailEnabled").value(false));
        channels.clear();
        book(c, b, "10:00").andExpect(status().isOk());
        notifications.dispatch();
        assertThat(channels.emailsTo(c.email())).isEmpty();
        mvc.perform(authed(get("/api/v1/account/notifications/unread-count"), c.token())).andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void remindersGoOutOnceAndOnlyTheClosestDueOne() throws Exception {
        Biz b = approvedClinic("INSTANT");
        Person c = verifiedCustomer();
        book(c, b, "11:00").andExpect(status().isOk());
        Instant start = b.at("11:00");
        clock.advance(Duration.between(clock.instant(), start.minus(Duration.ofMinutes(30))));

        // Other tests may have bookings due at the same moment (the test order differs between machines), so count
        // this customer's reminders rather than everything the run sent.
        assertThat(bookingNotifications.sendReminders()).isPositive();
        assertThat(bookingNotifications.sendReminders()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'REMINDER'",
                Integer.class, c.id())).isEqualTo(1);
    }

    @Test
    void reviewsOnlyAfterCompletionUpdateRatingAndCanBeModerated() throws Exception {
        Biz b = approvedClinic("INSTANT");
        Person c = verifiedCustomer();
        long id = read(book(c, b, "09:30").andReturn()).path("data").path("id").asLong();
        String reviewUrl = "/api/v1/customer/appointments/" + id + "/review";
        mvc.perform(jsonRequest(post(reviewUrl), c.token(), Map.of("rating", 4, "comment", "Kind doctor")))
                .andExpect(jsonPath("$.error.code").value("REVIEW_NOT_ALLOWED"));

        clock.advance(Duration.ofDays(8));
        String owner = login(b.owner().email(), PASSWORD).accessToken();
        String customer = login(c.email(), PASSWORD).accessToken();
        mvc.perform(authed(post("/api/v1/owner/organizations/" + b.orgId() + "/appointments/" + id + "/complete"), owner))
                .andExpect(status().isOk());
        mvc.perform(authed(get("/api/v1/account/notifications"), customer)).andExpect(jsonPath("$.data[0].type").value("BOOKING_COMPLETED"));

        long reviewId = read(mvc.perform(jsonRequest(post(reviewUrl), customer, Map.of("rating", 4, "comment", "Kind doctor")))
                .andExpect(status().isOk()).andReturn()).path("data").path("id").asLong();
        mvc.perform(jsonRequest(post(reviewUrl), customer, Map.of("rating", 5))).andExpect(jsonPath("$.error.code").value("REVIEW_ALREADY_EXISTS"));
        mvc.perform(authed(get("/api/v1/customer/appointments/" + id), customer)).andExpect(jsonPath("$.data.reviewed").value(true));

        String slug = jdbc.queryForObject("SELECT slug FROM organizations WHERE id = ?", String.class, b.orgId());
        mvc.perform(get("/api/v1/public/organizations/" + slug)).andExpect(jsonPath("$.data.ratingAvg").value(4.0))
                .andExpect(jsonPath("$.data.reviewCount").value(1));
        mvc.perform(get("/api/v1/public/organizations/" + slug + "/reviews"))
                .andExpect(jsonPath("$.data[0].authorName").value("Asha K."))
                .andExpect(jsonPath("$.data[0].comment").value("Kind doctor"));

        mvc.perform(jsonRequest(post("/api/v1/owner/organizations/" + b.orgId() + "/reviews/" + reviewId + "/reply"), owner,
                Map.of("text", "Thank you!"))).andExpect(jsonPath("$.data.ownerReply").value("Thank you!"));
        Person other = verifiedCustomer();
        mvc.perform(jsonRequest(post("/api/v1/owner/organizations/" + b.orgId() + "/reviews/" + reviewId + "/reply"),
                login(other.email(), PASSWORD).accessToken(), Map.of("text", "x"))).andExpect(status().isForbidden());
        String otherToken = login(other.email(), PASSWORD).accessToken();
        for (int i = 0; i < 2; i++) {
            mvc.perform(jsonRequest(post("/api/v1/reviews/" + reviewId + "/report"), otherToken, Map.of("text", "Fake review")))
                    .andExpect(status().isOk());
        }
        Person admin = admin();
        JsonNode queue = read(mvc.perform(authed(get("/api/v1/admin/reviews"), admin.token())).andReturn()).path("data");
        assertThat(queue).anySatisfy(r -> {
            assertThat(r.path("id").asLong()).isEqualTo(reviewId);
            assertThat(r.path("reportCount").asInt()).isEqualTo(1);
        });
        mvc.perform(jsonRequest(post("/api/v1/admin/reviews/" + reviewId + "/hide"), admin.token(), Map.of("text", "Not genuine")))
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));
        mvc.perform(get("/api/v1/public/organizations/" + slug)).andExpect(jsonPath("$.data.reviewCount").value(0));
        mvc.perform(get("/api/v1/public/organizations/" + slug + "/reviews")).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(jsonRequest(post("/api/v1/admin/reviews/" + reviewId + "/restore"), admin.token(), Map.of()))
                .andExpect(jsonPath("$.data.status").value("VISIBLE"));
        mvc.perform(get("/api/v1/public/organizations/" + slug)).andExpect(jsonPath("$.data.reviewCount").value(1));
    }
}

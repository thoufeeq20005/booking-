package com.bookeasy.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import com.bookeasy.payment.PaymentService;
import com.bookeasy.support.BookingTestBase;
import com.fasterxml.jackson.databind.JsonNode;

/** Movie tickets and table bookings, from the owner's setup to the customer's booking. */
@TestPropertySource(properties = {"bookeasy.payments.demo-mode=true", "bookeasy.payments.hold-minutes=15"})
class VenueIT extends BookingTestBase {

    private static final String NEAR = "lat=13.0418&lng=80.2341";

    @Autowired
    PaymentService payments;

    private record Cinema(Person owner, long orgId, long branchId, long screenId, long movieId, long showId, Instant showAt) {
    }

    private record Restaurant(Person owner, long orgId, String slug, long branchId) {
    }

    private String venue(long orgId) {
        return "/api/v1/owner/organizations/" + orgId + "/venue";
    }

    private JsonNode data(ResultActions r) throws Exception {
        return read(r.andExpect(status().isOk()).andReturn()).path("data");
    }

    private long createOrg(Person owner, String subcategory, String name) throws Exception {
        return data(mvc.perform(jsonRequest(post("/api/v1/business/organizations"), owner.token(),
                orgBody(subcategoryId(subcategory), name)))).path("summary").path("id").asLong();
    }

    private void submitAndApprove(Person owner, long orgId) throws Exception {
        uploadMandatoryDocuments(owner, orgId);
        mvc.perform(authed(post("/api/v1/owner/organizations/" + orgId + "/submit"), owner.token())).andExpect(status().isOk());
        mvc.perform(authed(post("/api/v1/admin/organizations/" + orgId + "/approve"), admin().token())).andExpect(status().isOk());
    }

    private long addMovie(String title) throws Exception {
        return data(mvc.perform(jsonRequest(post("/api/v1/admin/movies"), admin().token(), Map.of("title", title,
                "languages", "Tamil, Telugu", "certificate", "UA", "genres", "Drama", "durationMinutes", 150,
                "releaseDate", LocalDate.now(IST).minusDays(3).toString())))).path("id").asLong();
    }

    private ResultActions addShow(Cinema c, Instant at) throws Exception {
        return mvc.perform(jsonRequest(post(venue(c.orgId()) + "/showtimes"), c.owner().token(), Map.of("screenId", c.screenId(),
                "movieId", c.movieId(), "startsAt", at.toString(), "format", "2D", "language", "Tamil",
                "prices", Map.of("CLASSIC", 150, "PRIME", "220.50"))));
    }

    /** Approved theatre: one screen (A: 10 classic, B: 8 prime) and one show tomorrow at 18:00. */
    private Cinema cinema() throws Exception {
        Person owner = verifiedCustomer();
        long orgId = createOrg(owner, "cinema", "Cinema " + System.nanoTime());
        long branchId = addBranch(owner, orgId);
        mvc.perform(jsonRequest(put(venue(orgId)), owner.token(), Map.of("features", "Dolby Atmos, Parking")))
                .andExpect(jsonPath("$.data.kind").value("CINEMA"));
        long screenId = data(mvc.perform(jsonRequest(post(venue(orgId) + "/screens"), owner.token(), Map.of("branchId", branchId,
                "name", "Screen 1", "rows", List.of(Map.of("row", "a", "tier", "CLASSIC", "seats", 10, "gapsAfter", List.of(5)),
                        Map.of("row", "B", "tier", "PRIME", "seats", 8)))))).path("screens").get(0).path("id").asLong();
        long movieId = addMovie("Kaadhal Express " + System.nanoTime());
        Instant at = ZonedDateTime.of(LocalDate.now(IST).plusDays(1), LocalTime.of(18, 0), IST).toInstant();
        Cinema c = new Cinema(owner, orgId, branchId, screenId, movieId, 0, at);
        long showId = data(addShow(c, at)).path("shows").get(0).path("id").asLong();
        submitAndApprove(owner, orgId);
        return new Cinema(owner, orgId, branchId, screenId, movieId, showId, at);
    }

    private ResultActions buyTickets(Person customer, long showId, List<String> seats) throws Exception {
        return mvc.perform(jsonRequest(post("/api/v1/customer/tickets"), customer.token(),
                Map.of("showtimeId", showId, "seats", seats)));
    }

    private JsonNode seatMap(long showId) throws Exception {
        return data(mvc.perform(get("/api/v1/public/showtimes/" + showId)));
    }

    @Test
    void customersFindAShowPickSeatsAndPay() throws Exception {
        Cinema c = cinema();

        JsonNode movies = data(mvc.perform(get("/api/v1/public/movies?" + NEAR)));
        assertThat(movies.findValues("id").stream().map(JsonNode::asLong).toList()).contains(c.movieId());
        JsonNode page = data(mvc.perform(get("/api/v1/public/movies/" + c.movieId() + "?" + NEAR)));
        assertThat(page.path("theatres").get(0).path("organizationId").asLong()).isEqualTo(c.orgId());
        assertThat(page.path("theatres").get(0).path("shows").get(0).path("seatsLeft").asInt()).isEqualTo(18);
        assertThat(page.path("theatres").get(0).path("shows").get(0).path("fromPrice").decimalValue()).isEqualByComparingTo("150");

        JsonNode map = seatMap(c.showId());
        assertThat(map.path("rows").get(0).path("row").asText()).isEqualTo("A");
        assertThat(map.path("rows").get(0).path("gapsAfter").get(0).asInt()).isEqualTo(5);
        assertThat(map.path("bookable").asBoolean()).isTrue();

        Person ana = verifiedCustomer();
        JsonNode booking = data(buyTickets(ana, c.showId(), List.of("b1", "A2")));
        assertThat(booking.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(booking.path("servicePrice").decimalValue()).isEqualByComparingTo("370.50");
        assertThat(booking.path("serviceName").asText()).endsWith("(2D) · B1, A2");
        assertThat(booking.path("screenName").asText()).isEqualTo("Screen 1");
        assertThat(booking.path("seats").toString()).isEqualTo("[\"A2\",\"B1\"]");
        assertThat(seatMap(c.showId()).path("taken").size()).isEqualTo(2);

        Person ben = verifiedCustomer();
        buyTickets(ben, c.showId(), List.of("A3", "A2")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SEATS_UNAVAILABLE"));
        buyTickets(ben, c.showId(), List.of("Z9")).andExpect(status().isBadRequest());
        buyTickets(ben, c.showId(), List.of("A3", "a3")).andExpect(status().isBadRequest());
        assertThat(seatMap(c.showId()).path("taken").size()).isEqualTo(2); // the failed attempt left nothing behind

        long id = booking.path("id").asLong();
        mvc.perform(authed(post("/api/v1/customer/appointments/" + id + "/payment-order"), ana.token())).andExpect(status().isOk());
        mvc.perform(jsonRequest(post("/api/v1/customer/appointments/" + id + "/demo-payment"), ana.token(), Map.of("method", "upi")))
                .andExpect(status().isOk());
        mvc.perform(jsonRequest(post("/api/v1/customer/appointments/" + id + "/reschedule"), ana.token(),
                Map.of("startsAt", c.showAt().plus(Duration.ofHours(1)).toString())))
                .andExpect(jsonPath("$.error.code").value("BOOKING_NOT_CHANGEABLE"));
        mvc.perform(jsonRequest(post("/api/v1/customer/appointments/" + id + "/cancel"), ana.token(), Map.of("reason", "Busy")))
                .andExpect(status().isOk());
        assertThat(seatMap(c.showId()).path("taken").size()).isZero();
        buyTickets(ben, c.showId(), List.of("A2")).andExpect(status().isOk());
    }

    @Test
    void unpaidSeatsGoBackOnSaleWhenTheHoldExpires() throws Exception {
        Cinema c = cinema();
        buyTickets(verifiedCustomer(), c.showId(), List.of("A5", "A6")).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(16));
        try {
            payments.expireStale();
            assertThat(seatMap(c.showId()).path("taken").size()).isZero();
        } finally {
            clock.reset();
        }
    }

    @Test
    void ownersScheduleShowsWithoutClashes() throws Exception {
        Cinema c = cinema();
        addShow(c, c.showAt().plus(Duration.ofMinutes(120))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("OVERLAP"));
        addShow(c, c.showAt().plus(Duration.ofMinutes(165))).andExpect(status().isOk());
        mvc.perform(jsonRequest(post(venue(c.orgId()) + "/showtimes"), c.owner().token(), Map.of("screenId", c.screenId(),
                "movieId", c.movieId(), "startsAt", c.showAt().plus(Duration.ofDays(1)).toString(), "language", "Tamil",
                "prices", Map.of("CLASSIC", 150)))).andExpect(jsonPath("$.error.fieldErrors[0].code").value("MISSING"));

        buyTickets(verifiedCustomer(), c.showId(), List.of("A1")).andExpect(status().isOk());
        mvc.perform(authed(delete(venue(c.orgId()) + "/showtimes/" + c.showId()), c.owner().token()))
                .andExpect(status().isConflict());
        mvc.perform(authed(delete(venue(c.orgId()) + "/screens/" + c.screenId()), c.owner().token()))
                .andExpect(status().isConflict());

        // Another owner can't see or change this theatre.
        Person other = verifiedCustomer();
        createOrg(other, "cinema", "Other " + System.nanoTime());
        mvc.perform(authed(get(venue(c.orgId())), other.token())).andExpect(status().isNotFound());

        // A retired movie can't be scheduled or found.
        mvc.perform(jsonRequest(post("/api/v1/admin/movies/" + c.movieId() + "/active"), admin().token(), Map.of("active", false)))
                .andExpect(jsonPath("$.data.active").value(false));
        mvc.perform(get("/api/v1/public/movies/" + c.movieId() + "?" + NEAR)).andExpect(status().isNotFound());
    }

    @Test
    void aClinicHasNoVenueSettings() throws Exception {
        Biz clinic = approvedClinic("INSTANT");
        mvc.perform(authed(get(venue(clinic.orgId())), clinic.owner().token())).andExpect(jsonPath("$.data.kind").doesNotExist());
        mvc.perform(jsonRequest(put(venue(clinic.orgId())), clinic.owner().token(), Map.of("cuisines", "x")))
                .andExpect(status().isNotFound());
    }

    // ----- dining -----

    /** Approved restaurant, Mon–Sat 09:00–18:00 (break 13–14), seating 6 guests per sitting. */
    private Restaurant restaurant() throws Exception {
        Person owner = verifiedCustomer();
        String name = "Dosa Corner " + System.nanoTime();
        long orgId = createOrg(owner, "restaurant", name);
        long branchId = addBranch(owner, orgId);
        mvc.perform(jsonRequest(put(venue(orgId)), owner.token(), Map.of("cuisines", "South Indian, Chettinad")))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("capacityPerSlot"));
        mvc.perform(jsonRequest(put(venue(orgId)), owner.token(), Map.of("cuisines", "South Indian, Chettinad",
                "costForTwo", 600, "vegOnly", true, "capacityPerSlot", 6, "offerText", "10% off before noon",
                "features", "Air conditioned, Family seating"))).andExpect(status().isOk());
        mvc.perform(jsonRequest(post(venue(orgId) + "/menu"), owner.token(), Map.of("section", "Tiffin", "name", "Ghee roast",
                "price", 120, "bestseller", true))).andExpect(status().isOk());
        JsonNode setup = data(mvc.perform(jsonRequest(post(venue(orgId) + "/menu"), owner.token(), Map.of("section", "Meals",
                "name", "South Indian thali", "price", 220))));
        assertThat(setup.path("menu").size()).isEqualTo(2);
        submitAndApprove(owner, orgId);
        String slug = jdbc.queryForObject("SELECT slug FROM organizations WHERE id = ?", String.class, orgId);
        return new Restaurant(owner, orgId, slug, branchId);
    }

    private ResultActions reserve(Person customer, Restaurant r, Instant at, int guests) throws Exception {
        return mvc.perform(jsonRequest(post("/api/v1/customer/table-bookings"), customer.token(), Map.of(
                "branchId", r.branchId(), "startsAt", at.toString(), "partySize", guests, "note", "Window seat")));
    }

    @Test
    void tablesAreBookedFreeUntilTheRestaurantIsFull() throws Exception {
        Restaurant r = restaurant();
        JsonNode list = data(mvc.perform(get("/api/v1/public/dining?" + NEAR + "&q=chettinad")));
        assertThat(list.findValues("organizationId").stream().map(JsonNode::asLong).toList()).contains(r.orgId());
        JsonNode details = data(mvc.perform(get("/api/v1/public/dining/" + r.slug())));
        assertThat(details.path("menu").get(0).path("name").asText()).isEqualTo("Tiffin");
        assertThat(details.path("features").size()).isEqualTo(2);
        assertThat(details.path("maxPartySize").asInt()).isEqualTo(6);

        LocalDate monday = LocalDate.now(IST).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Instant ten = ZonedDateTime.of(monday, LocalTime.of(10, 0), IST).toInstant();
        String slots = "/api/v1/public/dining/branches/" + r.branchId() + "/slots?date=" + monday;
        JsonNode day = data(mvc.perform(get(slots + "&partySize=4")));
        List<String> times = new ArrayList<>();
        day.forEach(s -> times.add(s.path("time").asText()));
        assertThat(times).contains("09:00", "17:00").doesNotContain("13:00", "17:30");

        JsonNode first = data(reserve(verifiedCustomer(), r, ten, 4));
        assertThat(first.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(first.path("totalAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(first.path("partySize").asInt()).isEqualTo(4);
        assertThat(first.path("qrPayload").asText()).isNotBlank();

        reserve(verifiedCustomer(), r, ten.plus(Duration.ofMinutes(30)), 4).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TABLE_UNAVAILABLE"));
        reserve(verifiedCustomer(), r, ten.plus(Duration.ofMinutes(30)), 2).andExpect(status().isOk());
        reserve(verifiedCustomer(), r, ten.plus(Duration.ofMinutes(7)), 1).andExpect(status().isConflict()); // not a slot
        reserve(verifiedCustomer(), r, ten.plus(Duration.ofMinutes(90)), 4).andExpect(status().isOk()); // first table freed

        JsonNode full = data(mvc.perform(get(slots + "&partySize=1")));
        full.forEach(s -> {
            if (s.path("time").asText().equals("10:30")) {
                assertThat(s.path("available").asBoolean()).isFalse();
            }
        });

        // Cancelling frees the table again.
        Person late = verifiedCustomer();
        JsonNode mine = data(reserve(late, r, ZonedDateTime.of(monday, LocalTime.of(16, 0), IST).toInstant(), 6));
        mvc.perform(jsonRequest(post("/api/v1/customer/appointments/" + mine.path("id").asLong() + "/cancel"), late.token(),
                Map.of("reason", "Plans changed"))).andExpect(status().isOk());
        reserve(verifiedCustomer(), r, ZonedDateTime.of(monday, LocalTime.of(16, 0), IST).toInstant(), 6)
                .andExpect(status().isOk());
    }

    @Test
    void ownersManageTheirMenu() throws Exception {
        Restaurant r = restaurant();
        long dish = data(mvc.perform(authed(get(venue(r.orgId())), r.owner().token()))).path("menu").get(0).path("id").asLong();
        mvc.perform(authed(delete(venue(r.orgId()) + "/menu/" + dish), r.owner().token()))
                .andExpect(jsonPath("$.data.menu.length()").value(1));
        mvc.perform(jsonRequest(post(venue(r.orgId()) + "/menu"), r.owner().token(), Map.of("section", "Meals", "name", "Thali",
                "price", -5))).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(venue(r.orgId()) + "/screens"), r.owner().token(), Map.of("branchId", r.branchId(),
                "name", "Hall", "rows", List.of(Map.of("row", "A", "tier", "CLASSIC", "seats", 5)))))
                .andExpect(status().isNotFound());
    }
}

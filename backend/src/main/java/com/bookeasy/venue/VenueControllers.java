package com.bookeasy.venue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.context.event.EventListener;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.appointment.AppointmentChanged;
import com.bookeasy.appointment.AppointmentView;
import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** HTTP endpoints for movies and dining, plus the listener that frees seats when a ticket booking ends. */
public final class VenueControllers {

    private VenueControllers() {
    }

    @RestController
    @RequestMapping("/api/v1/public")
    @Tag(name = "Movies & dining")
    @SecurityRequirements
    public static class PublicVenueController {

        private final CinemaService cinema;
        private final DiningService dining;

        public PublicVenueController(CinemaService cinema, DiningService dining) {
            this.cinema = cinema;
            this.dining = dining;
        }

        @GetMapping("/movies")
        @Operation(summary = "Movies with shows in the next week near a point")
        public ApiResponse<List<CinemaService.NowShowing>> movies(@RequestParam double lat, @RequestParam double lng,
                                                                 @RequestParam(defaultValue = "25") double radiusKm) {
            return ApiResponse.ok(cinema.nowShowing(lat, lng, radiusKm));
        }

        @GetMapping("/movies/upcoming")
        @Operation(summary = "Movies releasing soon")
        public ApiResponse<List<CinemaService.Movie>> upcoming() {
            return ApiResponse.ok(cinema.upcoming());
        }

        @GetMapping("/movies/{id}")
        @Operation(summary = "A movie, the days it plays nearby and each theatre's shows on one day")
        public ApiResponse<CinemaService.MoviePage> movie(@PathVariable long id, @RequestParam double lat, @RequestParam double lng,
                                                          @RequestParam(defaultValue = "25") double radiusKm,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
            return ApiResponse.ok(cinema.movie(id, date, lat, lng, radiusKm));
        }

        @GetMapping("/showtimes/{id}")
        @Operation(summary = "Seat map of a show: rows, tiers, prices and taken seats")
        public ApiResponse<CinemaService.SeatMap> seats(@PathVariable long id) {
            return ApiResponse.ok(cinema.seatMap(id));
        }

        @GetMapping("/theatres/{orgId}/schedule")
        @Operation(summary = "Upcoming shows at one theatre")
        public ApiResponse<List<CinemaService.TheatreShow>> schedule(@PathVariable long orgId) {
            return ApiResponse.ok(cinema.theatreSchedule(orgId));
        }

        @GetMapping("/dining")
        @Operation(summary = "Restaurants and cafés near a point")
        public ApiResponse<List<DiningService.Restaurant>> restaurants(@RequestParam double lat, @RequestParam double lng,
                                                                      @RequestParam(defaultValue = "15") double radiusKm,
                                                                      @RequestParam(required = false) String q) {
            return ApiResponse.ok(dining.nearby(lat, lng, radiusKm, q));
        }

        @GetMapping("/dining/{slug}")
        @Operation(summary = "Cuisines, cost for two, offers and menu of a restaurant")
        public ApiResponse<DiningService.DiningDetails> details(@PathVariable String slug) {
            return ApiResponse.ok(dining.details(slug));
        }

        @GetMapping("/dining/branches/{branchId}/slots")
        @Operation(summary = "Table times on a date for a party size")
        public ApiResponse<List<DiningService.TableSlot>> slots(@PathVariable long branchId,
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                               @RequestParam(defaultValue = "2") int partySize) {
            return ApiResponse.ok(dining.slots(branchId, date, partySize));
        }
    }

    public record TicketRequest(@NotNull Long showtimeId, @NotEmpty @Size(max = 10) List<@NotNull @Size(max = 8) String> seats) {
    }

    public record TableRequest(@NotNull Long branchId, @NotNull Instant startsAt, @NotNull @Min(1) @Max(10) Integer partySize,
                               @Size(max = 500) String note, Long offerId) {
    }

    public record BillRequest(@NotNull Long branchId, @NotNull java.math.BigDecimal amount, Long bookingId) {
    }

    @RestController
    @RequestMapping("/api/v1/customer")
    @Tag(name = "Movies & dining")
    public static class CustomerVenueController {

        private final CinemaService cinema;
        private final DiningService dining;

        public CustomerVenueController(CinemaService cinema, DiningService dining) {
            this.cinema = cinema;
            this.dining = dining;
        }

        @PostMapping("/tickets")
        @Operation(summary = "Book seats for a show (goes to payment when online payment is on)")
        public ApiResponse<AppointmentView> tickets(@Valid @RequestBody TicketRequest req) {
            return ApiResponse.ok(cinema.book(CurrentUser.require().id(), req.showtimeId(), req.seats()));
        }

        @PostMapping("/table-bookings")
        @Operation(summary = "Book a table (free, confirmed instantly unless the restaurant approves bookings)")
        public ApiResponse<AppointmentView> table(@Valid @RequestBody TableRequest req) {
            return ApiResponse.ok(dining.reserve(CurrentUser.require().id(), req.branchId(), req.startsAt(),
                    req.partySize(), req.note(), req.offerId()));
        }

        @GetMapping("/dining/bill-quote")
        @Operation(summary = "What paying a restaurant bill costs after the offer and the cover charge already paid")
        public ApiResponse<DiningService.BillQuote> quote(@RequestParam long branchId, @RequestParam java.math.BigDecimal amount,
                                                         @RequestParam(required = false) Long bookingId) {
            return ApiResponse.ok(dining.quote(CurrentUser.require().id(), branchId, amount, bookingId));
        }

        @PostMapping("/dining/bills")
        @Operation(summary = "Pay a restaurant bill (goes to payment when something is left to pay)")
        public ApiResponse<AppointmentView> payBill(@Valid @RequestBody BillRequest req) {
            return ApiResponse.ok(dining.payBill(CurrentUser.require().id(), req.branchId(), req.amount(), req.bookingId()));
        }
    }

    @RestController
    @RequestMapping("/api/v1/owner")
    @Tag(name = "Owner – Movies & dining")
    public static class OwnerVenueController {

        private final VenueSetupService setup;

        public OwnerVenueController(VenueSetupService setup) {
            this.setup = setup;
        }

        private static long me() {
            return CurrentUser.require().id();
        }

        @GetMapping("/movies")
        @Operation(summary = "Movies a theatre can schedule")
        public ApiResponse<List<VenueSetupService.CatalogMovie>> movies() {
            return ApiResponse.ok(setup.catalog(true));
        }

        @GetMapping("/organizations/{orgId}/venue")
        @Operation(summary = "Venue settings, menu, screens and upcoming shows")
        public ApiResponse<VenueSetupService.VenueSetup> get(@PathVariable long orgId) {
            return ApiResponse.ok(setup.setup(me(), orgId));
        }

        @PutMapping("/organizations/{orgId}/venue")
        @Operation(summary = "Save venue settings (creates them the first time)")
        public ApiResponse<VenueSetupService.VenueSetup> save(@PathVariable long orgId,
                                                             @RequestBody VenueSetupService.ProfileRequest req) {
            return ApiResponse.ok(setup.saveProfile(me(), orgId, req));
        }

        @PostMapping("/organizations/{orgId}/venue/offers")
        @Operation(summary = "Add a discount on the final bill")
        public ApiResponse<VenueSetupService.VenueSetup> addOffer(@PathVariable long orgId,
                                                                 @RequestBody VenueSetupService.OfferRequest req) {
            return ApiResponse.ok(setup.addOffer(me(), orgId, req));
        }

        @DeleteMapping("/organizations/{orgId}/venue/offers/{offerId}")
        @Operation(summary = "Remove an offer (bookings that chose it keep it)")
        public ApiResponse<VenueSetupService.VenueSetup> deleteOffer(@PathVariable long orgId, @PathVariable long offerId) {
            return ApiResponse.ok(setup.deleteOffer(me(), orgId, offerId));
        }

        @PostMapping("/organizations/{orgId}/venue/menu")
        @Operation(summary = "Add a dish to the menu")
        public ApiResponse<VenueSetupService.VenueSetup> addDish(@PathVariable long orgId,
                                                                @RequestBody VenueSetupService.MenuRequest req) {
            return ApiResponse.ok(setup.addMenuItem(me(), orgId, req));
        }

        @DeleteMapping("/organizations/{orgId}/venue/menu/{itemId}")
        @Operation(summary = "Remove a dish")
        public ApiResponse<VenueSetupService.VenueSetup> deleteDish(@PathVariable long orgId, @PathVariable long itemId) {
            return ApiResponse.ok(setup.deleteMenuItem(me(), orgId, itemId));
        }

        @PostMapping("/organizations/{orgId}/venue/screens")
        @Operation(summary = "Add a screen with its seat layout")
        public ApiResponse<VenueSetupService.VenueSetup> addScreen(@PathVariable long orgId,
                                                                  @RequestBody VenueSetupService.ScreenRequest req) {
            return ApiResponse.ok(setup.addScreen(me(), orgId, req));
        }

        @DeleteMapping("/organizations/{orgId}/venue/screens/{screenId}")
        @Operation(summary = "Remove a screen and its shows (not while tickets are sold for upcoming shows)")
        public ApiResponse<VenueSetupService.VenueSetup> deleteScreen(@PathVariable long orgId, @PathVariable long screenId) {
            return ApiResponse.ok(setup.deleteScreen(me(), orgId, screenId));
        }

        @PostMapping("/organizations/{orgId}/venue/showtimes")
        @Operation(summary = "Schedule a show")
        public ApiResponse<VenueSetupService.VenueSetup> addShow(@PathVariable long orgId,
                                                                @RequestBody VenueSetupService.ShowtimeRequest req) {
            return ApiResponse.ok(setup.addShowtime(me(), orgId, req));
        }

        @DeleteMapping("/organizations/{orgId}/venue/showtimes/{showId}")
        @Operation(summary = "Remove a show that has no tickets sold")
        public ApiResponse<VenueSetupService.VenueSetup> deleteShow(@PathVariable long orgId, @PathVariable long showId) {
            return ApiResponse.ok(setup.deleteShowtime(me(), orgId, showId));
        }
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    @RestController
    @RequestMapping("/api/v1/admin/movies")
    @Tag(name = "Admin – Movies")
    public static class AdminMovieController {

        private final VenueSetupService setup;

        public AdminMovieController(VenueSetupService setup) {
            this.setup = setup;
        }

        @GetMapping
        @Operation(summary = "The movie catalogue, including retired titles")
        public ApiResponse<List<VenueSetupService.CatalogMovie>> list() {
            return ApiResponse.ok(setup.catalog(false));
        }

        @PostMapping
        @Operation(summary = "Add a movie theatres can schedule")
        public ApiResponse<VenueSetupService.CatalogMovie> add(@RequestBody VenueSetupService.MovieRequest req) {
            return ApiResponse.ok(setup.addMovie(req));
        }

        @PostMapping(value = "/{id}/poster", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Upload or replace a movie's poster photo (JPEG, PNG or WebP, up to 5 MB)")
        public ApiResponse<VenueSetupService.CatalogMovie> poster(@PathVariable long id,
                                                                 @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
            return ApiResponse.ok(setup.setPoster(CurrentUser.require().id(), id, file));
        }

        @PostMapping("/{id}/active")
        @Operation(summary = "Show or retire a movie")
        public ApiResponse<VenueSetupService.CatalogMovie> active(@PathVariable long id, @Valid @RequestBody ActiveRequest req) {
            return ApiResponse.ok(setup.setMovieActive(id, req.active()));
        }
    }

    /** Seats go back on sale as soon as a ticket booking is cancelled, declined or its payment hold expires. */
    @Component
    public static class SeatRelease {

        private final NamedParameterJdbcTemplate jdbc;

        public SeatRelease(NamedParameterJdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @EventListener
        public void on(AppointmentChanged e) {
            if (e.kind() == AppointmentChanged.Kind.CANCELLED || e.kind() == AppointmentChanged.Kind.DECLINED
                    || e.kind() == AppointmentChanged.Kind.EXPIRED) {
                jdbc.update("DELETE FROM ticket_seats WHERE appointment_id = :id", Map.of("id", e.appointmentId()));
            }
        }
    }
}

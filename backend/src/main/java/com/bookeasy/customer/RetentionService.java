package com.bookeasy.customer;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.platform.PlatformConfigService;

/**
 * Data retention. Bookings older than the history window keep their money and status (needed for accounts and
 * tax) but lose free-text personal notes; read notifications, sent emails, webhook ids and rate-limit counters are
 * deleted after they stop being useful. Each run is logged.
 */
@Service
public class RetentionService {

    private final JdbcTemplate jdbc;
    private final PlatformConfigService settings;
    private final Clock clock;

    public RetentionService(JdbcTemplate jdbc, PlatformConfigService settings, Clock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.clock = clock;
    }

    public record Run(long id, Instant startedAt, Instant finishedAt, int appointmentsAnonymised, int notificationsDeleted,
                      int outboxDeleted, int webhookEventsDeleted, int rateLimitsDeleted) {
    }

    /** Nightly at 03:30 India time. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
    public void runScheduled() {
        run();
    }

    @Transactional
    public Run run() {
        Instant now = clock.instant();
        Instant historyCutoff = now.atZone(ZoneId.of("Asia/Kolkata"))
                .minusYears(settings.integer("retention.booking_history_years")).toInstant();
        int anonymised = jdbc.update("""
                UPDATE appointments SET customer_note = NULL, cancel_reason = NULL, patient_name = NULL
                WHERE starts_at < ? AND (customer_note IS NOT NULL OR cancel_reason IS NOT NULL OR patient_name IS NOT NULL)""",
                Timestamp.from(historyCutoff));
        int notifications = jdbc.update("DELETE FROM notifications WHERE created_at < ? AND read_at IS NOT NULL",
                Timestamp.from(now.minus(Duration.ofDays(180))));
        int outbox = jdbc.update("DELETE FROM notification_outbox WHERE status IN ('SENT', 'SKIPPED', 'FAILED') AND created_at < ?",
                Timestamp.from(now.minus(Duration.ofDays(90))));
        int webhooks = jdbc.update("DELETE FROM webhook_events WHERE received_at < ?", Timestamp.from(now.minus(Duration.ofDays(90))));
        int limits = jdbc.update("DELETE FROM rate_limit_counters WHERE window_start < ?", Timestamp.from(now.minus(Duration.ofDays(2))));
        jdbc.update("""
                INSERT INTO data_retention_runs (started_at, finished_at, appointments_anonymised, notifications_deleted,
                  outbox_deleted, webhook_events_deleted, rate_limits_deleted) VALUES (?, ?, ?, ?, ?, ?, ?)""",
                Timestamp.from(now), Timestamp.from(clock.instant()), anonymised, notifications, outbox, webhooks, limits);
        return runs().get(0);
    }

    @Transactional(readOnly = true)
    public List<Run> runs() {
        return jdbc.query("SELECT * FROM data_retention_runs ORDER BY id DESC LIMIT 30", (rs, i) -> new Run(rs.getLong("id"),
                rs.getTimestamp("started_at").toInstant(), rs.getTimestamp("finished_at").toInstant(),
                rs.getInt("appointments_anonymised"), rs.getInt("notifications_deleted"), rs.getInt("outbox_deleted"),
                rs.getInt("webhook_events_deleted"), rs.getInt("rate_limits_deleted")));
    }
}

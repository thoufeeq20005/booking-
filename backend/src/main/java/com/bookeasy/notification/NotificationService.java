package com.bookeasy.notification;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bookeasy.common.config.BookEasyProperties;
import com.bookeasy.notification.channel.EmailSender;
import com.bookeasy.notification.channel.EmailSender.EmailMessage;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;

/**
 * Every user-facing notification goes through here: an in-app inbox row plus outbox rows for the channels the
 * person allows, all written in the caller's transaction. The dispatcher sends the outbox after commit and retries
 * failures with backoff, so nothing is lost if a provider is down.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    static final int MAX_ATTEMPTS = 5;

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final MessageSource messages;
    private final EmailSender email;
    private final BookEasyProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final java.util.concurrent.ExecutorService dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "notification-dispatch");
        t.setDaemon(true);
        return t;
    });
    private final java.util.concurrent.atomic.AtomicBoolean dispatchQueued = new java.util.concurrent.atomic.AtomicBoolean();

    public NotificationService(JdbcTemplate jdbc, UserRepository users, MessageSource messages, EmailSender email,
                               BookEasyProperties properties, TransactionTemplate tx, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.messages = messages;
        this.email = email;
        this.properties = properties;
        this.tx = tx;
        this.clock = clock;
    }

    public record Preferences(boolean emailEnabled, boolean smsEnabled, boolean whatsappEnabled, boolean remindersEnabled) {
    }

    /**
     * Queues a notification of {@code type}. Text comes from {@code notify.<type>.title|body} in the user's language;
     * {@code args} fill the placeholders of both. {@code link} is an app path such as /customer/bookings/42.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notify(long userId, String type, Object[] args, String link) {
        notify(userId, type, args, link, null);
    }

    /**
     * As {@link #notify(long, String, Object[], String)}, with an e-ticket email: {@code ticket} renders it in the
     * recipient's language (HTML, text and the booking pass whose QR is attached when sent).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notify(long userId, String type, Object[] args, String link,
                       java.util.function.Function<Locale, TicketEmail.Ticket> ticket) {
        User user = users.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        Locale locale = Locale.forLanguageTag(user.getPreferredLanguage());
        String title = messages.getMessage("notify." + type + ".title", args, locale);
        String body = messages.getMessage("notify." + type + ".body", args, locale);
        Instant now = clock.instant();
        jdbc.update("INSERT INTO notifications (user_id, type, title, body, link, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                userId, type, title, body, link, Timestamp.from(now));
        Preferences prefs = preferences(userId);
        if (type.equals("REMINDER") && !prefs.remindersEnabled()) {
            return;
        }
        if (prefs.emailEnabled() && user.getEmail() != null) {
            TicketEmail.Ticket t = ticket == null ? null : ticket.apply(locale);
            String text = t != null ? t.text()
                    : body + (link == null ? "" : "\n\n" + properties.frontendUrl() + link) + "\n\n– BookEasy";
            jdbc.update("""
                    INSERT INTO notification_outbox (user_id, channel, destination, type, subject, body, html_body, qr_payload,
                                                     next_attempt_at, created_at)
                    VALUES (?, 'EMAIL', ?, ?, ?, ?, ?, ?, ?, ?)""", userId, user.getEmail(), type, title, text,
                    t == null ? null : t.html(), t == null ? null : t.qrPayload(), Timestamp.from(now), Timestamp.from(now));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchSoon();
                }
            });
        }
        // SMS and WhatsApp need DLT/Meta-approved templates per message; they join the outbox once configured.
    }

    @Transactional(readOnly = true)
    public Preferences preferences(long userId) {
        List<Preferences> rows = jdbc.query("SELECT * FROM notification_preferences WHERE user_id = ?",
                (rs, i) -> new Preferences(rs.getBoolean("email_enabled"), rs.getBoolean("sms_enabled"),
                        rs.getBoolean("whatsapp_enabled"), rs.getBoolean("reminders_enabled")), userId);
        return rows.isEmpty() ? new Preferences(true, true, false, true) : rows.get(0);
    }

    @Transactional
    public Preferences savePreferences(long userId, Preferences p) {
        jdbc.update("""
                INSERT INTO notification_preferences (user_id, email_enabled, sms_enabled, whatsapp_enabled, reminders_enabled, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE email_enabled = VALUES(email_enabled), sms_enabled = VALUES(sms_enabled),
                  whatsapp_enabled = VALUES(whatsapp_enabled), reminders_enabled = VALUES(reminders_enabled), updated_at = VALUES(updated_at)""",
                userId, p.emailEnabled(), p.smsEnabled(), p.whatsappEnabled(), p.remindersEnabled(), Timestamp.from(clock.instant()));
        return preferences(userId);
    }

    // ----- inbox -----

    public record InboxItem(long id, String type, String title, String body, String link, Instant readAt, Instant createdAt) {
    }

    @Transactional(readOnly = true)
    public List<InboxItem> inbox(long userId, int limit) {
        return jdbc.query("SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?",
                (rs, i) -> new InboxItem(rs.getLong("id"), rs.getString("type"), rs.getString("title"), rs.getString("body"),
                        rs.getString("link"), rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()), userId, Math.min(Math.max(limit, 1), 100));
    }

    @Transactional(readOnly = true)
    public long unread(long userId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL", Long.class, userId);
        return n == null ? 0 : n;
    }

    @Transactional
    public void markRead(long userId, Long id) {
        Timestamp now = Timestamp.from(clock.instant());
        if (id == null) {
            jdbc.update("UPDATE notifications SET read_at = ? WHERE user_id = ? AND read_at IS NULL", now, userId);
        } else {
            jdbc.update("UPDATE notifications SET read_at = ? WHERE id = ? AND user_id = ? AND read_at IS NULL", now, id, userId);
        }
    }

    // ----- dispatcher -----

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT30S")
    public void dispatchScheduled() {
        dispatch();
    }

    /** Sends due outbox rows; a failure is retried after 1, 2, 4, 8 minutes, then marked FAILED. */
    /**
     * Sends pending messages on a background thread, so the request that queued them never waits for the email
     * provider (a slow or unreachable mail server used to hold bookings for 10 s per message). Requests made while a
     * run is queued share it; the scheduled run retries anything left.
     */
    void dispatchSoon() {
        if (dispatchQueued.compareAndSet(false, true)) {
            try {
                dispatcher.execute(() -> {
                    dispatchQueued.set(false);
                    try {
                        dispatch();
                    } catch (RuntimeException e) {
                        log.warn("Background notification dispatch failed", e);
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                dispatchQueued.set(false);
            }
        }
    }

    @jakarta.annotation.PreDestroy
    void stopDispatcher() {
        dispatcher.shutdown();
    }

    /** The outbox row as an email; a ticket gets its booking pass QR drawn now (inline image, plus its web address). */
    private EmailMessage message(Map<String, Object> row) {
        String to = (String) row.get("destination");
        String subject = (String) row.get("subject");
        String text = (String) row.get("body");
        String html = (String) row.get("html_body");
        String pass = (String) row.get("qr_payload");
        if (html == null) {
            return new EmailMessage(to, subject, text);
        }
        if (pass == null) {
            return new EmailMessage(to, subject, text, html, null, null);
        }
        String site = properties.frontendUrl() == null ? "" : properties.frontendUrl().replaceAll("/+$", "");
        byte[] png = com.bookeasy.appointment.QrImages.png(com.bookeasy.appointment.QrImages.passLink(site, pass), 360);
        String url = site + "/api/v1/public/passes/" + java.net.URLEncoder.encode(pass, java.nio.charset.StandardCharsets.UTF_8) + "/qr.png";
        return new EmailMessage(to, subject, text, html, png, url);
    }

    public int dispatch() {
        Integer sent = tx.execute(s -> {
            Instant now = clock.instant();
            List<Map<String, Object>> due = jdbc.queryForList("""
                    SELECT id, channel, destination, subject, body, html_body, qr_payload, attempts FROM notification_outbox
                    WHERE status = 'PENDING' AND next_attempt_at <= ? ORDER BY id LIMIT 50 FOR UPDATE SKIP LOCKED""",
                    Timestamp.from(now));
            int ok = 0;
            for (Map<String, Object> row : due) {
                long id = ((Number) row.get("id")).longValue();
                int attempts = ((Number) row.get("attempts")).intValue() + 1;
                try {
                    if ("EMAIL".equals(row.get("channel"))) {
                        email.send(message(row));
                    }
                    jdbc.update("UPDATE notification_outbox SET status = 'SENT', attempts = ?, sent_at = ?, last_error = NULL WHERE id = ?",
                            attempts, Timestamp.from(now), id);
                    ok++;
                } catch (RuntimeException e) {
                    log.warn("Notification {} failed (attempt {})", id, attempts, e);
                    boolean giveUp = attempts >= MAX_ATTEMPTS;
                    jdbc.update("UPDATE notification_outbox SET status = ?, attempts = ?, last_error = ?, next_attempt_at = ? WHERE id = ?",
                            giveUp ? "FAILED" : "PENDING", attempts, e.getMessage() == null ? "error" : e.getMessage().substring(0,
                                    Math.min(e.getMessage().length(), 480)),
                            Timestamp.from(now.plus(Duration.ofMinutes(1L << (attempts - 1)))), id);
                }
            }
            return ok;
        });
        return sent == null ? 0 : sent;
    }
}

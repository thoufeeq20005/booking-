package com.bookeasy.appointment;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;

/**
 * The database-level double-booking guard: a booking claims every 5-minute unit of [start, blockedUntil) on its
 * resource. The primary key on (resource_key, slot_start) lets exactly one of two overlapping bookings commit,
 * whatever the timing and however many API instances run.
 */
@Component
public class SlotLocks {

    static final Duration UNIT = Duration.ofMinutes(5);

    private final JdbcTemplate jdbc;

    public SlotLocks(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static String resourceKey(Long staffId, long branchId, long serviceId) {
        return staffId != null ? "staff:" + staffId : "svc:" + branchId + ":" + serviceId;
    }

    static List<Instant> units(Instant from, Instant to) {
        Instant t = from.truncatedTo(ChronoUnit.MINUTES);
        t = t.minus(Duration.ofMinutes(t.atZone(java.time.ZoneOffset.UTC).getMinute() % 5));
        List<Instant> out = new ArrayList<>();
        for (; t.isBefore(to); t = t.plus(UNIT)) {
            out.add(t);
        }
        return out;
    }

    /** @throws ApiException SLOT_UNAVAILABLE if any unit is already taken */
    @Transactional(propagation = Propagation.MANDATORY)
    public void claim(String resourceKey, Instant from, Instant to, long appointmentId) {
        try {
            jdbc.batchUpdate("INSERT INTO appointment_slot_locks (resource_key, slot_start, appointment_id) VALUES (?, ?, ?)",
                    units(from, to), 100, (ps, unit) -> {
                        ps.setString(1, resourceKey);
                        ps.setTimestamp(2, Timestamp.from(unit));
                        ps.setLong(3, appointmentId);
                    });
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.SLOT_UNAVAILABLE);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(long appointmentId) {
        jdbc.update("DELETE FROM appointment_slot_locks WHERE appointment_id = ?", appointmentId);
    }
}

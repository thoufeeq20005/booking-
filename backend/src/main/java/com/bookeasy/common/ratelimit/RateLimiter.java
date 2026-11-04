package com.bookeasy.common.ratelimit;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;

/**
 * Fixed-window counter stored in MySQL so limits hold across every API instance.
 * Each hit commits in its own transaction: a failing request must still count.
 */
@Service
public class RateLimiter {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RateLimiter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Records a hit and throws {@link ErrorCode#RATE_LIMITED} once {@code limit} is exceeded within the window. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void hit(String bucket, int limit, Duration window) {
        long windowMillis = window.toMillis();
        Instant now = clock.instant();
        Instant windowStart = Instant.ofEpochMilli(now.toEpochMilli() - now.toEpochMilli() % windowMillis);
        String key = bucket.length() > 191 ? bucket.substring(0, 191) : bucket;
        Timestamp ts = Timestamp.from(windowStart);

        jdbc.update("INSERT INTO rate_limit_counters (bucket_key, window_start, hits) VALUES (?, ?, 1) "
                + "ON DUPLICATE KEY UPDATE hits = hits + 1", key, ts);
        Integer hits = jdbc.queryForObject(
                "SELECT hits FROM rate_limit_counters WHERE bucket_key = ? AND window_start = ?", Integer.class, key, ts);
        if (hits != null && hits > limit) {
            throw new ApiException(ErrorCode.RATE_LIMITED);
        }
    }

    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        return jdbc.update("DELETE FROM rate_limit_counters WHERE window_start < ?", Timestamp.from(cutoff));
    }
}

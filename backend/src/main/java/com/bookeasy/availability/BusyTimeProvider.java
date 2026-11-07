package com.bookeasy.availability;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Time already taken by bookings, including each booking's buffer. Phase 7 replaces the default
 * implementation with one backed by appointments; slot computation already subtracts whatever this returns.
 */
public interface BusyTimeProvider {

    /** @param staffId null for bookings of services that need no staff (the branch is the resource) */
    record Busy(Long staffId, Instant start, Instant end) {
    }

    List<Busy> busy(long branchId, long serviceId, Collection<Long> staffIds, Instant from, Instant to);
}

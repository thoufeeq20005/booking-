package com.bookeasy.availability;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;

/** Until bookings exist (Phase 7) nothing is busy. */
@Component
public class NoBookingsYet implements BusyTimeProvider {

    @Override
    public List<Busy> busy(long branchId, long serviceId, Collection<Long> staffIds, Instant from, Instant to) {
        return List.of();
    }
}

package com.bookeasy.appointment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;

import com.bookeasy.availability.BusyTimeProvider;

/** Busy time = every booking still holding its slot, including its buffer. */
@Component
public class AppointmentBusyTime implements BusyTimeProvider {

    private final AppointmentRepository appointments;

    public AppointmentBusyTime(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Override
    public List<Busy> busy(long branchId, long serviceId, Collection<Long> staffIds, Instant from, Instant to) {
        Collection<Long> ids = staffIds.isEmpty() ? List.of(-1L) : staffIds;
        return appointments.holding(Appointment.Status.ACTIVE, ids, branchId, serviceId, from, to).stream()
                .map(a -> new Busy(a.getStaffId(), a.getStartsAt(), a.getBlockedUntil())).toList();
    }
}

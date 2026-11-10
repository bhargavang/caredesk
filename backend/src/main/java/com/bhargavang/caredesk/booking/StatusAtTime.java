package com.bhargavang.caredesk.booking;

import java.time.LocalDateTime;

/** Lightweight projection used by the stats query. */
public record StatusAtTime(LocalDateTime startTime, AppointmentStatus status) {
}

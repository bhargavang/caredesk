package com.bhargavang.caredesk.booking;

import java.time.LocalDateTime;

public record FreeSlot(LocalDateTime start, LocalDateTime end, Long staffId, String staffName) {
}

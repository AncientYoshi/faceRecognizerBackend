package com.tuhmb.smartattendancebackend.attendance.api;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public enum StudentAttendancePeriod {
    ALL,
    WEEK,
    MONTH;

    public LocalDate from(LocalDate referenceDate) {
        return switch (this) {
            case ALL -> LocalDate.of(1970, 1, 1);
            case WEEK -> referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> referenceDate.withDayOfMonth(1);
        };
    }

    public LocalDate to(LocalDate referenceDate) {
        return switch (this) {
            case ALL -> referenceDate;
            case WEEK -> from(referenceDate).plusDays(6);
            case MONTH -> referenceDate.with(TemporalAdjusters.lastDayOfMonth());
        };
    }
}

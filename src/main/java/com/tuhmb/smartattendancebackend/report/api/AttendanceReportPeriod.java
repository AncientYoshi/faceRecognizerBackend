package com.tuhmb.smartattendancebackend.report.api;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public enum AttendanceReportPeriod {
    WEEK,
    MONTH;

    public LocalDate from(LocalDate referenceDate) {
        return switch (this) {
            case WEEK -> referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> referenceDate.withDayOfMonth(1);
        };
    }

    public LocalDate to(LocalDate referenceDate) {
        return switch (this) {
            case WEEK -> from(referenceDate).plusDays(6);
            case MONTH -> referenceDate.with(TemporalAdjusters.lastDayOfMonth());
        };
    }
}

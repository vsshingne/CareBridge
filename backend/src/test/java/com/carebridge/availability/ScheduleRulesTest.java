package com.carebridge.availability;

import static org.junit.jupiter.api.Assertions.*;

import com.carebridge.common.ApiException;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleRulesTest {
  Availability window() {
    Availability w = new Availability();
    w.dayOfWeek = 1;
    w.startTime = LocalTime.of(9, 0);
    w.endTime = LocalTime.of(12, 0);
    w.slotDurationMinutes = 30;
    return w;
  }

  Instant start = Instant.parse("2030-01-07T09:00:00Z");

  @Test
  void acceptsAlignedSlot() {
    assertDoesNotThrow(
        () ->
            ScheduleRules.validate(
                start,
                start.plusSeconds(1800),
                ZoneOffset.UTC,
                List.of(window()),
                start.minusSeconds(1)));
  }

  @Test
  void rejectsPastOutsideAndUnaligned() {
    assertThrows(
        ApiException.class,
        () ->
            ScheduleRules.validate(
                start, start.plusSeconds(1800), ZoneOffset.UTC, List.of(window()), start));
    assertThrows(
        ApiException.class,
        () ->
            ScheduleRules.validate(
                start.minusSeconds(3600),
                start.minusSeconds(1800),
                ZoneOffset.UTC,
                List.of(window()),
                start.minusSeconds(7200)));
    assertThrows(
        ApiException.class,
        () ->
            ScheduleRules.validate(
                start.plusSeconds(60),
                start.plusSeconds(1860),
                ZoneOffset.UTC,
                List.of(window()),
                start.minusSeconds(1)));
  }

  @Test
  void rejectsAmbiguousDstTime() {
    Availability w = window();
    w.dayOfWeek = 7;
    w.startTime = LocalTime.MIDNIGHT;
    w.endTime = LocalTime.of(4, 0);
    Instant fall = Instant.parse("2030-11-03T05:00:00Z");
    assertThrows(
        ApiException.class,
        () ->
            ScheduleRules.validate(
                fall,
                fall.plusSeconds(1800),
                ZoneId.of("America/New_York"),
                List.of(w),
                fall.minusSeconds(1)));
  }
}

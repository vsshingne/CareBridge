package com.carebridge.availability;

import com.carebridge.common.ApiException;
import java.time.*;
import java.util.List;

public final class ScheduleRules {
  private ScheduleRules() {}

  public static void validate(
      Instant start, Instant end, ZoneId zone, List<Availability> windows, Instant now) {
    if (!start.isAfter(now)) throw ApiException.badRequest("Appointments must start in the future");
    if (!end.isAfter(start) || start.getNano() != 0 || end.getNano() != 0)
      throw ApiException.badRequest("Invalid appointment interval");
    ZonedDateTime local = start.atZone(zone), finish = end.atZone(zone);
    if (!local.toLocalDate().equals(finish.toLocalDate()))
      throw ApiException.badRequest("Appointment must fit within a single schedule day");
    if (zone.getRules().getValidOffsets(local.toLocalDateTime()).size() != 1
        || zone.getRules().getValidOffsets(finish.toLocalDateTime()).size() != 1
        || !local.getOffset().equals(finish.getOffset()))
      throw ApiException.badRequest("Ambiguous daylight-saving interval");
    boolean valid =
        windows.stream()
            .anyMatch(
                w ->
                    w.active
                        && w.dayOfWeek == local.getDayOfWeek().getValue()
                        && !local.toLocalTime().isBefore(w.startTime)
                        && !finish.toLocalTime().isAfter(w.endTime)
                        && Duration.between(start, end)
                            .equals(Duration.ofMinutes(w.slotDurationMinutes))
                        && Duration.between(w.startTime, local.toLocalTime()).getSeconds()
                                % (w.slotDurationMinutes * 60L)
                            == 0);
    if (!valid)
      throw ApiException.badRequest("Selected interval is outside the doctor's available slots");
  }
}

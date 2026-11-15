package com.carebridge.availability;

import com.carebridge.appointment.*;
import com.carebridge.common.ApiException;
import com.carebridge.config.CatalogInvalidator;
import com.carebridge.doctor.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {
  public record Input(
      @Min(1) @Max(7) int dayOfWeek,
      @NotNull LocalTime startTime,
      @NotNull LocalTime endTime,
      @Min(5) @Max(240) int slotDurationMinutes,
      boolean active) {}

  public record View(
      UUID id,
      int dayOfWeek,
      LocalTime startTime,
      LocalTime endTime,
      int slotDurationMinutes,
      boolean active)
      implements java.io.Serializable {
    static View from(Availability a) {
      return new View(a.id, a.dayOfWeek, a.startTime, a.endTime, a.slotDurationMinutes, a.active);
    }
  }

  public record Slot(Instant startTime, Instant endTime) {}

  private final AvailabilityRepository availability;
  private final DoctorRepository doctors;
  private final DoctorService profiles;
  private final AppointmentRepository appointments;
  private final CatalogInvalidator cache;

  public AvailabilityService(
      AvailabilityRepository availability,
      DoctorRepository doctors,
      DoctorService profiles,
      AppointmentRepository appointments,
      CatalogInvalidator cache) {
    this.availability = availability;
    this.doctors = doctors;
    this.profiles = profiles;
    this.appointments = appointments;
    this.cache = cache;
  }

  @Transactional(readOnly = true)
  public List<View> publicWindows(UUID doctor) {
    profiles.publicProfile(doctor);
    return availability.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctor).stream()
        .filter(a -> a.active)
        .map(View::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<View> mine() {
    return availability.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(profiles.owned().id).stream()
        .map(View::from)
        .toList();
  }

  @Transactional
  public View save(UUID id, Input r) {
    Doctor d = doctors.lockById(profiles.owned().id).orElseThrow(ApiException::notFound);
    if (!r.endTime().isAfter(r.startTime())
        || r.startTime().getSecond() != 0
        || r.endTime().getSecond() != 0
        || r.startTime().getNano() != 0
        || r.endTime().getNano() != 0)
      throw ApiException.badRequest("Availability must use whole minutes and end after it starts");
    if (Duration.between(r.startTime(), r.endTime()).toMinutes() % r.slotDurationMinutes() != 0)
      throw ApiException.badRequest("Availability length must be a multiple of slot duration");
    List<Availability> windows = availability.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(d.id);
    Availability a =
        id == null
            ? new Availability()
            : windows.stream()
                .filter(w -> w.id.equals(id))
                .findFirst()
                .orElseThrow(ApiException::notFound);
    if (r.active()
        && windows.stream()
            .anyMatch(
                w ->
                    !w.id.equals(a.id)
                        && w.active
                        && w.dayOfWeek == r.dayOfWeek()
                        && w.startTime.isBefore(r.endTime())
                        && w.endTime.isAfter(r.startTime())))
      throw ApiException.conflict("Availability windows overlap");
    a.doctorId = d.id;
    a.dayOfWeek = r.dayOfWeek();
    a.startTime = r.startTime();
    a.endTime = r.endTime();
    a.slotDurationMinutes = r.slotDurationMinutes();
    a.active = r.active();
    availability.saveAndFlush(a);
    ensureExistingBookings(d);
    cache.invalidateAfterCommit();
    return View.from(a);
  }

  @Transactional
  public void delete(UUID id) {
    Doctor d = doctors.lockById(profiles.owned().id).orElseThrow(ApiException::notFound);
    Availability a =
        availability
            .findById(id)
            .filter(w -> w.doctorId.equals(d.id))
            .orElseThrow(ApiException::notFound);
    availability.delete(a);
    availability.flush();
    ensureExistingBookings(d);
    cache.invalidateAfterCommit();
  }

  private void ensureExistingBookings(Doctor d) {
    Instant now = Instant.now();
    var windows = availability.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(d.id);
    for (Appointment a : appointments.overlapping(d.id, now, now.plus(Duration.ofDays(3660)))) {
      try {
        ScheduleRules.validate(
            a.startTime, a.endTime, ZoneId.of(d.timezone), windows, a.startTime.minusSeconds(1));
      } catch (ApiException e) {
        throw ApiException.conflict("Schedule change would invalidate an existing appointment");
      }
    }
  }

  @Transactional(readOnly = true)
  public List<Slot> slots(UUID doctor, LocalDate date) {
    var profile = profiles.publicProfile(doctor);
    ZoneId zone = ZoneId.of(profile.timezone());
    LocalDate today = LocalDate.now(zone);
    if (date.isBefore(today) || date.isAfter(today.plusDays(90)))
      throw ApiException.badRequest("Choose a date within the next 90 days");
    var windows = availability.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctor);
    var booked =
        appointments.overlapping(
            doctor,
            date.atStartOfDay(zone).toInstant(),
            date.plusDays(1).atStartOfDay(zone).toInstant());
    List<Slot> slots = new ArrayList<>();
    for (var w : windows)
      if (w.active && w.dayOfWeek == date.getDayOfWeek().getValue()) {
        for (LocalDateTime local = date.atTime(w.startTime);
            !local.plusMinutes(w.slotDurationMinutes).isAfter(date.atTime(w.endTime));
            local = local.plusMinutes(w.slotDurationMinutes)) {
          LocalDateTime finish = local.plusMinutes(w.slotDurationMinutes);
          if (zone.getRules().getValidOffsets(local).size() != 1
              || zone.getRules().getValidOffsets(finish).size() != 1) continue;
          Instant start = local.atZone(zone).toInstant(), end = finish.atZone(zone).toInstant();
          if (!Duration.between(start, end).equals(Duration.ofMinutes(w.slotDurationMinutes)))
            continue;
          if (start.isAfter(Instant.now())
              && booked.stream()
                  .noneMatch(a -> a.startTime.isBefore(end) && a.endTime.isAfter(start)))
            slots.add(new Slot(start, end));
        }
      }
    return slots;
  }
}

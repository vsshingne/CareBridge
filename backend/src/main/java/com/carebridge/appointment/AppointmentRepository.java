package com.carebridge.appointment;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AppointmentRepository
    extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Appointment a where a.id=:id")
  Optional<Appointment> lockById(UUID id);

  @Query(
      "select (count(a)>0) from Appointment a where a.doctorId=:doctor and a.status in"
          + " (com.carebridge.appointment.AppointmentStatus.PENDING,com.carebridge.appointment.AppointmentStatus.CONFIRMED)"
          + " and a.endTime>:now")
  boolean hasFutureActive(UUID doctor, Instant now);

  @Query(
      "select a from Appointment a where a.doctorId=:doctor and a.status in"
          + " (com.carebridge.appointment.AppointmentStatus.PENDING,com.carebridge.appointment.AppointmentStatus.CONFIRMED)"
          + " and a.startTime<:end and a.endTime>:start")
  List<Appointment> overlapping(UUID doctor, Instant start, Instant end);
}

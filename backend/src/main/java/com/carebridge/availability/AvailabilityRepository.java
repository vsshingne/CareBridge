package com.carebridge.availability;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRepository extends JpaRepository<Availability, UUID> {
  List<Availability> findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(UUID doctorId);
}

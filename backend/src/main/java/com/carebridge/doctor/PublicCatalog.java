package com.carebridge.doctor;

import com.carebridge.availability.AvailabilityService;
import java.util.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class PublicCatalog {
  private final DoctorService profiles;
  private final DoctorRepository doctors;
  private final AvailabilityService availability;

  public PublicCatalog(
      DoctorService profiles, DoctorRepository doctors, AvailabilityService availability) {
    this.profiles = profiles;
    this.doctors = doctors;
    this.availability = availability;
  }

  @Cacheable(value = "doctorProfiles", key = "#id")
  public DoctorDtos.View profile(UUID id) {
    return profiles.publicProfile(id);
  }

  @Cacheable(value = "specializations", key = "'all'")
  public List<String> specializations() {
    return new ArrayList<>(doctors.specializations());
  }

  @Cacheable(value = "availability", key = "#id")
  public List<AvailabilityService.View> windows(UUID id) {
    return new ArrayList<>(availability.publicWindows(id));
  }
}

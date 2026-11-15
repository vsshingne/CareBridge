package com.carebridge.doctor;

import com.carebridge.availability.AvailabilityService;
import com.carebridge.common.Pages;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/doctors")
public class DoctorController {
  private final DoctorService doctors;
  private final PublicCatalog catalog;
  private final AvailabilityService availability;

  public DoctorController(
      DoctorService doctors, PublicCatalog catalog, AvailabilityService availability) {
    this.doctors = doctors;
    this.catalog = catalog;
    this.availability = availability;
  }

  @GetMapping
  Pages.Result<DoctorDtos.View> search(
      @RequestParam(required = false) String specialization,
      @RequestParam(required = false) String q,
      Pageable p) {
    return doctors.search(specialization, q, p);
  }

  @GetMapping("/specializations")
  List<String> specializations() {
    return catalog.specializations();
  }

  @GetMapping("/{id}")
  DoctorDtos.View profile(@PathVariable UUID id) {
    return catalog.profile(id);
  }

  @GetMapping("/{id}/availability")
  List<AvailabilityService.View> windows(@PathVariable UUID id) {
    return catalog.windows(id);
  }

  @GetMapping("/{id}/slots")
  List<AvailabilityService.Slot> slots(@PathVariable UUID id, @RequestParam LocalDate date) {
    return availability.slots(id, date);
  }

  @GetMapping("/me")
  DoctorDtos.View me() {
    return doctors.me();
  }

  @PutMapping("/me")
  DoctorDtos.View update(@Valid @RequestBody DoctorDtos.Update r) {
    return doctors.update(r);
  }

  @GetMapping("/me/availability")
  List<AvailabilityService.View> mine() {
    return availability.mine();
  }

  @PostMapping("/me/availability")
  @ResponseStatus(HttpStatus.CREATED)
  AvailabilityService.View create(@Valid @RequestBody AvailabilityService.Input r) {
    return availability.save(null, r);
  }

  @PutMapping("/me/availability/{id}")
  AvailabilityService.View updateWindow(
      @PathVariable UUID id, @Valid @RequestBody AvailabilityService.Input r) {
    return availability.save(id, r);
  }

  @DeleteMapping("/me/availability/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID id) {
    availability.delete(id);
  }
}

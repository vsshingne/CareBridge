package com.carebridge.appointment;

import static com.carebridge.appointment.AppointmentDtos.*;

import com.carebridge.common.Pages;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AppointmentController {
  private final AppointmentService service;

  public AppointmentController(AppointmentService service) {
    this.service = service;
  }

  @PostMapping("/appointments")
  @ResponseStatus(HttpStatus.CREATED)
  View book(@Valid @RequestBody Book r) {
    return service.book(r);
  }

  @GetMapping({"/appointments/me", "/doctors/me/appointments"})
  Pages.Result<View> mine(
      @RequestParam(required = false) AppointmentStatus status,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      Pageable p) {
    return service.mine(status, from, to, p);
  }

  @GetMapping("/appointments/{id}")
  View get(@PathVariable UUID id) {
    return service.get(id);
  }

  @GetMapping("/appointments/{id}/patient")
  AppointmentService.PatientDetails patient(@PathVariable UUID id) {
    return service.patientDetails(id);
  }

  @PatchMapping("/appointments/{id}/reschedule")
  View reschedule(@PathVariable UUID id, @Valid @RequestBody Reschedule r) {
    return service.reschedule(id, r);
  }

  @PatchMapping("/appointments/{id}/cancel")
  View cancel(@PathVariable UUID id) {
    return service.transition(id, AppointmentStatus.CANCELLED);
  }

  @PatchMapping("/appointments/{id}/confirm")
  View confirm(@PathVariable UUID id) {
    return service.transition(id, AppointmentStatus.CONFIRMED);
  }

  @PatchMapping("/appointments/{id}/reject")
  View reject(@PathVariable UUID id) {
    return service.transition(id, AppointmentStatus.REJECTED);
  }

  @PatchMapping("/appointments/{id}/complete")
  View complete(@PathVariable UUID id) {
    return service.transition(id, AppointmentStatus.COMPLETED);
  }
}

package com.carebridge.admin;

import com.carebridge.appointment.*;
import com.carebridge.auth.AuthDtos;
import com.carebridge.common.Pages;
import com.carebridge.doctor.DoctorDtos;
import com.carebridge.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
  public record Enabled(@NotNull Boolean enabled) {}

  public record Verified(@NotNull Boolean verified) {}

  private final AdminService service;
  private final AppointmentService appointments;

  public AdminController(AdminService service, AppointmentService appointments) {
    this.service = service;
    this.appointments = appointments;
  }

  @GetMapping("/users")
  Pages.Result<AuthDtos.UserView> users(@RequestParam(required = false) Role role, Pageable p) {
    return service.users(role, p);
  }

  @GetMapping("/patients")
  Pages.Result<AuthDtos.UserView> patients(Pageable p) {
    return service.users(Role.PATIENT, p);
  }

  @GetMapping("/doctors")
  Pages.Result<DoctorDtos.View> doctors(Pageable p) {
    return service.doctors(p);
  }

  @PatchMapping("/doctors/{id}/verify")
  DoctorDtos.View verify(@PathVariable UUID id, @Valid @RequestBody Verified r) {
    return service.verify(id, r.verified());
  }

  @PatchMapping("/users/{id}/enabled")
  AuthDtos.UserView enable(@PathVariable UUID id, @Valid @RequestBody Enabled r) {
    return service.enable(id, r.enabled());
  }

  @GetMapping("/appointments")
  Pages.Result<AppointmentDtos.View> appointments(
      @RequestParam(required = false) AppointmentStatus status,
      @RequestParam(required = false) UUID doctorId,
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      Pageable p) {
    return appointments.list(status, doctorId, patientId, from, to, p);
  }

  @GetMapping("/analytics")
  Map<String, Object> analytics() {
    return service.analytics();
  }

  @GetMapping("/audit-logs")
  Pages.Result<AdminService.AuditView> logs(Pageable p) {
    return service.logs(p);
  }

  @GetMapping("/outbox")
  Pages.Result<AdminService.OutboxView> outbox(Pageable p) {
    return service.outbox(p);
  }

  @PostMapping("/outbox/{id}/retry")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void retry(@PathVariable UUID id) {
    service.retry(id);
  }
}

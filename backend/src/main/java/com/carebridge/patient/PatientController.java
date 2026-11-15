package com.carebridge.patient;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patients/me")
public class PatientController {
  private final PatientService service;

  public PatientController(PatientService service) {
    this.service = service;
  }

  @GetMapping
  PatientService.View me() {
    return service.me();
  }

  @PutMapping
  PatientService.View update(@Valid @RequestBody PatientService.Update r) {
    return service.update(r);
  }
}

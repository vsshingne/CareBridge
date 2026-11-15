package com.carebridge.patient;

import com.carebridge.auth.*;
import com.carebridge.common.ApiException;
import com.carebridge.user.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {
  public record Update(
      @NotBlank @Size(max = 120) String fullName,
      @Size(max = 30) String phoneNumber,
      @Past LocalDate dateOfBirth,
      @Size(max = 40) String gender,
      @Size(max = 500) String address,
      @Size(max = 120) String emergencyContact) {}

  public record View(
      UUID id,
      AuthDtos.UserView user,
      LocalDate dateOfBirth,
      String gender,
      String address,
      String emergencyContact) {}

  private final PatientRepository patients;
  private final UserRepository users;
  private final CurrentUser current;

  public PatientService(PatientRepository patients, UserRepository users, CurrentUser current) {
    this.patients = patients;
    this.users = users;
    this.current = current;
  }

  @Transactional(readOnly = true)
  public View me() {
    UserAccount u = current.require(Role.PATIENT);
    return view(patients.findByUserId(u.id).orElseThrow(ApiException::notFound), u);
  }

  @Transactional
  public View update(Update r) {
    UserAccount u = current.require(Role.PATIENT);
    Patient p = patients.findByUserId(u.id).orElseThrow(ApiException::notFound);
    u.fullName = r.fullName();
    u.phoneNumber = r.phoneNumber();
    u.updatedAt = Instant.now();
    users.save(u);
    p.dateOfBirth = r.dateOfBirth();
    p.gender = r.gender();
    p.address = r.address();
    p.emergencyContact = r.emergencyContact();
    patients.save(p);
    return view(p, u);
  }

  private View view(Patient p, UserAccount u) {
    return new View(
        p.id, AuthDtos.UserView.from(u), p.dateOfBirth, p.gender, p.address, p.emergencyContact);
  }
}

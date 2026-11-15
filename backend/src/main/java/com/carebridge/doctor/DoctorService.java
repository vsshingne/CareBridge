package com.carebridge.doctor;

import static com.carebridge.doctor.DoctorDtos.*;

import com.carebridge.appointment.AppointmentRepository;
import com.carebridge.auth.CurrentUser;
import com.carebridge.common.*;
import com.carebridge.config.CatalogInvalidator;
import com.carebridge.user.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorService {
  private final DoctorRepository doctors;
  private final UserRepository users;
  private final CurrentUser current;
  private final AppointmentRepository appointments;
  private final CatalogInvalidator cache;

  public DoctorService(
      DoctorRepository doctors,
      UserRepository users,
      CurrentUser current,
      AppointmentRepository appointments,
      CatalogInvalidator cache) {
    this.doctors = doctors;
    this.users = users;
    this.current = current;
    this.appointments = appointments;
    this.cache = cache;
  }

  @Transactional(readOnly = true)
  public Pages.Result<View> search(String specialization, String q, Pageable pageable) {
    Specification<Doctor> spec =
        (root, query, cb) -> {
          var user = query.from(UserAccount.class);
          var predicate =
              cb.and(
                  cb.equal(root.get("userId"), user.get("id")),
                  cb.isTrue(root.get("verified")),
                  cb.isTrue(user.get("enabled")));
          if (specialization != null && !specialization.isBlank())
            predicate =
                cb.and(
                    predicate,
                    cb.equal(
                        cb.lower(root.get("specialization")),
                        specialization.toLowerCase(Locale.ROOT)));
          if (q != null && !q.isBlank())
            predicate =
                cb.and(
                    predicate,
                    cb.like(
                        cb.lower(user.get("fullName")),
                        "%"
                            + q.toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_")
                            + "%",
                        '\\'));
          return predicate;
        };
    return Pages.Result.from(
        doctors
            .findAll(
                spec,
                Pages.checked(
                    pageable, "specialization", "experienceYears", "consultationFee", "createdAt"))
            .map(this::view));
  }

  @Transactional(readOnly = true)
  public View publicProfile(UUID id) {
    Doctor d = doctors.findById(id).orElseThrow(ApiException::notFound);
    if (!d.verified || !users.findById(d.userId).orElseThrow(ApiException::notFound).enabled)
      throw ApiException.notFound();
    return view(d);
  }

  @Transactional(readOnly = true)
  public View me() {
    return view(owned());
  }

  public Doctor owned() {
    return doctors
        .findByUserId(current.require(Role.DOCTOR).id)
        .orElseThrow(ApiException::notFound);
  }

  @Transactional
  public View update(Update r) {
    Doctor d = doctors.lockById(owned().id).orElseThrow(ApiException::notFound);
    ZoneId.of(r.timezone());
    if (!d.timezone.equals(r.timezone()) && appointments.hasFutureActive(d.id, Instant.now()))
      throw ApiException.conflict("Cancel upcoming appointments before changing timezone");
    UserAccount u = users.findById(d.userId).orElseThrow(ApiException::notFound);
    u.fullName = r.fullName();
    u.updatedAt = Instant.now();
    users.save(u);
    // Material credential changes require administrator review again.
    if (!d.specialization.equals(r.specialization()) || !d.qualification.equals(r.qualification()))
      d.verified = false;
    d.specialization = r.specialization();
    d.qualification = r.qualification();
    d.experienceYears = r.experienceYears();
    d.consultationFee = r.consultationFee();
    d.clinicAddress = r.clinicAddress();
    d.biography = r.biography();
    d.timezone = r.timezone();
    doctors.save(d);
    cache.invalidateAfterCommit();
    return view(d);
  }

  public View view(Doctor d) {
    UserAccount u = users.findById(d.userId).orElseThrow(ApiException::notFound);
    return new View(
        d.id,
        u.fullName,
        d.specialization,
        d.qualification,
        d.experienceYears,
        d.consultationFee,
        d.clinicAddress,
        d.biography,
        d.timezone,
        d.verified);
  }
}

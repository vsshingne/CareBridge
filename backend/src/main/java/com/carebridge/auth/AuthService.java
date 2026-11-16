package com.carebridge.auth;

import static com.carebridge.auth.AuthDtos.*;

import com.carebridge.audit.AuditService;
import com.carebridge.common.ApiException;
import com.carebridge.doctor.*;
import com.carebridge.patient.*;
import com.carebridge.user.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final UserRepository users;
  private final PatientRepository patients;
  private final DoctorRepository doctors;
  private final RefreshTokenRepository refresh;
  private final PasswordEncoder passwords;
  private final JwtService jwt;
  private final AuditService audit;
  private final com.carebridge.outbox.EventService events;
  private final String dummyHash;

  public AuthService(
      UserRepository users,
      PatientRepository patients,
      DoctorRepository doctors,
      RefreshTokenRepository refresh,
      PasswordEncoder passwords,
      JwtService jwt,
      AuditService audit,
      com.carebridge.outbox.EventService events) {
    this.users = users;
    this.patients = patients;
    this.doctors = doctors;
    this.refresh = refresh;
    this.passwords = passwords;
    this.jwt = jwt;
    this.audit = audit;
    this.events = events;
    dummyHash = passwords.encode(UUID.randomUUID().toString());
  }

  @Transactional
  public Tokens register(Register r, Role role) {
    if (role == Role.ADMIN) throw ApiException.forbidden();
    if (r.password().getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiException.badRequest("Password must be at most 72 UTF-8 bytes");
    String email = r.email().trim().toLowerCase(Locale.ROOT);
    if (users.existsByEmail(email)) throw ApiException.conflict("Email already registered");
    UserAccount u = new UserAccount();
    u.fullName = r.fullName().trim();
    u.email = email;
    u.passwordHash = passwords.encode(r.password());
    u.phoneNumber = r.phoneNumber();
    u.role = role;
    users.save(u);
    if (role == Role.PATIENT) {
      Patient p = new Patient();
      p.userId = u.id;
      patients.save(p);
      events.domain(p.id, "PATIENT", "PATIENT_REGISTERED", p.id, null);
    } else {
      if (r.specialization() == null
          || r.specialization().isBlank()
          || r.qualification() == null
          || r.qualification().isBlank())
        throw ApiException.badRequest("Specialization and qualification are required");
      Doctor d = new Doctor();
      d.userId = u.id;
      d.specialization = r.specialization().trim();
      d.qualification = r.qualification().trim();
      d.timezone = ZoneId.of(r.timezone() == null ? "UTC" : r.timezone()).getId();
      d.consultationFee = BigDecimal.ZERO;
      d.clinicAddress = "";
      doctors.save(d);
    }
    audit.record(u.id, "USER_REGISTERED", "USER", u.id);
    return issue(u, UUID.randomUUID());
  }

  @Transactional
  public Tokens login(Login request) {
    var found = users.findByEmail(request.email().trim().toLowerCase(Locale.ROOT));
    boolean matches =
        passwords.matches(request.password(), found.map(u -> u.passwordHash).orElse(dummyHash));
    if (found.isEmpty() || !matches || !found.get().enabled) {
      audit.loginFailure();
      throw ApiException.unauthorized();
    }
    audit.record(found.get().id, "LOGIN_SUCCEEDED", "USER", found.get().id);
    return issue(found.get(), UUID.randomUUID());
  }

  // Revocations on replay must commit even though the request fails.
  @Transactional(noRollbackFor = ApiException.class)
  public Tokens rotate(String raw) {
    if (raw == null || raw.isBlank()) throw ApiException.unauthorized();
    RefreshToken token = refresh.lockByHash(hash(raw)).orElseThrow(ApiException::unauthorized);
    if (token.revoked || token.expiresAt.isBefore(Instant.now())) {
      refresh.revokeFamily(token.familyId);
      throw ApiException.unauthorized();
    }
    UserAccount u = users.findById(token.userId).orElseThrow(ApiException::unauthorized);
    if (!u.enabled) {
      refresh.revokeFamily(token.familyId);
      throw ApiException.unauthorized();
    }
    token.revoked = true;
    refresh.saveAndFlush(token);
    return issue(u, token.familyId);
  }

  @Transactional
  public void logout(String raw) {
    if (raw != null)
      refresh
          .lockByHash(hash(raw))
          .ifPresent(
              t -> {
                refresh.revokeFamily(t.familyId);
                audit.record(t.userId, "LOGOUT", "USER", t.userId);
              });
  }

  private Tokens issue(UserAccount user, UUID family) {
    byte[] bytes = new byte[48];
    new SecureRandom().nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    RefreshToken token = new RefreshToken();
    token.userId = user.id;
    token.tokenHash = hash(raw);
    token.familyId = family;
    token.expiresAt = Instant.now().plus(Duration.ofDays(7));
    refresh.save(token);
    return new Tokens(new Session(jwt.issue(user), 900, UserView.from(user)), raw);
  }

  public static String hash(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}

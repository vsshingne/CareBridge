package com.carebridge.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.carebridge.audit.AuditService;
import com.carebridge.common.ApiException;
import com.carebridge.doctor.*;
import com.carebridge.patient.*;
import com.carebridge.user.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {
  UserRepository users = mock(UserRepository.class);
  PatientRepository patients = mock(PatientRepository.class);
  DoctorRepository doctors = mock(DoctorRepository.class);
  RefreshTokenRepository refresh = mock(RefreshTokenRepository.class);
  JwtService jwt = mock(JwtService.class);
  AuditService audit = mock(AuditService.class);
  BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
  AuthService service =
      new AuthService(
          users,
          patients,
          doctors,
          refresh,
          encoder,
          jwt,
          audit,
          mock(com.carebridge.outbox.EventService.class));
  AuthDtos.Register registration =
      new AuthDtos.Register(
          "Jane Doe", "JANE@example.test", "strong-password-123", null, null, null, null);

  @Test
  void registrationHashesPasswordAndCreatesProfile() {
    when(jwt.issue(any())).thenReturn("signed");
    var tokens = service.register(registration, Role.PATIENT);
    assertEquals(Role.PATIENT, tokens.session().user().role());
    verify(users)
        .save(
            argThat(
                u ->
                    u.email.equals("jane@example.test")
                        && encoder.matches(registration.password(), u.passwordHash)));
    verify(patients).save(any());
    verify(refresh)
        .save(
            argThat(t -> !t.tokenHash.equals(tokens.refreshToken()) && t.tokenHash.length() == 64));
  }

  @Test
  void rejectsDuplicateEmail() {
    when(users.existsByEmail("jane@example.test")).thenReturn(true);
    assertThrows(ApiException.class, () -> service.register(registration, Role.PATIENT));
    verify(users, never()).save(any());
  }

  @Test
  void loginAndWrongPassword() {
    UserAccount u = new UserAccount();
    u.role = Role.PATIENT;
    u.passwordHash = encoder.encode("strong-password-123");
    when(users.findByEmail("jane@example.test")).thenReturn(Optional.of(u));
    assertNotNull(service.login(new AuthDtos.Login("jane@example.test", "strong-password-123")));
    assertThrows(
        ApiException.class, () -> service.login(new AuthDtos.Login("jane@example.test", "wrong")));
    verify(audit).loginFailure();
  }

  @Test
  void replayRevokesFamily() {
    RefreshToken t = new RefreshToken();
    t.revoked = true;
    t.familyId = UUID.randomUUID();
    when(refresh.lockByHash(AuthService.hash("reused"))).thenReturn(Optional.of(t));
    assertThrows(ApiException.class, () -> service.rotate("reused"));
    verify(refresh).revokeFamily(t.familyId);
  }

  @Test
  void rotatesAndRevokesOldToken() {
    UserAccount u = new UserAccount();
    u.role = Role.PATIENT;
    RefreshToken t = new RefreshToken();
    t.userId = u.id;
    t.familyId = UUID.randomUUID();
    t.expiresAt = Instant.now().plusSeconds(3600);
    when(refresh.lockByHash(AuthService.hash("old"))).thenReturn(Optional.of(t));
    when(users.findById(u.id)).thenReturn(Optional.of(u));
    assertNotEquals("old", service.rotate("old").refreshToken());
    assertTrue(t.revoked);
    verify(refresh).saveAndFlush(t);
  }
}

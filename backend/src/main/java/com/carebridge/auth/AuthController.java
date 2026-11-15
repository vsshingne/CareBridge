package com.carebridge.auth;

import static com.carebridge.auth.AuthDtos.*;

import com.carebridge.common.ApiException;
import com.carebridge.user.Role;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService service;
  private final boolean secure;
  private final Set<String> origins;

  public AuthController(
      AuthService service,
      @Value("${app.secure-cookies}") boolean secure,
      @Value("${app.allowed-origins}") String origins) {
    this.service = service;
    this.secure = secure;
    this.origins = Set.of(origins.split(","));
  }

  @PostMapping("/register/patient")
  ResponseEntity<Session> patient(
      @Valid @RequestBody Register r,
      @RequestHeader(value = "Origin", required = false) String origin) {
    checkOrigin(origin);
    return response(service.register(r, Role.PATIENT), HttpStatus.CREATED);
  }

  @PostMapping("/register/doctor")
  ResponseEntity<Session> doctor(
      @Valid @RequestBody Register r,
      @RequestHeader(value = "Origin", required = false) String origin) {
    checkOrigin(origin);
    return response(service.register(r, Role.DOCTOR), HttpStatus.CREATED);
  }

  @PostMapping("/login")
  ResponseEntity<Session> login(
      @Valid @RequestBody Login r,
      @RequestHeader(value = "Origin", required = false) String origin) {
    checkOrigin(origin);
    return response(service.login(r), HttpStatus.OK);
  }

  @PostMapping("/refresh")
  ResponseEntity<Session> refresh(
      @CookieValue(value = "refresh_token", required = false) String token,
      @RequestHeader(value = "Origin", required = false) String origin) {
    checkOrigin(origin);
    return response(service.rotate(token), HttpStatus.OK);
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(
      @CookieValue(value = "refresh_token", required = false) String token,
      @RequestHeader(value = "Origin", required = false) String origin) {
    checkOrigin(origin);
    service.logout(token);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
        .build();
  }

  private void checkOrigin(String origin) {
    if (origin == null || !origins.contains(origin)) throw ApiException.forbidden();
  }

  private ResponseCookie cookie(String token, Duration age) {
    return ResponseCookie.from("refresh_token", token)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Strict")
        .path("/api/v1/auth")
        .maxAge(age)
        .build();
  }

  private ResponseEntity<Session> response(Tokens tokens, HttpStatus status) {
    return ResponseEntity.status(status)
        .header(
            HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), Duration.ofDays(7)).toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(tokens.session());
  }
}

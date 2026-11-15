package com.carebridge.auth;

import com.carebridge.user.*;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class AuthDtos {
  private AuthDtos() {}

  public record Register(
      @NotBlank @Size(max = 120) String fullName,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password,
      @Size(max = 30) String phoneNumber,
      @Size(max = 120) String specialization,
      @Size(max = 200) String qualification,
      @Size(max = 80) String timezone) {}

  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}

  public record UserView(
      UUID id, String fullName, String email, String phoneNumber, Role role, boolean enabled) {
    public static UserView from(UserAccount u) {
      return new UserView(u.id, u.fullName, u.email, u.phoneNumber, u.role, u.enabled);
    }
  }

  public record Session(String accessToken, long expiresIn, UserView user) {}

  public record Tokens(Session session, String refreshToken) {}
}

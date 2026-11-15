package com.carebridge.auth;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
  @Id public UUID id = UUID.randomUUID();
  public UUID userId;

  @Column(unique = true)
  public String tokenHash;

  public UUID familyId;
  public Instant expiresAt;
  public boolean revoked;
}

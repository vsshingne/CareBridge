package com.carebridge.user;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {
  @Id public UUID id = UUID.randomUUID();

  @Column(nullable = false)
  public String fullName;

  @Column(nullable = false, unique = true)
  public String email;

  @Column(nullable = false)
  public String passwordHash;

  public String phoneNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Role role;

  public boolean enabled = true;
  public Instant createdAt = Instant.now();
  public Instant updatedAt = Instant.now();
}

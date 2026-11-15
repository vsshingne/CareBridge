package com.carebridge.patient;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "patients")
public class Patient {
  @Id public UUID id = UUID.randomUUID();

  @Column(nullable = false, unique = true)
  public UUID userId;

  public LocalDate dateOfBirth;
  public String gender;
  public String address;
  public String emergencyContact;
  public Instant createdAt = Instant.now();
}

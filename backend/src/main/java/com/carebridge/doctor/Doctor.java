package com.carebridge.doctor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "doctors")
public class Doctor {
  @Id public UUID id = UUID.randomUUID();

  @Column(nullable = false, unique = true)
  public UUID userId;

  public String specialization;
  public String qualification;
  public int experienceYears;

  @Column(precision = 12, scale = 2)
  public BigDecimal consultationFee;

  public String clinicAddress;
  public String biography;
  public String timezone;
  public boolean verified;
  public Instant createdAt = Instant.now();
}

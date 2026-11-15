package com.carebridge.doctor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class DoctorDtos {
  private DoctorDtos() {}

  public record View(
      UUID id,
      String fullName,
      String specialization,
      String qualification,
      int experienceYears,
      BigDecimal consultationFee,
      String clinicAddress,
      String biography,
      String timezone,
      boolean verified)
      implements java.io.Serializable {}

  public record Update(
      @NotBlank @Size(max = 120) String fullName,
      @NotBlank @Size(max = 120) String specialization,
      @NotBlank @Size(max = 200) String qualification,
      @Min(0) @Max(80) int experienceYears,
      @NotNull @DecimalMin("0") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
          BigDecimal consultationFee,
      @NotBlank @Size(max = 500) String clinicAddress,
      @Size(max = 2000) String biography,
      @NotBlank @Size(max = 80) String timezone) {}
}

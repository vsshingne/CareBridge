package com.carebridge.appointment;

import com.carebridge.common.ApiException;

public enum AppointmentStatus {
  PENDING,
  CONFIRMED,
  CANCELLED,
  COMPLETED,
  REJECTED;

  public void requireTransition(AppointmentStatus target) {
    boolean valid =
        switch (this) {
          case PENDING -> target == CONFIRMED || target == CANCELLED || target == REJECTED;
          case CONFIRMED -> target == CANCELLED || target == COMPLETED;
          default -> false;
        };
    if (!valid)
      throw ApiException.conflict("Appointment cannot transition from " + this + " to " + target);
  }

  public boolean active() {
    return this == PENDING || this == CONFIRMED;
  }
}

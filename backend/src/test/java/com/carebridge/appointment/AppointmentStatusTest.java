package com.carebridge.appointment;

import static org.junit.jupiter.api.Assertions.*;

import com.carebridge.common.ApiException;
import org.junit.jupiter.api.Test;

class AppointmentStatusTest {
  @Test
  void transitionMatrixIsExplicit() {
    for (var source : AppointmentStatus.values())
      for (var target : AppointmentStatus.values()) {
        boolean allowed =
            source == AppointmentStatus.PENDING
                    && (target == AppointmentStatus.CONFIRMED
                        || target == AppointmentStatus.REJECTED
                        || target == AppointmentStatus.CANCELLED)
                || source == AppointmentStatus.CONFIRMED
                    && (target == AppointmentStatus.CANCELLED
                        || target == AppointmentStatus.COMPLETED);
        if (allowed) assertDoesNotThrow(() -> source.requireTransition(target));
        else assertThrows(ApiException.class, () -> source.requireTransition(target));
      }
  }
}

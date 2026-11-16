package com.carebridge.care;

import static com.carebridge.care.CareStates.*;
import static org.junit.jupiter.api.Assertions.*;

import com.carebridge.common.ApiException;
import org.junit.jupiter.api.Test;

class CareStatesTest {
  @Test
  void terminalActivitiesCannotReopenAndCreationCannotComplete() {
    for (var terminal : new ActivityStatus[] {ActivityStatus.COMPLETED, ActivityStatus.CANCELLED})
      for (var target : ActivityStatus.values())
        assertThrows(ApiException.class, () -> terminal.requireTransition(target));
    assertThrows(
        ApiException.class,
        () -> ActivityStatus.NOT_STARTED.requireTransition(ActivityStatus.COMPLETED));
    assertDoesNotThrow(
        () -> ActivityStatus.IN_PROGRESS.requireTransition(ActivityStatus.COMPLETED));
  }

  @Test
  void referralCannotSkipAcceptanceOrExecution() {
    assertThrows(
        ApiException.class,
        () -> ReferralStatus.CREATED.requireTransition(ReferralStatus.SCHEDULED));
    assertThrows(
        ApiException.class,
        () -> ReferralStatus.SCHEDULED.requireTransition(ReferralStatus.COMPLETED));
    for (var terminal : new ReferralStatus[] {ReferralStatus.COMPLETED, ReferralStatus.CANCELLED})
      for (var target : ReferralStatus.values())
        assertThrows(ApiException.class, () -> terminal.requireTransition(target));
    assertDoesNotThrow(
        () -> ReferralStatus.IN_PROGRESS.requireTransition(ReferralStatus.COMPLETED));
  }
}

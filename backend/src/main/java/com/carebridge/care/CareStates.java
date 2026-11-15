package com.carebridge.care;

import com.carebridge.common.ApiException;
import java.util.Set;

public final class CareStates {
  private CareStates() {}
  public enum ActivityType { INVESTIGATION, FOLLOW_UP, CONSULTATION }
  public enum PlanStatus { ACTIVE, COMPLETED, CANCELLED }
  public enum Priority { ROUTINE, URGENT }
  public enum ActivityStatus {
    NOT_STARTED, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED;
    public void requireTransition(ActivityStatus to) {
      boolean valid = switch (this) {
        case NOT_STARTED -> Set.of(SCHEDULED, IN_PROGRESS, CANCELLED).contains(to);
        case SCHEDULED -> Set.of(IN_PROGRESS, CANCELLED).contains(to);
        case IN_PROGRESS -> Set.of(COMPLETED, CANCELLED).contains(to);
        default -> false;
      };
      if (!valid) throw ApiException.conflict("Invalid care activity transition");
    }
  }
  public enum ReferralStatus {
    CREATED, PENDING_ACCEPTANCE, ACCEPTED, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED;
    public void requireTransition(ReferralStatus to) {
      boolean valid = to == CANCELLED && this != COMPLETED && this != CANCELLED;
      valid |= switch (this) {
        case CREATED -> to == PENDING_ACCEPTANCE;
        case PENDING_ACCEPTANCE -> to == ACCEPTED;
        case ACCEPTED -> to == SCHEDULED;
        case SCHEDULED -> to == IN_PROGRESS;
        case IN_PROGRESS -> to == COMPLETED;
        default -> false;
      };
      if (!valid) throw ApiException.conflict("Invalid referral transition");
    }
  }
}

package com.carebridge.outbox;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
  @Query(
      value =
          "select * from outbox_events where status='PENDING' and next_attempt_at<=now() order by"
              + " created_at limit 20 for update skip locked",
      nativeQuery = true)
  List<OutboxEvent> lockDue();
}

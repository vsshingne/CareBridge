package com.carebridge.notification;

import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  Page<Notification> findByUserId(UUID userId, Pageable pageable);
}

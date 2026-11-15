package com.carebridge.notification;

import com.carebridge.common.Pages;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final NotificationService service;

  public NotificationController(NotificationService service) {
    this.service = service;
  }

  @GetMapping
  Pages.Result<NotificationService.View> mine(Pageable p) {
    return service.mine(p);
  }
}

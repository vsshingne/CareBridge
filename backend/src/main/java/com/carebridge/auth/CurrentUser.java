package com.carebridge.auth;

import com.carebridge.common.ApiException;
import com.carebridge.user.*;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
  private final UserRepository users;

  public CurrentUser(UserRepository users) {
    this.users = users;
  }

  public UserAccount get() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) throw ApiException.unauthorized();
    UserAccount user =
        users.findById(UUID.fromString(auth.getName())).orElseThrow(ApiException::unauthorized);
    if (!user.enabled) throw ApiException.unauthorized();
    return user;
  }

  public UserAccount require(Role role) {
    UserAccount u = get();
    if (u.role != role) throw ApiException.forbidden();
    return u;
  }
}

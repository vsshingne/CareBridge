package com.carebridge.config;

import com.carebridge.audit.AuditService;
import com.carebridge.user.*;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminProvisioner implements ApplicationRunner {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final AuditService audit;
  private final String email, password;

  public AdminProvisioner(
      UserRepository users,
      PasswordEncoder passwords,
      AuditService audit,
      @Value("${app.admin-email}") String email,
      @Value("${app.admin-password}") String password) {
    this.users = users;
    this.passwords = passwords;
    this.audit = audit;
    this.email = email;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (email.isBlank()) return;
    if (password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        || password.startsWith("CHANGE_ME"))
      throw new IllegalStateException("Set a strong ADMIN_PASSWORD before provisioning");
    if (users.existsByEmail(email.toLowerCase(Locale.ROOT))) return;
    UserAccount u = new UserAccount();
    u.fullName = "Platform Administrator";
    u.email = email.toLowerCase(Locale.ROOT);
    u.passwordHash = passwords.encode(password);
    u.role = Role.ADMIN;
    users.save(u);
    audit.record(u.id, "ADMIN_PROVISIONED", "USER", u.id);
  }
}

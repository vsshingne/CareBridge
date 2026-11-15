package com.carebridge.user;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {
  Optional<UserAccount> findByEmail(String email);

  boolean existsByEmail(String email);

  org.springframework.data.domain.Page<UserAccount> findByRole(
      Role role, org.springframework.data.domain.Pageable pageable);
}

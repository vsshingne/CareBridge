package com.carebridge.auth;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshToken t where t.tokenHash = :hash")
  Optional<RefreshToken> lockByHash(String hash);

  @Modifying
  @Query("update RefreshToken t set t.revoked=true where t.familyId=:family")
  void revokeFamily(UUID family);

  @Modifying
  @Query("update RefreshToken t set t.revoked=true where t.userId=:userId")
  void revokeUser(UUID userId);
}

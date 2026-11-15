package com.carebridge.auth;

import com.carebridge.user.UserAccount;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final JwtEncoder encoder;

  public JwtService(JwtEncoder encoder) {
    this.encoder = encoder;
  }

  public String issue(UserAccount user) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("carebridge")
            .subject(user.id.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .claim("role", user.role.name())
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}

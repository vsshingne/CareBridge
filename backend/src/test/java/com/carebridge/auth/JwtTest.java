package com.carebridge.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class JwtTest {
  @Test
  void rejectsExpiredAndWronglySignedTokens() {
    var key = new SecretKeySpec(new byte[32], "HmacSHA256");
    var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("carebridge"));
    var claims =
        JwtClaimsSet.builder()
            .issuer("carebridge")
            .subject("test")
            .issuedAt(Instant.now().minusSeconds(7200))
            .expiresAt(Instant.now().minusSeconds(3600))
            .build();
    String token =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    assertThrows(JwtException.class, () -> decoder.decode(token));
    byte[] other = new byte[32];
    other[0] = 1;
    var otherDecoder =
        NimbusJwtDecoder.withSecretKey(new SecretKeySpec(other, "HmacSHA256")).build();
    assertThrows(JwtException.class, () -> otherDecoder.decode(token));
  }
}

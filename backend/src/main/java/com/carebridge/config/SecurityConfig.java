package com.carebridge.config;

import com.carebridge.common.Errors;
import com.carebridge.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecretKeySpec jwtKey(@Value("${app.jwt-secret}") String encoded) {
    byte[] bytes = Base64.getDecoder().decode(encoded);
    if (bytes.length < 32)
      throw new IllegalStateException(
          "JWT_SECRET must contain at least 32 random bytes, base64 encoded");
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKeySpec key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKeySpec key) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("carebridge"));
    return decoder;
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      UserRepository users,
      ObjectMapper mapper,
      com.carebridge.audit.AuditService audit)
      throws Exception {
    return http.cors(c -> {})
        .csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/auth/**",
                        "/actuator/health",
                        "/actuator/info",
                        "/actuator/health/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/actuator/metrics", "/actuator/metrics/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/doctors/me/**")
                    .hasRole("DOCTOR")
                    .requestMatchers("/api/v1/patients/**")
                    .hasRole("PATIENT")
                    .requestMatchers(HttpMethod.GET, "/api/v1/doctors/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            o ->
                o.jwt(
                        j ->
                            j.jwtAuthenticationConverter(
                                jwt -> {
                                  var u =
                                      users
                                          .findById(UUID.fromString(jwt.getSubject()))
                                          .orElseThrow(
                                              () ->
                                                  new OAuth2AuthenticationException(
                                                      "invalid_token"));
                                  if (!u.enabled)
                                    throw new OAuth2AuthenticationException("invalid_token");
                                  return new JwtAuthenticationToken(
                                      jwt,
                                      List.of(new SimpleGrantedAuthority("ROLE_" + u.role.name())));
                                }))
                    .authenticationEntryPoint(
                        (r, s, e) -> {
                          s.setStatus(401);
                          s.setContentType("application/json");
                          mapper.writeValue(
                              s.getOutputStream(),
                              Errors.body(
                                  HttpStatus.UNAUTHORIZED,
                                  "Authentication required or expired",
                                  r.getRequestURI()));
                        }))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (r, s, x) -> {
                          s.setStatus(401);
                          s.setContentType("application/json");
                          mapper.writeValue(
                              s.getOutputStream(),
                              Errors.body(
                                  HttpStatus.UNAUTHORIZED,
                                  "Authentication required",
                                  r.getRequestURI()));
                        })
                    .accessDeniedHandler(
                        (r, s, x) -> {
                          audit.authorizationFailure();
                          s.setStatus(403);
                          s.setContentType("application/json");
                          mapper.writeValue(
                              s.getOutputStream(),
                              Errors.body(
                                  HttpStatus.FORBIDDEN, "Access denied", r.getRequestURI()));
                        }))
        .build();
  }

  @Bean
  CorsConfigurationSource cors(@Value("${app.allowed-origins}") String origins) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(List.of(origins.split(",")));
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    c.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }
}

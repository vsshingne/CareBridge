package com.carebridge.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
  private final com.carebridge.audit.AuditService audit;
  private final io.micrometer.core.instrument.MeterRegistry metrics;
  public Errors(com.carebridge.audit.AuditService audit,io.micrometer.core.instrument.MeterRegistry metrics){this.audit=audit;this.metrics=metrics;}
  public record ErrorBody(
      Instant timestamp, int status, String error, String message, String path) {}

  public static ErrorBody body(HttpStatus status, String message, String path) {
    return new ErrorBody(Instant.now(), status.value(), status.getReasonPhrase(), message, path);
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorBody> api(ApiException e, HttpServletRequest r) {
    if(e.status==HttpStatus.FORBIDDEN)audit.authorizationFailure();
    if(e.status==HttpStatus.CONFLICT)metrics.counter("carebridge.conflicts", "workflow", r.getRequestURI().startsWith("/api/v1/appointments")||r.getRequestURI().startsWith("/api/v1/recovery")?"appointment":"care").increment();
    return ResponseEntity.status(e.status).body(body(e.status, e.getMessage(), r.getRequestURI()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorBody> conflict(Exception e, HttpServletRequest r) {
    return api(
        ApiException.conflict(
            "The record conflicts with existing data or the selected slot is no longer available"),
        r);
  }

  @ExceptionHandler({
    org.springframework.dao.OptimisticLockingFailureException.class,
    org.springframework.dao.PessimisticLockingFailureException.class
  })
  ResponseEntity<ErrorBody> concurrent(Exception e, HttpServletRequest r) {
    return api(
        ApiException.conflict("This record was changed concurrently. Please refresh and retry."),
        r);
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<ErrorBody> missing(Exception e, HttpServletRequest r) {
    return api(ApiException.notFound(), r);
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorBody> method(Exception e, HttpServletRequest r) {
    return api(
        new ApiException(
            HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not supported for this endpoint"),
        r);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    java.time.DateTimeException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    IllegalArgumentException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    jakarta.validation.ConstraintViolationException.class
  })
  ResponseEntity<ErrorBody> invalid(Exception e, HttpServletRequest r) {
    return api(
        ApiException.badRequest(
            "Invalid request. Check required fields, formats and allowed values."),
        r);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorBody> forbidden(Exception e, HttpServletRequest r) {
    return api(ApiException.forbidden(), r);
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorBody> mediaType(Exception e, HttpServletRequest r) {
    return api(
        new ApiException(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use application/json for this request"),
        r);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorBody> unexpected(Exception e, HttpServletRequest r) {
    LoggerFactory.getLogger(Errors.class).error("Request failed: {}", e.getClass().getSimpleName());
    return ResponseEntity.status(500)
        .body(
            body(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                r.getRequestURI()));
  }
}

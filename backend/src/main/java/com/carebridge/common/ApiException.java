package com.carebridge.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
  public final HttpStatus status;

  public ApiException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public static ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "Resource not found");
  }

  public static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, message);
  }

  public static ApiException badRequest(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException forbidden() {
    return new ApiException(HttpStatus.FORBIDDEN, "You do not have permission for this operation");
  }

  public static ApiException unauthorized() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "Authentication required or credentials invalid");
  }
}

package com.carebridge.common;

import java.util.*;
import org.springframework.data.domain.*;

public final class Pages {
  private Pages() {}

  public static Pageable checked(Pageable p, String... fields) {
    Set<String> allowed = Set.of(fields);
    for (Sort.Order order : p.getSort())
      if (!allowed.contains(order.getProperty()))
        throw ApiException.badRequest("Unsupported sort field");
    return PageRequest.of(p.getPageNumber(), Math.min(p.getPageSize(), 100), p.getSort());
  }

  public record Result<T>(
      List<T> content, long totalElements, int totalPages, int number, int size) {
    public static <T> Result<T> from(Page<T> p) {
      return new Result<>(
          p.getContent(), p.getTotalElements(), p.getTotalPages(), p.getNumber(), p.getSize());
    }
  }
}

package com.subsense.common;

import java.util.Map;

/** A business rule that spans fields failed, reported against the field the user should fix. */
public class FieldValidationException extends RuntimeException {

  private final String field;

  public FieldValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public Map<String, String> errors() {
    return Map.of(field, getMessage());
  }
}

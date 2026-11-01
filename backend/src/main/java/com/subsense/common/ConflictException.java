package com.subsense.common;

/** The request is valid but the resource's state does not allow it (HTTP 409). */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}

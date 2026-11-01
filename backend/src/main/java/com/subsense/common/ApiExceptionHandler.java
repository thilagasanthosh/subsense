package com.subsense.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps errors to RFC 7807 problem details, with field-level messages for the UI. */
@RestControllerAdvice
class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail handleInvalidRequest(MethodArgumentNotValidException ex) {
    Map<String, String> errors = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return validationProblem(errors);
  }

  @ExceptionHandler(FieldValidationException.class)
  ProblemDetail handleFieldValidation(FieldValidationException ex) {
    return validationProblem(ex.errors());
  }

  @ExceptionHandler(NotFoundException.class)
  ProblemDetail handleNotFound(NotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ProblemDetail handleConcurrentUpdate() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "This subscription was changed elsewhere. Reload and try again.");
  }

  private static ProblemDetail validationProblem(Map<String, String> errors) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Some fields are not valid.");
    problem.setTitle("Validation failed");
    problem.setProperty("errors", errors);
    return problem;
  }
}

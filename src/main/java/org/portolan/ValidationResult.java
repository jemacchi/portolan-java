package org.portolan;

import java.util.List;

/** Validation outcome. */
public record ValidationResult(List<ValidationError> errors) {
  public boolean valid() {
    return errors.isEmpty();
  }
}

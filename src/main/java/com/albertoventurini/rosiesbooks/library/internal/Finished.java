package com.albertoventurini.rosiesbooks.library.internal;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** A completed book whose start date may be historically unknown. */
public record Finished(Optional<LocalDate> startedOn, LocalDate finishedOn)
    implements ReadingState {

  public Finished {
    Objects.requireNonNull(startedOn, "startedOn");
    Objects.requireNonNull(finishedOn, "finishedOn");
    if (startedOn.filter(start -> finishedOn.isBefore(start)).isPresent()) {
      throw new IllegalArgumentException("Finish date cannot precede start date");
    }
  }
}

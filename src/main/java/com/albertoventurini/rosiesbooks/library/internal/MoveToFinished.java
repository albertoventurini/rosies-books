package com.albertoventurini.rosiesbooks.library.internal;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Moves the book to Finished, optionally supplying a start date when it is unknown. */
public record MoveToFinished(LocalDate finishedOn, Optional<LocalDate> startedOnWhenUnknown)
    implements ReadingStateTransition {

  public MoveToFinished {
    Objects.requireNonNull(finishedOn, "finishedOn");
    Objects.requireNonNull(startedOnWhenUnknown, "startedOnWhenUnknown");
  }
}

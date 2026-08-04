package com.albertoventurini.rosiesbooks.library.internal;

import java.time.LocalDate;
import java.util.Objects;

/** Moves the book to Reading using the caller's browser-local current date when needed. */
public record MoveToReading(LocalDate localToday) implements ReadingStateTransition {

  public MoveToReading {
    Objects.requireNonNull(localToday, "localToday");
  }
}

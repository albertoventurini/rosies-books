package com.albertoventurini.rosiesbooks.library.internal;

import java.time.LocalDate;
import java.util.Objects;

/** A book currently being read. */
public record Reading(LocalDate startedOn) implements ReadingState {

  public Reading {
    Objects.requireNonNull(startedOn, "startedOn");
  }
}

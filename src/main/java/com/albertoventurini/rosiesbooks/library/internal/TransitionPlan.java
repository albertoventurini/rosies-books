package com.albertoventurini.rosiesbooks.library.internal;

import java.util.Objects;
import java.util.Optional;

/** The validated state resulting from a transition and any required confirmation. */
public record TransitionPlan(
    ReadingState resultingState, Optional<ConfirmationRequirement> confirmationRequirement) {

  public TransitionPlan {
    Objects.requireNonNull(resultingState, "resultingState");
    Objects.requireNonNull(confirmationRequirement, "confirmationRequirement");
  }
}

package com.albertoventurini.rosiesbooks.library.internal;

import java.util.Objects;
import java.util.Optional;

/** Plans reading-state changes without performing persistence or other side effects. */
public final class ReadingStateTransitions {

  public TransitionPlan plan(ReadingState current, ReadingStateTransition transition) {
    Objects.requireNonNull(current, "current");
    Objects.requireNonNull(transition, "transition");

    return switch (transition) {
      case MoveToRead ignored -> moveToRead(current);
      case MoveToReading command -> moveToReading(current, command);
      case MoveToFinished command -> moveToFinished(current, command);
    };
  }

  private static TransitionPlan moveToRead(ReadingState current) {
    if (current instanceof ToRead) {
      throw sameState("To Read");
    }
    return new TransitionPlan(
        new ToRead(), Optional.of(ConfirmationRequirement.DISCARD_RECORDED_DATES));
  }

  private static TransitionPlan moveToReading(ReadingState current, MoveToReading command) {
    return switch (current) {
      case ToRead ignored -> plan(new Reading(command.localToday()));
      case Reading ignored -> throw sameState("Reading");
      case Finished finished ->
          plan(new Reading(finished.startedOn().orElse(command.localToday())));
    };
  }

  private static TransitionPlan moveToFinished(ReadingState current, MoveToFinished command) {
    return switch (current) {
      case ToRead ignored ->
          plan(new Finished(command.startedOnWhenUnknown(), command.finishedOn()));
      case Reading reading -> {
        if (command.startedOnWhenUnknown().isPresent()) {
          throw new IllegalArgumentException(
              "A Reading book retains its existing start date; no replacement is permitted");
        }
        yield plan(new Finished(Optional.of(reading.startedOn()), command.finishedOn()));
      }
      case Finished ignored -> throw sameState("Finished");
    };
  }

  private static TransitionPlan plan(ReadingState state) {
    return new TransitionPlan(state, Optional.empty());
  }

  private static IllegalArgumentException sameState(String state) {
    return new IllegalArgumentException("Book is already " + state);
  }
}

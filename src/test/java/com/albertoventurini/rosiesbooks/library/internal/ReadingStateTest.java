package com.albertoventurini.rosiesbooks.library.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReadingStateTest {

  private static final LocalDate STARTED = LocalDate.of(2026, 7, 1);
  private static final LocalDate FINISHED = LocalDate.of(2026, 8, 4);

  @Test
  void permitsExactlyTheValidStateAndDateShapes() {
    assertDoesNotThrow(ToRead::new);
    assertDoesNotThrow(() -> new Reading(STARTED));
    assertDoesNotThrow(() -> new Finished(Optional.empty(), FINISHED));
    assertDoesNotThrow(() -> new Finished(Optional.of(STARTED), FINISHED));
    assertDoesNotThrow(() -> new Finished(Optional.of(FINISHED), FINISHED));

    assertThrows(NullPointerException.class, () -> new Reading(null));
    assertThrows(NullPointerException.class, () -> new Finished(null, FINISHED));
    assertThrows(NullPointerException.class, () -> new Finished(Optional.empty(), null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Finished(Optional.of(FINISHED.plusDays(1)), FINISHED));
  }

  @Test
  void performsAllSixCrossStateTransitionsWithExactDateBehavior() {
    LocalDate localToday = LocalDate.of(2026, 8, 4);
    ReadingStateTransitions transitions = new ReadingStateTransitions();

    assertEquals(
        plan(new Reading(localToday)),
        transitions.plan(new ToRead(), new MoveToReading(localToday)));
    assertEquals(
        plan(new Finished(Optional.of(STARTED), FINISHED)),
        transitions.plan(new ToRead(), new MoveToFinished(FINISHED, Optional.of(STARTED))));
    assertEquals(
        plan(new Finished(Optional.empty(), FINISHED)),
        transitions.plan(new ToRead(), new MoveToFinished(FINISHED, Optional.empty())));
    assertEquals(
        plan(new Finished(Optional.of(STARTED), FINISHED)),
        transitions.plan(new Reading(STARTED), new MoveToFinished(FINISHED, Optional.empty())));
    assertEquals(
        plan(new Reading(STARTED)),
        transitions.plan(
            new Finished(Optional.of(STARTED), FINISHED), new MoveToReading(localToday)));
    assertEquals(
        plan(new Reading(localToday)),
        transitions.plan(new Finished(Optional.empty(), FINISHED), new MoveToReading(localToday)));
    assertEquals(discardDatesPlan(), transitions.plan(new Reading(STARTED), new MoveToRead()));
    assertEquals(
        discardDatesPlan(),
        transitions.plan(new Finished(Optional.of(STARTED), FINISHED), new MoveToRead()));
  }

  @Test
  void rejectsSameStateUnsupportedAndInvalidTransitions() {
    ReadingStateTransitions transitions = new ReadingStateTransitions();

    assertThrows(
        IllegalArgumentException.class, () -> transitions.plan(new ToRead(), new MoveToRead()));
    assertThrows(
        IllegalArgumentException.class,
        () -> transitions.plan(new Reading(STARTED), new MoveToReading(FINISHED)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            transitions.plan(
                new Finished(Optional.empty(), FINISHED),
                new MoveToFinished(FINISHED, Optional.empty())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            transitions.plan(
                new Reading(STARTED),
                new MoveToFinished(FINISHED, Optional.of(STARTED.minusDays(1)))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            transitions.plan(
                new ToRead(), new MoveToFinished(STARTED, Optional.of(STARTED.plusDays(1)))));
  }

  @Test
  void rejectsMissingCommandInputsAndNullPlanningInputs() {
    ReadingStateTransitions transitions = new ReadingStateTransitions();

    assertThrows(NullPointerException.class, () -> new MoveToReading(null));
    assertThrows(NullPointerException.class, () -> new MoveToFinished(null, Optional.empty()));
    assertThrows(NullPointerException.class, () -> new MoveToFinished(FINISHED, null));
    assertThrows(NullPointerException.class, () -> transitions.plan(null, new MoveToRead()));
    assertThrows(NullPointerException.class, () -> transitions.plan(new ToRead(), null));
  }

  private static TransitionPlan plan(ReadingState state) {
    return new TransitionPlan(state, Optional.empty());
  }

  private static TransitionPlan discardDatesPlan() {
    return new TransitionPlan(
        new ToRead(), Optional.of(ConfirmationRequirement.DISCARD_RECORDED_DATES));
  }
}

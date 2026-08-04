package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.library.internal.Finished;
import com.albertoventurini.rosiesbooks.library.internal.Reading;
import com.albertoventurini.rosiesbooks.library.internal.ReadingState;
import com.albertoventurini.rosiesbooks.library.persistence.StateChangeService.BookState;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

record StateChangePage(
    String productName,
    String userDisplayLabel,
    String bookId,
    String title,
    String currentState,
    String currentShelfRoute,
    long version,
    String target,
    String readingStartedOn,
    String finishedStartedOn,
    String finishedOn,
    String currentStartedOn,
    List<StateTargetView> targets,
    Map<String, List<String>> errors,
    boolean readingStartEditable,
    boolean readingStartRetained,
    boolean confirmation,
    boolean conflict) {

  StateChangePage {
    targets = List.copyOf(targets);
    errors = Map.copyOf(errors);
  }

  static StateChangePage form(
      String userLabel,
      BookState book,
      String target,
      String readingStartedOn,
      String finishedStartedOn,
      String finishedOn,
      Map<String, List<String>> errors) {
    return create(
        userLabel,
        book,
        target,
        readingStartedOn,
        finishedStartedOn,
        finishedOn,
        errors,
        false,
        false);
  }

  static StateChangePage confirmation(String userLabel, BookState book) {
    String finish =
        book.state() instanceof Finished finished ? finished.finishedOn().toString() : "";
    return create(userLabel, book, "TO_READ", "", "", finish, Map.of(), true, false);
  }

  static StateChangePage conflict(String userLabel, BookState book) {
    return create(userLabel, book, "", "", "", "", Map.of(), false, true);
  }

  private static StateChangePage create(
      String userLabel,
      BookState book,
      String target,
      String readingStartedOn,
      String finishedStartedOn,
      String finishedOn,
      Map<String, List<String>> errors,
      boolean confirmation,
      boolean conflict) {
    return new StateChangePage(
        "Rosie's books",
        userLabel,
        book.id().value().toString(),
        book.title(),
        label(book.state()),
        route(book.state()),
        book.version(),
        target,
        readingStartedOn == null ? "" : readingStartedOn,
        finishedStartedOn == null ? "" : finishedStartedOn,
        finishedOn == null ? "" : finishedOn,
        currentStart(book.state()),
        targets(book.state(), target),
        errors,
        readingStartEditable(book.state()),
        readingStartRetained(book.state()),
        confirmation,
        conflict);
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  public List<String> errorsFor(String field) {
    return errors.getOrDefault(field, List.of());
  }

  public boolean hasError(String field) {
    return errors.containsKey(field);
  }

  public boolean toFinished() {
    return target.equals("FINISHED");
  }

  public boolean toReading() {
    return target.equals("READING");
  }

  public boolean finishedStartEditable() {
    return currentState.equals("To Read");
  }

  public boolean finishedStartRetained() {
    return currentState.equals("Reading");
  }

  public boolean clearsStart() {
    return confirmation && !startedDateFromCurrent().isEmpty();
  }

  public boolean clearsFinish() {
    return confirmation && !finishedDateFromCurrent().isEmpty();
  }

  public String startedDateFromCurrent() {
    return currentStartedOn;
  }

  public String finishedDateFromCurrent() {
    return finishedOn;
  }

  private static List<StateTargetView> targets(ReadingState state, String selected) {
    if (state instanceof com.albertoventurini.rosiesbooks.library.internal.ToRead) {
      return List.of(
          new StateTargetView("READING", "Reading", selected.equals("READING")),
          new StateTargetView("FINISHED", "Finished", selected.equals("FINISHED")));
    }
    if (state instanceof Reading) {
      return List.of(
          new StateTargetView("TO_READ", "To Read", selected.equals("TO_READ")),
          new StateTargetView("FINISHED", "Finished", selected.equals("FINISHED")));
    }
    return List.of(
        new StateTargetView("TO_READ", "To Read", selected.equals("TO_READ")),
        new StateTargetView("READING", "Reading", selected.equals("READING")));
  }

  private static boolean readingStartEditable(ReadingState state) {
    return state instanceof com.albertoventurini.rosiesbooks.library.internal.ToRead
        || state instanceof Finished finished && finished.startedOn().isEmpty();
  }

  private static boolean readingStartRetained(ReadingState state) {
    return state instanceof Finished finished && finished.startedOn().isPresent();
  }

  private static String currentStart(ReadingState state) {
    if (state instanceof Reading reading) return reading.startedOn().toString();
    if (state instanceof Finished finished) {
      return finished.startedOn().map(LocalDate::toString).orElse("");
    }
    return "";
  }

  static String route(ReadingState state) {
    if (state instanceof Reading) return "/reading";
    if (state instanceof Finished) return "/finished";
    return "/to-read";
  }

  private static String label(ReadingState state) {
    if (state instanceof Reading) return "Reading";
    if (state instanceof Finished) return "Finished";
    return "To Read";
  }
}

record StateTargetView(String value, String label, boolean selected) {}

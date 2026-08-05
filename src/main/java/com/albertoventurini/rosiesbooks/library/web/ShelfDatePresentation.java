package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.library.internal.Finished;
import com.albertoventurini.rosiesbooks.library.internal.Reading;
import com.albertoventurini.rosiesbooks.library.internal.ReadingState;
import com.albertoventurini.rosiesbooks.library.internal.ToRead;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

final class ShelfDatePresentation {

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);

  private ShelfDatePresentation() {}

  static String stateLabel(ReadingState state) {
    Objects.requireNonNull(state, "state");
    return switch (state) {
      case Reading ignored -> "Reading";
      case ToRead ignored -> "To Read";
      case Finished ignored -> "Finished";
    };
  }

  static String contextLine(ReadingState state, Instant createdAt, LocalDate today, ZoneId zone) {
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(today, "today");
    Objects.requireNonNull(zone, "zone");
    return switch (state) {
      case Reading reading -> "Started " + DATE.format(reading.startedOn());
      case Finished finished -> "Finished " + DATE.format(finished.finishedOn());
      case ToRead ignored -> addedAge(createdAt.atZone(zone).toLocalDate(), today);
    };
  }

  private static String addedAge(LocalDate addedOn, LocalDate today) {
    long days = Math.max(0, ChronoUnit.DAYS.between(addedOn, today));
    if (days == 0) {
      return "Added today";
    }
    if (days < 7) {
      return "Added " + quantity(days, "day") + " ago";
    }
    if (days < 30) {
      return "Added " + quantity(days / 7, "week") + " ago";
    }
    if (days < 365) {
      return "Added " + quantity(days / 30, "month") + " ago";
    }
    return "Added " + quantity(days / 365, "year") + " ago";
  }

  private static String quantity(long value, String unit) {
    return value + " " + unit + (value == 1 ? "" : "s");
  }
}

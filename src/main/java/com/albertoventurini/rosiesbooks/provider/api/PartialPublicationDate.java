package com.albertoventurini.rosiesbooks.provider.api;

import java.time.DateTimeException;
import java.time.LocalDate;

/** A provider publication date retaining whether only a year or month was supplied. */
public record PartialPublicationDate(Integer year, Integer month, Integer day) {
  public PartialPublicationDate {
    if (year == null) {
      if (month != null || day != null) throw new DateTimeException("A date needs a year");
    } else if (month == null) {
      if (day != null || year < 1 || year > 9999) throw new DateTimeException("Invalid year");
    } else if (day == null) {
      if (year < 1 || year > 9999 || month < 1 || month > 12)
        throw new DateTimeException("Invalid year-month");
    } else {
      LocalDate.of(year, month, day);
    }
  }

  public static PartialPublicationDate year(int year) {
    return new PartialPublicationDate(year, null, null);
  }

  public static PartialPublicationDate yearMonth(int year, int month) {
    return new PartialPublicationDate(year, month, null);
  }

  public static PartialPublicationDate full(int year, int month, int day) {
    return new PartialPublicationDate(year, month, day);
  }
}

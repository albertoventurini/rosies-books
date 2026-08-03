package com.albertoventurini.rosiesbooks.library.internal;

import java.time.DateTimeException;
import java.time.LocalDate;

/** A publication date whose precision is retained exactly. */
public record PartialPublicationDate(Integer year, Integer month, Integer day)
    implements Comparable<PartialPublicationDate> {

  public PartialPublicationDate {
    if (year == null) {
      if (month != null || day != null) {
        throw new DateTimeException("A publication month or day requires a year");
      }
    } else {
      if (year < 1 || year > 9999) {
        throw new DateTimeException("Publication year must be between 1 and 9999");
      }
      if (month == null) {
        if (day != null) {
          throw new DateTimeException("A publication day requires a month");
        }
      } else if (day == null) {
        if (month < 1 || month > 12) {
          throw new DateTimeException("Publication month must be between 1 and 12");
        }
      } else {
        LocalDate.of(year, month, day);
      }
    }
  }

  public static PartialPublicationDate unknown() {
    return new PartialPublicationDate(null, null, null);
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

  @Override
  public int compareTo(PartialPublicationDate other) {
    if (year == null || other.year == null) {
      if (year == null && other.year == null) {
        return 0;
      }
      return year == null ? 1 : -1;
    }
    int comparison = year.compareTo(other.year);
    if (comparison != 0) {
      return comparison;
    }
    comparison = compareComponent(month, other.month);
    if (comparison != 0) {
      return comparison;
    }
    return compareComponent(day, other.day);
  }

  private static int compareComponent(Integer left, Integer right) {
    if (left == null || right == null) {
      if (left == null && right == null) {
        return 0;
      }
      return left == null ? -1 : 1;
    }
    return left.compareTo(right);
  }
}

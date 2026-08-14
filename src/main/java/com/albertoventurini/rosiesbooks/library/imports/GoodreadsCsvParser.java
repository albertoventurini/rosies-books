package com.albertoventurini.rosiesbooks.library.imports;

import com.albertoventurini.rosiesbooks.library.internal.Isbn10;
import com.albertoventurini.rosiesbooks.library.internal.Isbn13;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/** Parses the deliberately small, stable subset of Goodreads' CSV export. */
final class GoodreadsCsvParser {
  private static final Set<String> REQUIRED = Set.of("Title", "Author", "Exclusive Shelf");

  GoodreadsParseResult parse(String csv) {
    List<String> errors = new ArrayList<>();
    List<GoodreadsRow> rows = new ArrayList<>();
    try (CSVParser parser =
        CSVParser.parse(
            new StringReader(csv),
            CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).build())) {
      Map<String, Integer> headers = parser.getHeaderMap();
      if (!headers.keySet().containsAll(REQUIRED)) {
        return GoodreadsParseResult.invalid(
            List.of("The CSV must contain Title, Author, and Exclusive Shelf columns."));
      }
      Set<String> isbns = new LinkedHashSet<>();
      for (CSVRecord record : parser) {
        long line = record.getRecordNumber() + 1;
        String title = value(record, "Title").trim();
        List<String> authors =
            authors(value(record, "Author"), value(record, "Additional Authors"));
        if (title.isBlank() || title.length() > 500)
          errors.add("Row " + line + " has no valid title.");
        if (authors.isEmpty()) errors.add("Row " + line + " has no valid author.");
        Optional<Isbn13> isbn = canonicalIsbn(value(record, "ISBN"), value(record, "ISBN13"));
        if (isbn.isPresent() && !isbns.add(isbn.get().value()))
          errors.add("Row " + line + " duplicates an ISBN in this file.");
        rows.add(
            new GoodreadsRow(
                title,
                authors,
                isbn,
                optional(value(record, "Publisher"), 500),
                optional(value(record, "Binding"), 500),
                positive(value(record, "Number of Pages")),
                year(value(record, "Year Published")),
                optional(value(record, "Private Notes"), 10_000),
                shelf(value(record, "Exclusive Shelf")),
                date(value(record, "Date Added")),
                date(value(record, "Date Read"))));
      }
    } catch (IOException | RuntimeException exception) {
      return GoodreadsParseResult.invalid(List.of("The uploaded file is not a valid CSV export."));
    }
    if (rows.isEmpty()) errors.add("The CSV contains no books.");
    return errors.isEmpty()
        ? GoodreadsParseResult.valid(rows)
        : GoodreadsParseResult.invalid(errors);
  }

  private static String value(CSVRecord record, String header) {
    return record.isMapped(header) ? record.get(header) : "";
  }

  private static Optional<String> optional(String value, int limit) {
    String text = value == null ? "" : value.trim();
    return text.isBlank() || text.length() > limit ? Optional.empty() : Optional.of(text);
  }

  private static Integer positive(String value) {
    try {
      int number = Integer.parseInt(value.trim());
      return number > 0 && number <= 1_000_000 ? number : null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static Integer year(String value) {
    try {
      int year = Integer.parseInt(value.trim());
      return year >= 1 && year <= 9999 ? year : null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static LocalDate date(String value) {
    String text = value == null ? "" : value.trim();
    for (DateTimeFormatter formatter :
        List.of(
            DateTimeFormatter.ofPattern("uuuu/MM/dd"),
            DateTimeFormatter.ofPattern("M/d/uuuu"),
            DateTimeFormatter.ISO_LOCAL_DATE))
      try {
        return LocalDate.parse(text, formatter);
      } catch (DateTimeParseException ignored) {
      }
    return null;
  }

  private static String shelf(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private static List<String> authors(String main, String additional) {
    LinkedHashSet<String> all = new LinkedHashSet<>();
    addAuthors(all, main);
    addAuthors(all, additional);
    return all.stream().limit(20).toList();
  }

  private static void addAuthors(Set<String> target, String source) {
    for (String author : source == null ? new String[0] : source.split(",")) {
      String text = author.trim();
      if (!text.isBlank() && text.length() <= 300) target.add(text);
    }
  }

  private static Optional<Isbn13> canonicalIsbn(String isbn10Raw, String isbn13Raw) {
    Optional<Isbn10> ten = parse10(cleanIsbn(isbn10Raw));
    Optional<Isbn13> thirteen = parse13(cleanIsbn(isbn13Raw));
    if (ten.isPresent() && thirteen.isPresent() && !ten.get().toIsbn13().equals(thirteen.get()))
      return Optional.empty();
    return thirteen.isPresent() ? thirteen : ten.map(Isbn10::toIsbn13);
  }

  private static String cleanIsbn(String raw) {
    String text = raw == null ? "" : raw.trim();
    return text.matches("=\\\".*\\\"") ? text.substring(2, text.length() - 1) : text;
  }

  private static Optional<Isbn10> parse10(String value) {
    try {
      return value.isBlank() ? Optional.empty() : Optional.of(Isbn10.parse(value));
    } catch (RuntimeException ignored) {
      return Optional.empty();
    }
  }

  private static Optional<Isbn13> parse13(String value) {
    try {
      return value.isBlank() ? Optional.empty() : Optional.of(Isbn13.parse(value));
    } catch (RuntimeException ignored) {
      return Optional.empty();
    }
  }

  record GoodreadsRow(
      String title,
      List<String> authors,
      Optional<Isbn13> isbn13,
      Optional<String> publisher,
      Optional<String> format,
      Integer pageCount,
      Integer publicationYear,
      Optional<String> notes,
      String shelf,
      LocalDate addedOn,
      LocalDate readOn) {}

  record GoodreadsParseResult(List<GoodreadsRow> rows, List<String> errors) {
    static GoodreadsParseResult valid(List<GoodreadsRow> rows) {
      return new GoodreadsParseResult(List.copyOf(rows), List.of());
    }

    static GoodreadsParseResult invalid(List<String> errors) {
      return new GoodreadsParseResult(List.of(), List.copyOf(errors));
    }

    boolean valid() {
      return errors.isEmpty();
    }
  }
}

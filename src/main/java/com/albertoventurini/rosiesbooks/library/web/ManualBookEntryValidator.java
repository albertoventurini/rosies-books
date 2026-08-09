package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.library.internal.CanonicalIsbns;
import com.albertoventurini.rosiesbooks.library.internal.EditionMetadata;
import com.albertoventurini.rosiesbooks.library.internal.EditionMetadataLimits;
import com.albertoventurini.rosiesbooks.library.internal.Finished;
import com.albertoventurini.rosiesbooks.library.internal.Isbn10;
import com.albertoventurini.rosiesbooks.library.internal.Isbn13;
import com.albertoventurini.rosiesbooks.library.internal.PartialPublicationDate;
import com.albertoventurini.rosiesbooks.library.internal.Reading;
import com.albertoventurini.rosiesbooks.library.internal.ReadingState;
import com.albertoventurini.rosiesbooks.library.internal.ToRead;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ManualBookEntryValidator {

  private static final Pattern PUBLICATION_DATE =
      Pattern.compile("^(\\d{4})(?:-(\\d{2})(?:-(\\d{2}))?)?$");

  private final Clock clock;
  private final ZoneId defaultZone;

  ManualBookEntryValidator(Clock clock, ZoneId defaultZone) {
    this.clock = java.util.Objects.requireNonNull(clock, "clock");
    this.defaultZone = java.util.Objects.requireNonNull(defaultZone, "defaultZone");
  }

  ManualBookForm prepare(ManualBookForm submitted) {
    ManualBookForm form = submitted.withErrors(Map.of());
    String today = LocalDate.now(clock.withZone(defaultZone)).toString();
    return switch (form.state()) {
      case "TO_READ" -> form.withState(form.state(), "", "");
      case "READING" ->
          form.withState(form.state(), form.startedOn().isBlank() ? today : form.startedOn(), "");
      case "FINISHED" ->
          form.withState(
              form.state(),
              form.startedOn(),
              form.finishedOn().isBlank() ? today : form.finishedOn());
      default -> form;
    };
  }

  ManualBookValidation validate(ManualBookForm submitted) {
    ManualBookForm form = prepare(submitted);
    Errors errors = new Errors();

    String title = form.title().strip();
    if (title.isEmpty()) {
      errors.add("title", "Enter a title.");
    } else {
      maximum(errors, "title", title, EditionMetadataLimits.TITLE, "Title");
    }

    List<String> authors =
        form.authors().stream().map(String::strip).filter(author -> !author.isEmpty()).toList();
    if (authors.size() < EditionMetadataLimits.MIN_AUTHORS) {
      errors.add("authors", "Enter at least one author.");
    } else if (authors.size() > EditionMetadataLimits.MAX_AUTHORS) {
      errors.add("authors", "Enter no more than 20 authors.");
    }
    for (int index = 0; index < authors.size(); index++) {
      if (authors.get(index).length() > EditionMetadataLimits.AUTHOR) {
        errors.add("authors", "Author " + (index + 1) + " must be 300 characters or fewer.");
      }
    }

    Optional<String> subtitle =
        optionalText(
            errors, "subtitle", form.subtitle(), EditionMetadataLimits.SHORT_TEXT, "Subtitle");
    Optional<String> format =
        optionalText(errors, "format", form.format(), EditionMetadataLimits.SHORT_TEXT, "Format");
    Optional<String> publisher =
        optionalText(
            errors, "publisher", form.publisher(), EditionMetadataLimits.SHORT_TEXT, "Publisher");
    Optional<String> language =
        optionalText(
            errors, "language", form.language(), EditionMetadataLimits.SHORT_TEXT, "Language");
    Optional<String> description =
        optionalText(
            errors,
            "description",
            form.description(),
            EditionMetadataLimits.DESCRIPTION,
            "Description");

    Optional<Isbn10> isbn10 = parseIsbn10(form.isbn10(), errors);
    Optional<Isbn13> isbn13 = parseIsbn13(form.isbn13(), errors);
    CanonicalIsbns canonicalIsbns = null;
    if (!errors.has("isbn10") && !errors.has("isbn13")) {
      try {
        canonicalIsbns = new CanonicalIsbns(isbn10, isbn13);
      } catch (IllegalArgumentException exception) {
        errors.add("isbn10", "ISBN-10 and ISBN-13 identify different editions.");
        errors.add("isbn13", "ISBN-10 and ISBN-13 identify different editions.");
      }
    }

    Optional<PartialPublicationDate> publicationDate =
        parsePublicationDate(form.publicationDate(), errors);
    Optional<Integer> pageCount = parsePageCount(form.pageCount(), errors);
    ReadingState state = parseReadingState(form, errors);

    if (!errors.empty()) {
      return new ManualBookValidation(form.withErrors(errors.copy()), Optional.empty());
    }

    EditionMetadata metadata =
        new EditionMetadata(
            title,
            subtitle,
            authors,
            format,
            canonicalIsbns.isbn10(),
            canonicalIsbns.isbn13(),
            publisher,
            publicationDate,
            pageCount,
            language,
            description);
    return new ManualBookValidation(form, Optional.of(new ManualBookDraft(metadata, state)));
  }

  ManualBookForm validateState(ManualBookForm submitted) {
    ManualBookForm form = prepare(submitted);
    Errors errors = new Errors();
    parseReadingState(form, errors);
    return form.withErrors(errors.copy());
  }

  private static Optional<String> optionalText(
      Errors errors, String field, String raw, int maximum, String label) {
    String normalized = raw.strip();
    if (normalized.isEmpty()) {
      return Optional.empty();
    }
    maximum(errors, field, normalized, maximum, label);
    return Optional.of(normalized);
  }

  private static void maximum(
      Errors errors, String field, String value, int maximum, String label) {
    if (value.length() > maximum) {
      errors.add(field, label + " must be " + maximum + " characters or fewer.");
    }
  }

  private static Optional<Isbn10> parseIsbn10(String raw, Errors errors) {
    String normalized = raw.strip();
    if (normalized.isEmpty()) {
      return Optional.empty();
    }
    if (raw.length() > EditionMetadataLimits.RAW_ISBN) {
      errors.add("isbn10", "ISBN-10 input must be 64 characters or fewer.");
      return Optional.empty();
    }
    try {
      return Optional.of(Isbn10.parse(normalized));
    } catch (IllegalArgumentException exception) {
      errors.add("isbn10", "Enter a valid ISBN-10.");
      return Optional.empty();
    }
  }

  private static Optional<Isbn13> parseIsbn13(String raw, Errors errors) {
    String normalized = raw.strip();
    if (normalized.isEmpty()) {
      return Optional.empty();
    }
    if (raw.length() > EditionMetadataLimits.RAW_ISBN) {
      errors.add("isbn13", "ISBN-13 input must be 64 characters or fewer.");
      return Optional.empty();
    }
    try {
      return Optional.of(Isbn13.parse(normalized));
    } catch (IllegalArgumentException exception) {
      errors.add("isbn13", "Enter a valid ISBN-13.");
      return Optional.empty();
    }
  }

  private static Optional<PartialPublicationDate> parsePublicationDate(String raw, Errors errors) {
    String value = raw.strip();
    if (value.isEmpty()) {
      return Optional.empty();
    }
    Matcher matcher = PUBLICATION_DATE.matcher(value);
    if (!matcher.matches()) {
      errors.add("publicationDate", "Use YYYY, YYYY-MM, or YYYY-MM-DD.");
      return Optional.empty();
    }
    try {
      int year = Integer.parseInt(matcher.group(1));
      if (matcher.group(2) == null) {
        return Optional.of(PartialPublicationDate.year(year));
      }
      int month = Integer.parseInt(matcher.group(2));
      if (matcher.group(3) == null) {
        return Optional.of(PartialPublicationDate.yearMonth(year, month));
      }
      return Optional.of(
          PartialPublicationDate.full(year, month, Integer.parseInt(matcher.group(3))));
    } catch (DateTimeException exception) {
      errors.add("publicationDate", "Enter a valid publication date.");
      return Optional.empty();
    }
  }

  private static Optional<Integer> parsePageCount(String raw, Errors errors) {
    String value = raw.strip();
    if (value.isEmpty()) {
      return Optional.empty();
    }
    if (!value.chars().allMatch(character -> character >= '0' && character <= '9')) {
      errors.add("pageCount", "Enter a whole-number page count.");
      return Optional.empty();
    }
    try {
      int pageCount = Integer.parseInt(value);
      if (pageCount < EditionMetadataLimits.MIN_PAGE_COUNT
          || pageCount > EditionMetadataLimits.MAX_PAGE_COUNT) {
        errors.add("pageCount", "Page count must be between 1 and 1000000.");
        return Optional.empty();
      }
      return Optional.of(pageCount);
    } catch (NumberFormatException exception) {
      errors.add("pageCount", "Enter a whole-number page count.");
      return Optional.empty();
    }
  }

  private static ReadingState parseReadingState(ManualBookForm form, Errors errors) {
    return switch (form.state()) {
      case "TO_READ" -> new ToRead();
      case "READING" -> {
        LocalDate started = requiredDate(form.startedOn(), "startedOn", "start date", errors);
        yield started == null ? null : new Reading(started);
      }
      case "FINISHED" -> {
        LocalDate started = optionalDate(form.startedOn(), "startedOn", "start date", errors);
        LocalDate finished = requiredDate(form.finishedOn(), "finishedOn", "finish date", errors);
        if (started != null && finished != null && finished.isBefore(started)) {
          errors.add("finishedOn", "Finish date cannot be before start date.");
          yield null;
        }
        yield finished == null ? null : new Finished(Optional.ofNullable(started), finished);
      }
      default -> {
        errors.add("state", "Choose To Read, Reading, or Finished.");
        yield null;
      }
    };
  }

  private static LocalDate requiredDate(String raw, String field, String label, Errors errors) {
    if (raw.isBlank()) {
      errors.add(field, "Enter a " + label + ".");
      return null;
    }
    return optionalDate(raw, field, label, errors);
  }

  private static LocalDate optionalDate(String raw, String field, String label, Errors errors) {
    String value = raw.strip();
    if (value.isEmpty()) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (DateTimeException exception) {
      errors.add(field, "Enter a valid " + label + ".");
      return null;
    }
  }

  private static final class Errors {
    private final Map<String, List<String>> values = new LinkedHashMap<>();

    void add(String field, String message) {
      values.computeIfAbsent(field, ignored -> new ArrayList<>()).add(message);
    }

    boolean has(String field) {
      return values.containsKey(field);
    }

    boolean empty() {
      return values.isEmpty();
    }

    Map<String, List<String>> copy() {
      return values;
    }
  }
}

record ManualBookValidation(ManualBookForm form, Optional<ManualBookDraft> draft) {
  boolean valid() {
    return draft.isPresent();
  }
}

record ManualBookDraft(EditionMetadata metadata, ReadingState readingState) {}

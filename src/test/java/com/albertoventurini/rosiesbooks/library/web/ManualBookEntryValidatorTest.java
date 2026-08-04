package com.albertoventurini.rosiesbooks.library.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.albertoventurini.rosiesbooks.library.internal.Finished;
import com.albertoventurini.rosiesbooks.library.internal.Reading;
import com.albertoventurini.rosiesbooks.library.internal.ToRead;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManualBookEntryValidatorTest {

  private static final ZoneId JOHANNESBURG = ZoneId.of("Africa/Johannesburg");
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-03T22:30:00Z"), ZoneOffset.UTC);
  private final ManualBookEntryValidator validator =
      new ManualBookEntryValidator(CLOCK, JOHANNESBURG);

  @Test
  void normalizesACompleteDraftWithoutLosingAuthorOrderOrPublicationPrecision() {
    ManualBookValidation result =
        validator.validate(
            form()
                .withBibliography(
                    "  A title  ",
                    List.of(" First ", "  ", "Second"),
                    "  A subtitle ",
                    "  Paperback ",
                    " 0-306-40615-2 ",
                    "9780306406157",
                    " Publisher ",
                    "2024-02",
                    " 321 ",
                    " en ",
                    " Description "));

    assertTrue(result.valid());
    var metadata = result.draft().orElseThrow().metadata();
    assertEquals("A title", metadata.title());
    assertEquals(List.of("First", "Second"), metadata.authors());
    assertEquals("2024-02", ManualBookReview.publicationDate(metadata.publicationDate()));
    assertEquals("0306406152", metadata.isbn10().orElseThrow().value());
    assertEquals("9780306406157", metadata.isbn13().orElseThrow().value());
    assertInstanceOf(ToRead.class, result.draft().orElseThrow().readingState());
  }

  @Test
  void defaultsRequiredDatesInTheConfiguredZoneAndPreservesSharedStartDate() {
    ManualBookForm reading = form().withState("READING", "", "1999-01-01");
    ManualBookForm preparedReading = validator.prepare(reading);
    assertEquals("2026-08-04", preparedReading.startedOn());
    assertEquals("", preparedReading.finishedOn());
    assertInstanceOf(
        Reading.class, validator.validate(reading).draft().orElseThrow().readingState());

    ManualBookForm finished = validator.prepare(form().withState("FINISHED", "2020-05-06", ""));
    assertEquals("2020-05-06", finished.startedOn());
    assertEquals("2026-08-04", finished.finishedOn());
    assertInstanceOf(
        Finished.class, validator.validate(finished).draft().orElseThrow().readingState());

    ManualBookForm backToReading =
        validator.prepare(
            finished.withState("READING", finished.startedOn(), finished.finishedOn()));
    assertEquals("2020-05-06", backToReading.startedOn());
    assertEquals("", backToReading.finishedOn());
  }

  @Test
  void aggregatesIndependentFieldFailuresAndRetainsRawInvalidValues() {
    ManualBookForm submitted =
        form()
            .withBibliography(
                " ",
                List.of(" "),
                "s".repeat(501),
                "",
                "invalid",
                "invalid",
                "",
                "2023-02-29",
                "twelve",
                "",
                "")
            .withState("FINISHED", "2026-08-05", "2026-08-04");

    ManualBookValidation result = validator.validate(submitted);

    assertTrue(result.draft().isEmpty());
    for (String field :
        List.of(
            "title",
            "authors",
            "subtitle",
            "isbn10",
            "isbn13",
            "publicationDate",
            "pageCount",
            "finishedOn")) {
      assertTrue(result.form().errors().containsKey(field), field);
    }
    assertEquals("2023-02-29", result.form().publicationDate());
    assertEquals("twelve", result.form().pageCount());
  }

  @Test
  void acceptsEveryPublicationPrecisionAndRejectsAllOtherShapesAndInvalidDates() {
    for (String valid : List.of("2024", "2024-02", "2024-02-29")) {
      ManualBookValidation result =
          validator.validate(
              form()
                  .withBibliography(
                      "Title", List.of("Author"), "", "", "", "", "", valid, "", "", ""));
      assertTrue(result.valid(), valid);
      assertEquals(
          valid,
          ManualBookReview.publicationDate(
              result.draft().orElseThrow().metadata().publicationDate()));
    }
    for (String invalid :
        List.of("24", "2024-2", "2024-02-3", "2024-02-30", "2024/02/03", "+2024")) {
      assertFieldError(
          validator.validate(
              form()
                  .withBibliography(
                      "Title", List.of("Author"), "", "", "", "", "", invalid, "", "", "")),
          "publicationDate");
    }
  }

  @Test
  void enforcesAuthorTextIsbnAndPageCountBoundariesIndependently() {
    assertFieldError(validator.validate(form().withAuthors(List.of("a".repeat(301)))), "authors");
    assertFieldError(
        validator.validate(form().withAuthors(java.util.Collections.nCopies(21, "Author"))),
        "authors");

    ManualBookValidation ordered =
        validator.validate(form().withAuthors(List.of("Third", "", "First", "Second")));
    assertEquals(
        List.of("Third", "First", "Second"), ordered.draft().orElseThrow().metadata().authors());

    assertFieldError(
        validator.validate(
            form()
                .withBibliography(
                    "Title", List.of("Author"), "", "", "0".repeat(65), "", "", "", "", "", "")),
        "isbn10");
    ManualBookValidation conflict =
        validator.validate(
            form()
                .withBibliography(
                    "Title",
                    List.of("Author"),
                    "",
                    "",
                    "0306406152",
                    "9780804429573",
                    "",
                    "",
                    "",
                    "",
                    ""));
    assertFieldError(conflict, "isbn10");
    assertFieldError(conflict, "isbn13");

    for (String invalid : List.of("+1", "1.5", "0", "1000001", "999999999999999999")) {
      assertFieldError(
          validator.validate(
              form()
                  .withBibliography(
                      "Title", List.of("Author"), "", "", "", "", "", "", invalid, "", "")),
          "pageCount");
    }
    assertEquals(
        Optional.of(1_000_000),
        validator
            .validate(
                form()
                    .withBibliography(
                        "Title", List.of("Author"), "", "", "", "", "", "", "1000000", "", ""))
            .draft()
            .orElseThrow()
            .metadata()
            .pageCount());
  }

  @Test
  void validatesEveryStateShapeAndSupportsFinishedWithUnknownStart() {
    assertInstanceOf(
        Finished.class,
        validator
            .validate(form().withState("FINISHED", "", "2026-08-04"))
            .draft()
            .orElseThrow()
            .readingState());
    assertFieldError(validator.validate(form().withState("UNKNOWN", "", "")), "state");
    assertFieldError(
        validator.validate(form().withState("READING", "2026-02-30", "")), "startedOn");
    assertFieldError(
        validator.validate(form().withState("FINISHED", "not-a-date", "2026-08-04")), "startedOn");
    assertFieldError(
        validator.validate(form().withState("FINISHED", "", "not-a-date")), "finishedOn");
  }

  @Test
  void treatsEveryBlankOptionalAsAbsentAndEnforcesAllTextLimits() {
    ManualBookValidation blank =
        validator.validate(
            form()
                .withBibliography(
                    "Title", List.of("Author"), " \t ", " ", " ", " ", " ", " ", " ", " ", " \n "));
    var metadata = blank.draft().orElseThrow().metadata();
    assertEquals(Optional.empty(), metadata.subtitle());
    assertEquals(Optional.empty(), metadata.format());
    assertEquals(Optional.empty(), metadata.publisher());
    assertEquals(Optional.empty(), metadata.language());
    assertEquals(Optional.empty(), metadata.description());

    Map<String, ManualBookForm> overlong =
        Map.of(
            "title", form().withTitle("t".repeat(501)),
            "subtitle", form().withSubtitle("s".repeat(501)),
            "format", form().withFormat("f".repeat(501)),
            "publisher", form().withPublisher("p".repeat(501)),
            "language", form().withLanguage("l".repeat(501)),
            "description", form().withDescription("d".repeat(10_001)));
    overlong.forEach((field, submitted) -> assertFieldError(validator.validate(submitted), field));
  }

  private static void assertFieldError(ManualBookValidation result, String field) {
    assertTrue(result.form().errors().containsKey(field), field);
    assertTrue(result.draft().isEmpty(), field);
  }

  private static ManualBookForm form() {
    return new ManualBookForm(
        "Title",
        List.of("Author"),
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "TO_READ",
        "",
        "",
        Map.of());
  }
}

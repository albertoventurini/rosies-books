package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.library.internal.EditionMetadata;
import com.albertoventurini.rosiesbooks.library.internal.Finished;
import com.albertoventurini.rosiesbooks.library.internal.PartialPublicationDate;
import com.albertoventurini.rosiesbooks.library.internal.Reading;
import java.util.Optional;

record ManualBookReview(
    String title,
    String subtitle,
    java.util.List<String> authors,
    String format,
    String isbn10,
    String isbn13,
    String publisher,
    String publicationDate,
    String pageCount,
    String language,
    String description,
    String state,
    String startedOn,
    String finishedOn) {

  static ManualBookReview from(ManualBookDraft draft) {
    EditionMetadata metadata = draft.metadata();
    String state;
    String startedOn = "";
    String finishedOn = "";
    if (draft.readingState() instanceof Reading reading) {
      state = "Reading";
      startedOn = reading.startedOn().toString();
    } else if (draft.readingState() instanceof Finished finished) {
      state = "Finished";
      startedOn = finished.startedOn().map(Object::toString).orElse("");
      finishedOn = finished.finishedOn().toString();
    } else {
      state = "To Read";
    }
    return new ManualBookReview(
        metadata.title(),
        metadata.subtitle().orElse(""),
        metadata.authors(),
        metadata.format().orElse(""),
        metadata.isbn10().map(value -> value.value()).orElse(""),
        metadata.isbn13().map(value -> value.value()).orElse(""),
        metadata.publisher().orElse(""),
        publicationDate(metadata.publicationDate()),
        metadata.pageCount().map(Object::toString).orElse(""),
        metadata.language().orElse(""),
        metadata.description().orElse(""),
        state,
        startedOn,
        finishedOn);
  }

  static String publicationDate(Optional<PartialPublicationDate> date) {
    if (date.isEmpty()) {
      return "";
    }
    PartialPublicationDate value = date.orElseThrow();
    if (value.day() != null) {
      return "%04d-%02d-%02d".formatted(value.year(), value.month(), value.day());
    }
    if (value.month() != null) {
      return "%04d-%02d".formatted(value.year(), value.month());
    }
    return "%04d".formatted(value.year());
  }
}

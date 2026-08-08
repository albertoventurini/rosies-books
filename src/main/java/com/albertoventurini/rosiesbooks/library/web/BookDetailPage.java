package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.library.api.BookDetailCatalog.BookDetail;
import com.albertoventurini.rosiesbooks.library.internal.Finished;
import com.albertoventurini.rosiesbooks.library.internal.PartialPublicationDate;
import com.albertoventurini.rosiesbooks.library.internal.Reading;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

record BookDetailPage(
    String title,
    String subtitle,
    List<String> authors,
    String stateLabel,
    String startedOn,
    String finishedOn,
    String notes,
    String description,
    String format,
    String publisher,
    String publicationDate,
    String pageCount,
    String language,
    String isbn10,
    String isbn13,
    String shelfUrl,
    String notice,
    String stateUrl,
    String editUrl,
    String deleteUrl,
    String coverUrl,
    BookPlaceholder placeholder) {

  static BookDetailPage from(UserEditionId id, BookDetail book) {
    return from(id, book, null);
  }

  static BookDetailPage from(UserEditionId id, BookDetail book, String noticeCode) {
    var metadata = book.metadata();
    String startedOn = book.state() instanceof Reading reading ? date(reading.startedOn()) : null;
    if (book.state() instanceof Finished finished) {
      startedOn = finished.startedOn().map(BookDetailPage::date).orElse(null);
    }
    String finishedOn =
        book.state() instanceof Finished finished ? date(finished.finishedOn()) : null;
    String route = "/books/" + id.value();
    return new BookDetailPage(
        metadata.title(),
        metadata.subtitle().orElse(null),
        metadata.authors(),
        ShelfDatePresentation.stateLabel(book.state()),
        startedOn,
        finishedOn,
        book.privateNotes(),
        metadata.description().orElse(null),
        metadata.format().orElse(null),
        metadata.publisher().orElse(null),
        metadata.publicationDate().map(BookDetailPage::publicationDate).orElse(null),
        metadata.pageCount().map(value -> value + " pages").orElse(null),
        metadata.language().orElse(null),
        metadata.isbn10().map(value -> value.value()).orElse(null),
        metadata.isbn13().map(value -> value.value()).orElse(null),
        book.shelf().route(),
        "details-updated".equals(noticeCode) ? "Details updated." : null,
        route + "/state",
        route + "/edit",
        route + "/delete",
        book.hasCover() ? route + "/cover" : null,
        BookPlaceholder.from(metadata.title(), metadata.authors()));
  }

  public boolean hasSubtitle() {
    return subtitle != null;
  }

  public boolean hasStartedOn() {
    return startedOn != null;
  }

  public boolean hasFinishedOn() {
    return finishedOn != null;
  }

  public boolean hasNotes() {
    return notes != null && !notes.isBlank();
  }

  public boolean hasDescription() {
    return description != null;
  }

  public boolean hasFormat() {
    return format != null;
  }

  public boolean hasPublisher() {
    return publisher != null;
  }

  public boolean hasPublicationDate() {
    return publicationDate != null;
  }

  public boolean hasPageCount() {
    return pageCount != null;
  }

  public boolean hasLanguage() {
    return language != null;
  }

  public boolean hasIsbn10() {
    return isbn10 != null;
  }

  public boolean hasIsbn13() {
    return isbn13 != null;
  }

  public boolean hasCover() {
    return coverUrl != null;
  }

  public boolean hasNotice() { return notice != null; }

  private static String date(LocalDate value) {
    return value.format(DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH));
  }

  private static String publicationDate(PartialPublicationDate value) {
    if (value.day() != null) return date(LocalDate.of(value.year(), value.month(), value.day()));
    if (value.month() != null)
      return java.time.Month.of(value.month())
              .getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
          + " "
          + value.year();
    return value.year().toString();
  }
}

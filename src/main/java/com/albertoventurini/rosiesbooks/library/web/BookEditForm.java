package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.library.internal.EditionMetadata;
import com.albertoventurini.rosiesbooks.library.internal.MetadataOverrides;
import com.albertoventurini.rosiesbooks.library.internal.PartialPublicationDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

record BookEditForm(ManualBookForm bibliography, String notes, MetadataOverrides overrides) {
  BookEditForm {
    notes = notes == null ? "" : notes;
  }

  static BookEditForm from(EditionMetadata metadata, String notes, MetadataOverrides overrides) {
    return new BookEditForm(
        new ManualBookForm("", metadata.title(), metadata.authors(), metadata.subtitle().orElse(""),
            metadata.format().orElse(""), metadata.isbn10().map(value -> value.value()).orElse(""),
            metadata.isbn13().map(value -> value.value()).orElse(""), metadata.publisher().orElse(""),
            publicationDate(metadata.publicationDate().orElse(null)), metadata.pageCount().map(Object::toString).orElse(""),
            metadata.language().orElse(""), metadata.description().orElse(""), "TO_READ", "", "", Map.of()),
        notes, overrides);
  }

  BookEditForm submitted(
      String title, List<String> authors, String subtitle, String format, String isbn10, String isbn13,
      String publisher, String publicationDate, String pageCount, String language, String description,
      String notes) {
    return new BookEditForm(new ManualBookForm("", title, authors, subtitle, format, isbn10, isbn13,
        publisher, publicationDate, pageCount, language, description, "TO_READ", "", "", Map.of()), notes, overrides);
  }

  BookEditForm withBibliography(ManualBookForm bibliography) { return new BookEditForm(bibliography, notes, overrides); }
  BookEditForm withErrors(Map<String, List<String>> errors) { return withBibliography(bibliography.withErrors(errors)); }
  BookEditForm addAuthor() { return withBibliography(bibliography.addAuthor()); }
  BookEditForm removeAuthor(int index) { return withBibliography(bibliography.removeAuthor(index)); }
  public boolean hasError(String field) { return bibliography.hasError(field); }
  public List<String> errorsFor(String field) { return bibliography.errorsFor(field); }
  public Map<String, List<String>> errors() { return bibliography.errors(); }
  public boolean canResetTitle() { return overrides.title().isOverridden(); }
  public boolean canResetSubtitle() { return overrides.subtitle().isOverridden(); }
  public boolean canResetAuthors() { return overrides.authors().isOverridden(); }
  public boolean canResetFormat() { return overrides.format().isOverridden(); }
  public boolean canResetIsbn10() { return overrides.isbn10().isOverridden(); }
  public boolean canResetIsbn13() { return overrides.isbn13().isOverridden(); }
  public boolean canResetPublisher() { return overrides.publisher().isOverridden(); }
  public boolean canResetPublicationDate() { return overrides.publicationDate().isOverridden(); }
  public boolean canResetPageCount() { return overrides.pageCount().isOverridden(); }
  public boolean canResetLanguage() { return overrides.language().isOverridden(); }
  public boolean canResetDescription() { return overrides.description().isOverridden(); }

  private static String publicationDate(PartialPublicationDate date) {
    if (date == null || date.year() == null) return "";
    if (date.month() == null) return String.format("%04d", date.year());
    if (date.day() == null) return String.format("%04d-%02d", date.year(), date.month());
    return String.format("%04d-%02d-%02d", date.year(), date.month(), date.day());
  }
}

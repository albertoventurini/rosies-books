package com.albertoventurini.rosiesbooks.library.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record ManualBookForm(
    String title,
    List<String> authors,
    String subtitle,
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
    String finishedOn,
    Map<String, List<String>> errors) {

  ManualBookForm {
    title = value(title);
    authors = authors == null || authors.isEmpty() ? List.of("") : List.copyOf(authors);
    subtitle = value(subtitle);
    format = value(format);
    isbn10 = value(isbn10);
    isbn13 = value(isbn13);
    publisher = value(publisher);
    publicationDate = value(publicationDate);
    pageCount = value(pageCount);
    language = value(language);
    description = value(description);
    state = value(state);
    startedOn = value(startedOn);
    finishedOn = value(finishedOn);
    Map<String, List<String>> copied = new LinkedHashMap<>();
    if (errors != null) {
      errors.forEach((field, messages) -> copied.put(field, List.copyOf(messages)));
    }
    errors = Map.copyOf(copied);
  }

  static ManualBookForm empty() {
    return new ManualBookForm(
        "", List.of(""), "", "", "", "", "", "", "", "", "", "TO_READ", "", "", Map.of());
  }

  ManualBookForm withBibliography(
      String title,
      List<String> authors,
      String subtitle,
      String format,
      String isbn10,
      String isbn13,
      String publisher,
      String publicationDate,
      String pageCount,
      String language,
      String description) {
    return new ManualBookForm(
        title,
        authors,
        subtitle,
        format,
        isbn10,
        isbn13,
        publisher,
        publicationDate,
        pageCount,
        language,
        description,
        state,
        startedOn,
        finishedOn,
        errors);
  }

  ManualBookForm withState(String state, String startedOn, String finishedOn) {
    return new ManualBookForm(
        title,
        authors,
        subtitle,
        format,
        isbn10,
        isbn13,
        publisher,
        publicationDate,
        pageCount,
        language,
        description,
        state,
        startedOn,
        finishedOn,
        errors);
  }

  ManualBookForm withAuthors(List<String> authors) {
    return withBibliography(
        title,
        authors,
        subtitle,
        format,
        isbn10,
        isbn13,
        publisher,
        publicationDate,
        pageCount,
        language,
        description);
  }

  ManualBookForm withTitle(String title) {
    return withBibliography(
        title,
        authors,
        subtitle,
        format,
        isbn10,
        isbn13,
        publisher,
        publicationDate,
        pageCount,
        language,
        description);
  }

  ManualBookForm withSubtitle(String subtitle) {
    return withBibliography(
        title,
        authors,
        subtitle,
        format,
        isbn10,
        isbn13,
        publisher,
        publicationDate,
        pageCount,
        language,
        description);
  }

  ManualBookForm withFormat(String format) {
    return withBibliography(
        title,
        authors,
        subtitle,
        format,
        isbn10,
        isbn13,
        publisher,
        publicationDate,
        pageCount,
        language,
        description);
  }

  ManualBookForm withPublisher(String publisher) {
    return withBibliography(
        title,
        authors,
        subtitle,
        format,
        isbn10,
        isbn13,
        publisher,
        publicationDate,
        pageCount,
        language,
        description);
  }

  ManualBookForm withLanguage(String language) {
    return withBibliography(
        title,
        authors,
        subtitle,
        format,
        isbn10,
        isbn13,
        publisher,
        publicationDate,
        pageCount,
        language,
        description);
  }

  ManualBookForm withDescription(String description) {
    return withBibliography(
        title,
        authors,
        subtitle,
        format,
        isbn10,
        isbn13,
        publisher,
        publicationDate,
        pageCount,
        language,
        description);
  }

  ManualBookForm withErrors(Map<String, List<String>> errors) {
    return new ManualBookForm(
        title,
        authors,
        subtitle,
        format,
        isbn10,
        isbn13,
        publisher,
        publicationDate,
        pageCount,
        language,
        description,
        state,
        startedOn,
        finishedOn,
        errors);
  }

  ManualBookForm addAuthor() {
    List<String> changed = new ArrayList<>(authors);
    if (changed.size() < 20) {
      changed.add("");
    }
    return withAuthors(changed);
  }

  ManualBookForm removeAuthor(int index) {
    if (index < 0 || index >= authors.size()) {
      return this;
    }
    List<String> changed = new ArrayList<>(authors);
    changed.remove(index);
    if (changed.isEmpty()) {
      changed.add("");
    }
    return withAuthors(changed);
  }

  public boolean toRead() {
    return state.equals("TO_READ");
  }

  public boolean reading() {
    return state.equals("READING");
  }

  public boolean finished() {
    return state.equals("FINISHED");
  }

  public boolean hasError(String field) {
    return errors.containsKey(field);
  }

  public List<String> errorsFor(String field) {
    return errors.getOrDefault(field, List.of());
  }

  private static String value(String value) {
    return value == null ? "" : value;
  }
}

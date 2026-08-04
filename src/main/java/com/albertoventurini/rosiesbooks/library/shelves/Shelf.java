package com.albertoventurini.rosiesbooks.library.shelves;

/** A user-library shelf and its stable browser and persistence identities. */
public enum Shelf {
  READING("reading", "Reading", "READING", "No books are currently being read."),
  TO_READ("to-read", "To Read", "TO_READ", "There are no books waiting to be read."),
  FINISHED("finished", "Finished", "FINISHED", "No books have been finished yet.");

  private final String slug;
  private final String heading;
  private final String persistedState;
  private final String emptyMessage;

  Shelf(String slug, String heading, String persistedState, String emptyMessage) {
    this.slug = slug;
    this.heading = heading;
    this.persistedState = persistedState;
    this.emptyMessage = emptyMessage;
  }

  public String slug() {
    return slug;
  }

  public String route() {
    return "/" + slug;
  }

  public String heading() {
    return heading;
  }

  public String persistedState() {
    return persistedState;
  }

  public String emptyMessage() {
    return emptyMessage;
  }
}

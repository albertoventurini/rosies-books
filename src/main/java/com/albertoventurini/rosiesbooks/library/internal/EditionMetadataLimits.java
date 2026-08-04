package com.albertoventurini.rosiesbooks.library.internal;

/** Shared application limits for provider-independent edition metadata. */
public final class EditionMetadataLimits {

  public static final int TITLE = 500;
  public static final int AUTHOR = 300;
  public static final int MIN_AUTHORS = 1;
  public static final int MAX_AUTHORS = 20;
  public static final int SHORT_TEXT = 500;
  public static final int DESCRIPTION = 10_000;
  public static final int RAW_ISBN = 64;
  public static final int MIN_PAGE_COUNT = 1;
  public static final int MAX_PAGE_COUNT = 1_000_000;

  private EditionMetadataLimits() {}
}

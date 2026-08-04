package com.albertoventurini.rosiesbooks.library.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EditionMetadataValidationTest {

  @Test
  void requiresNonblankTitleAndBetweenOneAndTwentyNonblankAuthors() {
    assertThrows(IllegalArgumentException.class, () -> metadata(" ", List.of("Author")));
    assertThrows(IllegalArgumentException.class, () -> metadata("Title", List.of()));
    assertThrows(IllegalArgumentException.class, () -> metadata("Title", List.of(" ")));
    assertThrows(
        IllegalArgumentException.class,
        () -> metadata("Title", java.util.Collections.nCopies(21, "Author")));
    assertDoesNotThrow(() -> metadata("Title", java.util.Collections.nCopies(20, "Author")));
  }

  @Test
  void enforcesEverySupportedTextAndPageCountLimit() {
    assertThrows(
        IllegalArgumentException.class, () -> metadata("t".repeat(501), List.of("Author")));
    assertThrows(IllegalArgumentException.class, () -> metadata("Title", List.of("a".repeat(301))));
    assertThrows(
        IllegalArgumentException.class,
        () -> metadata("Title", List.of("Author"), Optional.of("s".repeat(501)), Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () -> metadata("Title", List.of("Author"), Optional.empty(), Optional.of(0)));
    assertThrows(
        IllegalArgumentException.class,
        () -> metadata("Title", List.of("Author"), Optional.empty(), Optional.of(1_000_001)));
    assertDoesNotThrow(
        () ->
            metadata(
                "t".repeat(500),
                List.of("a".repeat(300)),
                Optional.empty(),
                Optional.of(1_000_000)));
  }

  private static EditionMetadata metadata(String title, List<String> authors) {
    return metadata(title, authors, Optional.empty(), Optional.empty());
  }

  private static EditionMetadata metadata(
      String title, List<String> authors, Optional<String> subtitle, Optional<Integer> pageCount) {
    return new EditionMetadata(
        title,
        subtitle,
        authors,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        pageCount,
        Optional.empty(),
        Optional.empty());
  }
}

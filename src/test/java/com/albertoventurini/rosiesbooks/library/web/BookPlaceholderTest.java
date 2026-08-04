package com.albertoventurini.rosiesbooks.library.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BookPlaceholderTest {

  @Test
  void isDeterministicAndUsesOnlyFixedThemeClasses() {
    BookPlaceholder first =
        BookPlaceholder.from("The Left Hand of Darkness", List.of("Ursula K. Le Guin"));
    BookPlaceholder again =
        BookPlaceholder.from("The Left Hand of Darkness", List.of("Ursula K. Le Guin"));

    assertEquals(first, again);
    assertEquals("placeholder-theme-5", first.themeClass());
    assertTrue(BookPlaceholder.themeClasses().contains(first.themeClass()));
    assertTrue(first.themeClass().matches("placeholder-theme-[1-6]"));
  }

  @Test
  void authorSequenceParticipatesInTheStableHash() {
    BookPlaceholder first = BookPlaceholder.from("A title", List.of("First", "Second"));
    BookPlaceholder reversed = BookPlaceholder.from("A title", List.of("Second", "First"));

    assertNotEquals(first.themeClass(), reversed.themeClass());
    assertEquals("First, Second", first.authorsText());
    assertEquals("Second, First", reversed.authorsText());
  }

  @Test
  void preservesLongUnicodeTextForTypographicOverflowHandling() {
    String title = "長い本の題名 📚 ".repeat(30);
    String author = "Écrivain très prolifique ".repeat(20);

    BookPlaceholder placeholder = BookPlaceholder.from(title, List.of(author));

    assertEquals(title, placeholder.title());
    assertEquals(author, placeholder.authorsText());
  }

  @Test
  void rejectsMissingRequiredEffectiveMetadataWithoutDependingOnOptionalCoverData() {
    assertThrows(NullPointerException.class, () -> BookPlaceholder.from(null, List.of("Author")));
    assertThrows(IllegalArgumentException.class, () -> BookPlaceholder.from("Title", List.of()));
    assertThrows(IllegalArgumentException.class, () -> BookPlaceholder.from("Title", List.of(" ")));
  }
}

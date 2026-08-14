package com.albertoventurini.rosiesbooks.library.shelves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShelfSearchTest {

  @Test
  void classifiesTrimmedTextAndSeparatorNormalizedIsbnQueries() {
    assertEquals("Alb", ShelfSearch.parse("  Alb  ").orElseThrow().input());
    ShelfSearch.Isbn isbn =
        assertInstanceOf(ShelfSearch.Isbn.class, ShelfSearch.parse("978-030").orElseThrow());
    assertEquals("978030", isbn.digits());
    assertTrue(ShelfSearch.parse("ab").isEmpty());
    assertTrue(ShelfSearch.parse("97803").isEmpty());
    assertInstanceOf(ShelfSearch.Text.class, ShelfSearch.parse("ab1c").orElseThrow());
  }
}

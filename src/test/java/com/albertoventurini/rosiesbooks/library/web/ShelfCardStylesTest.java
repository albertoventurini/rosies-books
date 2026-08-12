package com.albertoventurini.rosiesbooks.library.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShelfCardStylesTest {

  @Test
  void distinguishesTheShelfContextLineFromTheAuthorLine() throws IOException {
    String styles =
        Files.readString(Path.of("src/main/resources/META-INF/resources/assets/app.css"));

    assertTrue(
        styles.contains(
            ".shelf-book-metadata .shelf-book-context {\n  color: var(--color-current);"));
    assertTrue(styles.contains("font-weight: 600;"));
  }
}

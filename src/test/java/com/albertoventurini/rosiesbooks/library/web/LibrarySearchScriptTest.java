package com.albertoventurini.rosiesbooks.library.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LibrarySearchScriptTest {

  @Test
  void enablesTextSearchAfterThreeLettersAndIsbnSearchAfterSixNormalizedDigits()
      throws IOException {
    String script =
        Files.readString(Path.of("src/main/resources/META-INF/resources/assets/library-search.js"));

    assertTrue(script.contains("/^[0-9]{6,}$/"));
    assertTrue(script.contains("replace(/[ -]/g, \"\")"));
    assertTrue(script.contains("/\\p{L}/u"));
    assertTrue(script.contains("submit.disabled = !isSearchable(input.value)"));
  }
}

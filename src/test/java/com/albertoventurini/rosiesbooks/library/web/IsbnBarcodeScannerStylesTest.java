package com.albertoventurini.rosiesbooks.library.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IsbnBarcodeScannerStylesTest {

  @Test
  void makesTheScannerCoverTheDynamicMobileViewport() throws IOException {
    String styles =
        Files.readString(Path.of("src/main/resources/META-INF/resources/assets/app.css"));

    assertTrue(styles.contains("width: 100dvw;"));
    assertTrue(styles.contains("height: 100dvh;"));
    assertTrue(styles.contains("max-width: none;"));
  }
}

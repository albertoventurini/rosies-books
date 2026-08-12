package com.albertoventurini.rosiesbooks.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PwaIconAssetTest {

  private static final Path ICONS = Path.of("src/main/resources/META-INF/resources/assets/icons");
  private static final Path MANIFEST =
      Path.of("src/main/resources/META-INF/resources/assets/manifest.webmanifest");

  @Test
  void providesVisibleMaskableIconsForAndroidLaunchers() throws IOException {
    String manifest = Files.readString(MANIFEST);

    assertTrue(manifest.contains("\"purpose\": \"any maskable\""));
    assertTrue(manifest.contains("/assets/icons/rosies-books-rounded-192.png"));
    assertTrue(manifest.contains("/assets/icons/rosies-books-rounded-512.png"));
    assertBrandedAtCenter("rosies-books-rounded-192.png", 192);
    assertBrandedAtCenter("rosies-books-rounded-512.png", 512);
  }

  private void assertBrandedAtCenter(String fileName, int expectedSize) throws IOException {
    BufferedImage icon = ImageIO.read(ICONS.resolve(fileName).toFile());

    assertEquals(expectedSize, icon.getWidth());
    assertEquals(expectedSize, icon.getHeight());
    assertNotEquals(0, icon.getRGB(expectedSize / 2, expectedSize / 2) >>> 24);
    assertEquals(0xfff0e8d6, icon.getRGB(expectedSize / 2, expectedSize / 2));
  }
}

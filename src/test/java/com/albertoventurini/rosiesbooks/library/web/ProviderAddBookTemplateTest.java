package com.albertoventurini.rosiesbooks.library.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.albertoventurini.rosiesbooks.provider.api.SelectedEdition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProviderAddBookTemplateTest {

  @Test
  void splitsNormalizedDescriptionLinesIntoParagraphs() {
    SelectedEdition edition =
        new SelectedEdition(
            "googlebooks",
            "google-volume",
            "Title",
            Optional.empty(),
            List.of("Author"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of("First paragraph.\n\nSecond paragraph.\nThird paragraph."),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    assertEquals(
        List.of("First paragraph.", "Second paragraph.", "Third paragraph."),
        new ProviderAddBookPage.Result(edition, "token", null, null).descriptionParagraphs());
  }

  @Test
  void keepsTheFoundEditionAndShelfSelectionOnOnePage() throws IOException {
    String template =
        Files.readString(Path.of("src/main/resources/templates/library/web/add.html"));

    assertTrue(template.contains("action=\"/books/new/add\""));
    assertTrue(template.contains("aria-label=\"Library shelves\""));
    assertTrue(template.contains("{#for item in page.navigation}"));
    assertTrue(template.contains("href=\"{item.route}\""));
    assertTrue(template.contains(">{item.label}</a>"));
    assertTrue(template.contains("name=\"state\""));
    assertTrue(template.contains(">Add book</button>"));
    assertTrue(template.contains("class=\"isbn-result-cover\""));
    assertTrue(template.contains("page.result.get.edition.cover"));
    assertTrue(template.contains("class=\"isbn-result-facts\""));
    assertTrue(template.contains("class=\"form-section isbn-result\""));
    assertTrue(template.contains("{#for descriptionParagraph in page.result.get.descriptionParagraphs}"));
    assertTrue(template.contains("<p>{descriptionParagraph}</p>"));
    assertTrue(template.contains("data-shelf-target"));
    assertTrue(template.contains("data-shelf-date-fields=\"READING\""));
    assertTrue(template.contains("data-shelf-date-fields=\"FINISHED\""));
    assertTrue(template.contains("/assets/state-change.js"));
    assertTrue(template.contains("id=\"scan-barcode\""));
    assertTrue(template.contains("aria-haspopup=\"dialog\""));
    assertTrue(template.contains("id=\"barcode-scanner\""));
    assertTrue(template.contains("role=\"dialog\""));
    assertTrue(template.contains("aria-modal=\"true\""));
    assertTrue(template.contains("id=\"barcode-scanner-status\""));
    assertTrue(template.contains("id=\"barcode-scanner-close\""));
    assertTrue(template.contains("id=\"barcode-scanner-switch-camera\""));
    assertFalse(template.contains("id=\"barcode-scanner-cancel\""));
    assertTrue(template.contains("/assets/isbn-barcode-scanner.js"));
    assertTrue(template.contains("action=\"/books/new/lookup\""));
    assertTrue(
        Files.exists(
            Path.of("src/main/resources/META-INF/resources/assets/isbn-barcode-scanner.js")));
    assertTrue(
        Files.exists(Path.of("src/main/resources/META-INF/resources/assets/quagga-1.12.1.min.js")));
    assertFalse(template.contains(">Shelf and dates<"));
    assertFalse(template.contains(">Update date fields<"));
    assertFalse(template.contains(">Provider result<"));
    assertFalse(template.contains("Review this edition"));

    assertEquals(
        java.util.List.of("/reading", "/to-read", "/finished"),
        ProviderAddBookPage.empty("Reader").navigation().stream()
            .map(ShelfNavigationItem::route)
            .toList());
  }
}

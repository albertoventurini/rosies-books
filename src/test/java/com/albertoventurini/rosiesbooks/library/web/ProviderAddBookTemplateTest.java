package com.albertoventurini.rosiesbooks.library.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProviderAddBookTemplateTest {

  @Test
  void keepsTheFoundEditionAndShelfSelectionOnOnePage() throws IOException {
    String template = Files.readString(Path.of("src/main/resources/templates/library/web/add.html"));

    assertTrue(template.contains("action=\"/books/new/add\""));
    assertTrue(template.contains("name=\"state\""));
    assertTrue(template.contains(">Add book</button>"));
    assertTrue(template.contains("class=\"isbn-result-cover\""));
    assertTrue(template.contains("page.result.get.edition.cover"));
    assertTrue(template.contains("class=\"isbn-result-facts\""));
    assertTrue(template.contains("class=\"form-section isbn-result\""));
    assertFalse(template.contains(">Provider result<"));
    assertFalse(template.contains("Review this edition"));
  }
}

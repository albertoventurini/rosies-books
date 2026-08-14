package com.albertoventurini.rosiesbooks.library.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GoodreadsCsvParserTest {
  private final GoodreadsCsvParser parser = new GoodreadsCsvParser();

  @Test
  void acceptsReorderedHeadersQuotedContentAndExcelWrappedIsbn() {
    var result =
        parser.parse(
            "Exclusive Shelf,Author,Title,ISBN,Additional Authors,Private Notes,Date Added\n"
                + "read,First Author,\"A title, with comma\",=\"0-306-40615-2\",First Author,\"a"
                + " note\n"
                + "next line\",4/2/2024\n");

    assertTrue(result.valid());
    var row = result.rows().getFirst();
    assertEquals("9780306406157", row.isbn13().orElseThrow().value());
    assertEquals(1, row.authors().size());
    assertEquals("a note\nnext line", row.notes().orElseThrow());
  }

  @Test
  void readsTheDateFormatUsedByGoodreadsExports() {
    var result =
        parser.parse(
            "Title,Author,Exclusive Shelf,Date Added,Date Read\n"
                + "Waiting,Author,to-read,2021/05/08,\n"
                + "Done,Author,read,2021/05/08,\n");

    assertTrue(result.valid());
    assertEquals(LocalDate.of(2021, 5, 8), result.rows().get(0).addedOn());
    assertEquals(LocalDate.of(2021, 5, 8), result.rows().get(1).addedOn());
    assertNull(result.rows().get(1).readOn());
  }

  @Test
  void reportsEveryInvalidRequiredRowAndDuplicateIsbn() {
    var result =
        parser.parse(
            "Title,Author,Exclusive Shelf,ISBN13\n"
                + ",,to-read,9780306406157\n"
                + "Another,Author,to-read,9780306406157\n");

    assertFalse(result.valid());
    assertEquals(3, result.errors().size());
  }
}

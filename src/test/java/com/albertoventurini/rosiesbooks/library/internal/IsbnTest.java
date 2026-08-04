package com.albertoventurini.rosiesbooks.library.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class IsbnTest {

  @Test
  void normalizesAndConvertsPublishedIsbn10Examples() {
    Isbn10 isbn = Isbn10.parse(" 0-306-40615-2 ");
    assertEquals("0306406152", isbn.value());
    assertEquals("9780306406157", isbn.toIsbn13().value());

    Isbn10 terminalX = Isbn10.parse("0 8044 2957 x");
    assertEquals("080442957X", terminalX.value());
    assertEquals("9780804429573", terminalX.toIsbn13().value());
    assertEquals(terminalX, Isbn10.parse(terminalX.value()));
  }

  @Test
  void acceptsNormalized978And979Isbn13Values() {
    assertEquals("9780306406157", Isbn13.parse("978-0-306-40615-7").value());
    assertEquals("9791090636071", Isbn13.parse("979 10 90636 07 1").value());
  }

  @Test
  void derivesTheCanonicalIsbn13AndRejectsConflictingPairs() {
    CanonicalIsbns derived =
        new CanonicalIsbns(Optional.of(Isbn10.parse("0-306-40615-2")), Optional.empty());
    assertEquals(Optional.of(Isbn10.parse("0306406152")), derived.isbn10());
    assertEquals(Optional.of(Isbn13.parse("9780306406157")), derived.isbn13());

    assertEquals(
        new CanonicalIsbns(
            Optional.of(Isbn10.parse("0306406152")), Optional.of(Isbn13.parse("9780306406157"))),
        derived);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CanonicalIsbns(
                Optional.of(Isbn10.parse("0306406152")),
                Optional.of(Isbn13.parse("9780804429573"))));
  }

  @Test
  void rejectsInvalidLengthsCharactersChecksumsAndPrefixes() {
    for (String invalid :
        new String[] {
          "030640615",
          "03064061522",
          "0306406153",
          "X306406152",
          "03064X6152",
          "03064061.2",
          "03064061_2"
        }) {
      assertThrows(IllegalArgumentException.class, () -> Isbn10.parse(invalid), invalid);
    }
    for (String invalid :
        new String[] {
          "978030640615",
          "97803064061570",
          "9780306406158",
          "9770306406157",
          "97803064061X7",
          "97803064061/7"
        }) {
      assertThrows(IllegalArgumentException.class, () -> Isbn13.parse(invalid), invalid);
    }
  }

  @Test
  void generatedValidIsbn10ValuesRemainValidAcrossNormalizationAndConversion() {
    for (int seed = 0; seed < 250; seed++) {
      String body = String.format("%09d", seed * 37_919L);
      Isbn10 normalized = Isbn10.parse(body + isbn10CheckDigit(body));
      String separated =
          body.substring(0, 1)
              + "-"
              + body.substring(1, 4)
              + " "
              + body.substring(4, 9)
              + normalized.value().charAt(9);

      assertEquals(normalized, Isbn10.parse(separated));
      assertEquals(normalized, Isbn10.parse(normalized.value()));
      assertEquals(normalized.toIsbn13(), Isbn13.parse(normalized.toIsbn13().value()));
    }
  }

  private static char isbn10CheckDigit(String body) {
    int weighted = 0;
    for (int index = 0; index < body.length(); index++) {
      weighted += (10 - index) * (body.charAt(index) - '0');
    }
    int check = (11 - weighted % 11) % 11;
    return check == 10 ? 'X' : (char) ('0' + check);
  }
}

package com.albertoventurini.rosiesbooks.library.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EffectiveMetadataResolverTest {

  private static final EditionMetadata CANONICAL =
      new EditionMetadata(
          "Canonical title",
          Optional.of("Canonical subtitle"),
          List.of("First Author", "Second Author"),
          Optional.of("Hardcover"),
          Optional.of(Isbn10.parse("0306406152")),
          Optional.of(Isbn13.parse("9780306406157")),
          Optional.of("Canonical publisher"),
          Optional.of(PartialPublicationDate.year(1999)),
          Optional.of(432),
          Optional.of("en"),
          Optional.of("Canonical description"));

  @Test
  void inheritedFieldsResolveToEveryCanonicalValue() {
    assertEquals(CANONICAL, EffectiveMetadataResolver.resolve(CANONICAL, MetadataOverrides.none()));
  }

  @Test
  void appliesEveryValueOverrideIndependentlyAndPreservesAuthorOrderAndDatePrecision() {
    EditionMetadata effective =
        EffectiveMetadataResolver.resolve(
            CANONICAL,
            new MetadataOverrides(
                MetadataOverride.value("Private title"),
                MetadataOverride.value("Private subtitle"),
                MetadataOverride.value(List.of("Second Author", "First Author")),
                MetadataOverride.value("Paperback"),
                MetadataOverride.value(Isbn10.parse("080442957X")),
                MetadataOverride.value(Isbn13.parse("9791090636071")),
                MetadataOverride.value("Private publisher"),
                MetadataOverride.value(PartialPublicationDate.yearMonth(2020, 7)),
                MetadataOverride.value(321),
                MetadataOverride.value("fr"),
                MetadataOverride.value("Private description")));

    assertEquals("Private title", effective.title());
    assertEquals(Optional.of("Private subtitle"), effective.subtitle());
    assertEquals(List.of("Second Author", "First Author"), effective.authors());
    assertEquals(Optional.of("Paperback"), effective.format());
    assertEquals(Optional.of(Isbn10.parse("080442957X")), effective.isbn10());
    assertEquals(Optional.of(Isbn13.parse("9791090636071")), effective.isbn13());
    assertEquals(Optional.of("Private publisher"), effective.publisher());
    assertEquals(
        Optional.of(PartialPublicationDate.yearMonth(2020, 7)), effective.publicationDate());
    assertEquals(Optional.of(321), effective.pageCount());
    assertEquals(Optional.of("fr"), effective.language());
    assertEquals(Optional.of("Private description"), effective.description());
  }

  @Test
  void explicitBlankClearsEveryOptionalField() {
    EditionMetadata effective =
        EffectiveMetadataResolver.resolve(
            CANONICAL,
            new MetadataOverrides(
                MetadataOverride.inherited(),
                MetadataOverride.blank(),
                MetadataOverride.inherited(),
                MetadataOverride.blank(),
                MetadataOverride.blank(),
                MetadataOverride.blank(),
                MetadataOverride.blank(),
                MetadataOverride.blank(),
                MetadataOverride.blank(),
                MetadataOverride.blank(),
                MetadataOverride.blank()));

    assertEquals("Canonical title", effective.title());
    assertEquals(List.of("First Author", "Second Author"), effective.authors());
    assertEquals(Optional.empty(), effective.subtitle());
    assertEquals(Optional.empty(), effective.format());
    assertEquals(Optional.empty(), effective.isbn10());
    assertEquals(Optional.empty(), effective.isbn13());
    assertEquals(Optional.empty(), effective.publisher());
    assertEquals(Optional.empty(), effective.publicationDate());
    assertEquals(Optional.empty(), effective.pageCount());
    assertEquals(Optional.empty(), effective.language());
    assertEquals(Optional.empty(), effective.description());
  }

  @Test
  void resettingFieldsToInheritedRestoresCanonicalValues() {
    MetadataOverrides changed =
        MetadataOverrides.none().withTitle(MetadataOverride.value("Changed"));
    assertEquals("Changed", EffectiveMetadataResolver.resolve(CANONICAL, changed).title());
    assertEquals(
        CANONICAL,
        EffectiveMetadataResolver.resolve(
            CANONICAL, changed.withTitle(MetadataOverride.inherited())));
  }

  @Test
  void rejectsMissingOrBlankRequiredEffectiveMetadata() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            EffectiveMetadataResolver.resolve(
                CANONICAL, MetadataOverrides.none().withTitle(MetadataOverride.blank())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            EffectiveMetadataResolver.resolve(
                CANONICAL, MetadataOverrides.none().withTitle(MetadataOverride.value(" \t "))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            EffectiveMetadataResolver.resolve(
                CANONICAL, MetadataOverrides.none().withAuthors(MetadataOverride.blank())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            EffectiveMetadataResolver.resolve(
                CANONICAL,
                MetadataOverrides.none().withAuthors(MetadataOverride.value(List.of()))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            EffectiveMetadataResolver.resolve(
                CANONICAL,
                MetadataOverrides.none()
                    .withAuthors(MetadataOverride.value(List.of("Valid", "  ")))));
  }

  @Test
  void copiesCanonicalAndOverrideAuthorInputs() {
    var canonicalAuthors = new ArrayList<>(List.of("Canonical"));
    var overrideAuthors = new ArrayList<>(List.of("Private"));
    EditionMetadata canonical =
        new EditionMetadata(
            "Title",
            Optional.empty(),
            canonicalAuthors,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    MetadataOverrides overrides =
        MetadataOverrides.none().withAuthors(MetadataOverride.value(overrideAuthors));

    canonicalAuthors.add("Mutation");
    overrideAuthors.add("Mutation");

    assertEquals(List.of("Canonical"), canonical.authors());
    assertEquals(
        List.of("Private"), EffectiveMetadataResolver.resolve(canonical, overrides).authors());
    assertThrows(UnsupportedOperationException.class, () -> canonical.authors().add("Mutation"));
  }
}

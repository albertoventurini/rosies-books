package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION_AUTHOR_OVERRIDE;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION_METADATA_OVERRIDE;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.library.internal.Isbn10;
import com.albertoventurini.rosiesbooks.library.internal.Isbn13;
import com.albertoventurini.rosiesbooks.library.internal.MetadataOverride;
import com.albertoventurini.rosiesbooks.library.internal.MetadataOverrides;
import com.albertoventurini.rosiesbooks.library.internal.PartialPublicationDate;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

@ApplicationScoped
class MetadataOverrideRepository {

  private final DSLContext dsl;

  MetadataOverrideRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  boolean save(CurrentUser owner, UserEditionId userEditionId, MetadataOverrides overrides) {
    dsl.deleteFrom(USER_EDITION_METADATA_OVERRIDE)
        .where(
            USER_EDITION_METADATA_OVERRIDE
                .USER_EDITION_ID
                .eq(userEditionId.value())
                .and(
                    DSL.exists(
                        dsl.selectOne()
                            .from(USER_EDITION)
                            .where(
                                USER_EDITION
                                    .ID
                                    .eq(USER_EDITION_METADATA_OVERRIDE.USER_EDITION_ID)
                                    .and(USER_EDITION.USER_ID.eq(owner.id().value()))))))
        .execute();

    PartialPublicationDate publicationDate = overrides.publicationDate().value().orElse(null);
    int inserted =
        dsl.insertInto(
                USER_EDITION_METADATA_OVERRIDE,
                USER_EDITION_METADATA_OVERRIDE.USER_EDITION_ID,
                USER_EDITION_METADATA_OVERRIDE.TITLE_IS_OVERRIDDEN,
                USER_EDITION_METADATA_OVERRIDE.TITLE_VALUE,
                USER_EDITION_METADATA_OVERRIDE.SUBTITLE_IS_OVERRIDDEN,
                USER_EDITION_METADATA_OVERRIDE.SUBTITLE_VALUE,
                USER_EDITION_METADATA_OVERRIDE.AUTHORS_IS_OVERRIDDEN,
                USER_EDITION_METADATA_OVERRIDE.FORMAT_IS_OVERRIDDEN,
                USER_EDITION_METADATA_OVERRIDE.FORMAT_VALUE,
                USER_EDITION_METADATA_OVERRIDE.ISBN_10_IS_OVERRIDDEN,
                USER_EDITION_METADATA_OVERRIDE.ISBN_10_VALUE,
                USER_EDITION_METADATA_OVERRIDE.ISBN_13_IS_OVERRIDDEN,
                USER_EDITION_METADATA_OVERRIDE.ISBN_13_VALUE,
                USER_EDITION_METADATA_OVERRIDE.PUBLISHER_IS_OVERRIDDEN,
                USER_EDITION_METADATA_OVERRIDE.PUBLISHER_VALUE,
                USER_EDITION_METADATA_OVERRIDE.PUBLICATION_DATE_IS_OVERRIDDEN,
                USER_EDITION_METADATA_OVERRIDE.PUBLICATION_YEAR_VALUE,
                USER_EDITION_METADATA_OVERRIDE.PUBLICATION_MONTH_VALUE,
                USER_EDITION_METADATA_OVERRIDE.PUBLICATION_DAY_VALUE,
                USER_EDITION_METADATA_OVERRIDE.PAGE_COUNT_IS_OVERRIDDEN,
                USER_EDITION_METADATA_OVERRIDE.PAGE_COUNT_VALUE,
                USER_EDITION_METADATA_OVERRIDE.LANGUAGE_IS_OVERRIDDEN,
                USER_EDITION_METADATA_OVERRIDE.LANGUAGE_VALUE,
                USER_EDITION_METADATA_OVERRIDE.DESCRIPTION_IS_OVERRIDDEN,
                USER_EDITION_METADATA_OVERRIDE.DESCRIPTION_VALUE)
            .select(
                dsl.select(
                        USER_EDITION.ID,
                        DSL.val(overrides.title().isOverridden()),
                        DSL.val(value(overrides.title())),
                        DSL.val(overrides.subtitle().isOverridden()),
                        DSL.val(value(overrides.subtitle())),
                        DSL.val(overrides.authors().isOverridden()),
                        DSL.val(overrides.format().isOverridden()),
                        DSL.val(value(overrides.format())),
                        DSL.val(overrides.isbn10().isOverridden()),
                        DSL.val(overrides.isbn10().value().map(Isbn10::value).orElse(null)),
                        DSL.val(overrides.isbn13().isOverridden()),
                        DSL.val(overrides.isbn13().value().map(Isbn13::value).orElse(null)),
                        DSL.val(overrides.publisher().isOverridden()),
                        DSL.val(value(overrides.publisher())),
                        DSL.val(overrides.publicationDate().isOverridden()),
                        DSL.val(publicationDate == null ? null : publicationDate.year()),
                        DSL.val(publicationDate == null ? null : publicationDate.month()),
                        DSL.val(publicationDate == null ? null : publicationDate.day()),
                        DSL.val(overrides.pageCount().isOverridden()),
                        DSL.val(value(overrides.pageCount())),
                        DSL.val(overrides.language().isOverridden()),
                        DSL.val(value(overrides.language())),
                        DSL.val(overrides.description().isOverridden()),
                        DSL.val(value(overrides.description())))
                    .from(USER_EDITION)
                    .where(
                        USER_EDITION
                            .ID
                            .eq(userEditionId.value())
                            .and(USER_EDITION.USER_ID.eq(owner.id().value()))))
            .execute();
    if (inserted == 0) {
      return false;
    }

    List<String> authors = overrides.authors().value().orElse(null);
    if (authors != null) {
      for (int position = 0; position < authors.size(); position++) {
        dsl.insertInto(
                USER_EDITION_AUTHOR_OVERRIDE,
                USER_EDITION_AUTHOR_OVERRIDE.USER_EDITION_ID,
                USER_EDITION_AUTHOR_OVERRIDE.AUTHORS_IS_OVERRIDDEN,
                USER_EDITION_AUTHOR_OVERRIDE.POSITION,
                USER_EDITION_AUTHOR_OVERRIDE.NAME)
            .select(
                dsl.select(
                        USER_EDITION_METADATA_OVERRIDE.USER_EDITION_ID,
                        DSL.val(true),
                        DSL.val(position),
                        DSL.val(authors.get(position)))
                    .from(USER_EDITION_METADATA_OVERRIDE)
                    .join(USER_EDITION)
                    .on(USER_EDITION.ID.eq(USER_EDITION_METADATA_OVERRIDE.USER_EDITION_ID))
                    .where(
                        USER_EDITION_METADATA_OVERRIDE
                            .USER_EDITION_ID
                            .eq(userEditionId.value())
                            .and(USER_EDITION.USER_ID.eq(owner.id().value()))))
            .execute();
      }
    }
    return true;
  }

  Optional<MetadataOverrides> find(CurrentUser owner, UserEditionId userEditionId) {
    return dsl.select(USER_EDITION_METADATA_OVERRIDE.fields())
        .from(USER_EDITION_METADATA_OVERRIDE)
        .join(USER_EDITION)
        .on(USER_EDITION.ID.eq(USER_EDITION_METADATA_OVERRIDE.USER_EDITION_ID))
        .where(
            USER_EDITION
                .USER_ID
                .eq(owner.id().value())
                .and(USER_EDITION.ID.eq(userEditionId.value())))
        .fetchOptional(
            row -> {
              boolean authorsOverridden =
                  row.get(USER_EDITION_METADATA_OVERRIDE.AUTHORS_IS_OVERRIDDEN);
              List<String> authors =
                  authorsOverridden
                      ? dsl.select(USER_EDITION_AUTHOR_OVERRIDE.NAME)
                          .from(USER_EDITION_AUTHOR_OVERRIDE)
                          .join(USER_EDITION)
                          .on(USER_EDITION.ID.eq(USER_EDITION_AUTHOR_OVERRIDE.USER_EDITION_ID))
                          .where(
                              USER_EDITION
                                  .USER_ID
                                  .eq(owner.id().value())
                                  .and(USER_EDITION.ID.eq(userEditionId.value())))
                          .orderBy(USER_EDITION_AUTHOR_OVERRIDE.POSITION)
                          .fetch(USER_EDITION_AUTHOR_OVERRIDE.NAME)
                      : null;
              Integer year = row.get(USER_EDITION_METADATA_OVERRIDE.PUBLICATION_YEAR_VALUE);
              Integer month = row.get(USER_EDITION_METADATA_OVERRIDE.PUBLICATION_MONTH_VALUE);
              Integer day = row.get(USER_EDITION_METADATA_OVERRIDE.PUBLICATION_DAY_VALUE);
              PartialPublicationDate publicationDate =
                  year == null && month == null && day == null
                      ? null
                      : new PartialPublicationDate(year, month, day);
              return new MetadataOverrides(
                  override(
                      row.get(USER_EDITION_METADATA_OVERRIDE.TITLE_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.TITLE_VALUE)),
                  override(
                      row.get(USER_EDITION_METADATA_OVERRIDE.SUBTITLE_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.SUBTITLE_VALUE)),
                  authorsOverridden
                      ? (authors.isEmpty()
                          ? MetadataOverride.blank()
                          : MetadataOverride.value(authors))
                      : MetadataOverride.inherited(),
                  override(
                      row.get(USER_EDITION_METADATA_OVERRIDE.FORMAT_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.FORMAT_VALUE)),
                  isbn10Override(
                      row.get(USER_EDITION_METADATA_OVERRIDE.ISBN_10_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.ISBN_10_VALUE)),
                  isbn13Override(
                      row.get(USER_EDITION_METADATA_OVERRIDE.ISBN_13_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.ISBN_13_VALUE)),
                  override(
                      row.get(USER_EDITION_METADATA_OVERRIDE.PUBLISHER_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.PUBLISHER_VALUE)),
                  override(
                      row.get(USER_EDITION_METADATA_OVERRIDE.PUBLICATION_DATE_IS_OVERRIDDEN),
                      publicationDate),
                  override(
                      row.get(USER_EDITION_METADATA_OVERRIDE.PAGE_COUNT_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.PAGE_COUNT_VALUE)),
                  override(
                      row.get(USER_EDITION_METADATA_OVERRIDE.LANGUAGE_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.LANGUAGE_VALUE)),
                  override(
                      row.get(USER_EDITION_METADATA_OVERRIDE.DESCRIPTION_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.DESCRIPTION_VALUE)));
            });
  }

  private static <T> T value(MetadataOverride<T> override) {
    return override.value().orElse(null);
  }

  private static <T> MetadataOverride<T> override(boolean overridden, T value) {
    if (!overridden) {
      return MetadataOverride.inherited();
    }
    return value == null ? MetadataOverride.blank() : MetadataOverride.value(value);
  }

  private static MetadataOverride<Isbn10> isbn10Override(boolean overridden, String value) {
    return override(overridden, value == null ? null : Isbn10.parse(value));
  }

  private static MetadataOverride<Isbn13> isbn13Override(boolean overridden, String value) {
    return override(overridden, value == null ? null : Isbn13.parse(value));
  }
}

package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION_AUTHOR_OVERRIDE;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION_METADATA_OVERRIDE;

import com.albertoventurini.rosiesbooks.identity.api.UserId;
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

  boolean save(UserId owner, UserEditionId userEditionId, MetadataOverrides overrides) {
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
                                    .and(USER_EDITION.USER_ID.eq(owner.value()))))))
        .execute();

    PartialPublicationDate publicationDate = overrides.publicationDate().value();
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
                        DSL.val(overrides.title().overridden()),
                        DSL.val(overrides.title().value()),
                        DSL.val(overrides.subtitle().overridden()),
                        DSL.val(overrides.subtitle().value()),
                        DSL.val(overrides.authors().overridden()),
                        DSL.val(overrides.format().overridden()),
                        DSL.val(overrides.format().value()),
                        DSL.val(overrides.isbn10().overridden()),
                        DSL.val(overrides.isbn10().value()),
                        DSL.val(overrides.isbn13().overridden()),
                        DSL.val(overrides.isbn13().value()),
                        DSL.val(overrides.publisher().overridden()),
                        DSL.val(overrides.publisher().value()),
                        DSL.val(overrides.publicationDate().overridden()),
                        DSL.val(publicationDate == null ? null : publicationDate.year()),
                        DSL.val(publicationDate == null ? null : publicationDate.month()),
                        DSL.val(publicationDate == null ? null : publicationDate.day()),
                        DSL.val(overrides.pageCount().overridden()),
                        DSL.val(overrides.pageCount().value()),
                        DSL.val(overrides.language().overridden()),
                        DSL.val(overrides.language().value()),
                        DSL.val(overrides.description().overridden()),
                        DSL.val(overrides.description().value()))
                    .from(USER_EDITION)
                    .where(
                        USER_EDITION
                            .ID
                            .eq(userEditionId.value())
                            .and(USER_EDITION.USER_ID.eq(owner.value()))))
            .execute();
    if (inserted == 0) {
      return false;
    }

    List<String> authors = overrides.authors().value();
    if (overrides.authors().overridden() && authors != null) {
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
                            .and(USER_EDITION.USER_ID.eq(owner.value()))))
            .execute();
      }
    }
    return true;
  }

  Optional<MetadataOverrides> find(UserId owner, UserEditionId userEditionId) {
    return dsl.select(USER_EDITION_METADATA_OVERRIDE.fields())
        .from(USER_EDITION_METADATA_OVERRIDE)
        .join(USER_EDITION)
        .on(USER_EDITION.ID.eq(USER_EDITION_METADATA_OVERRIDE.USER_EDITION_ID))
        .where(
            USER_EDITION.USER_ID.eq(owner.value()).and(USER_EDITION.ID.eq(userEditionId.value())))
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
                                  .eq(owner.value())
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
                  value(
                      row.get(USER_EDITION_METADATA_OVERRIDE.TITLE_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.TITLE_VALUE)),
                  value(
                      row.get(USER_EDITION_METADATA_OVERRIDE.SUBTITLE_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.SUBTITLE_VALUE)),
                  value(authorsOverridden, authors),
                  value(
                      row.get(USER_EDITION_METADATA_OVERRIDE.FORMAT_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.FORMAT_VALUE)),
                  value(
                      row.get(USER_EDITION_METADATA_OVERRIDE.ISBN_10_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.ISBN_10_VALUE)),
                  value(
                      row.get(USER_EDITION_METADATA_OVERRIDE.ISBN_13_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.ISBN_13_VALUE)),
                  value(
                      row.get(USER_EDITION_METADATA_OVERRIDE.PUBLISHER_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.PUBLISHER_VALUE)),
                  value(
                      row.get(USER_EDITION_METADATA_OVERRIDE.PUBLICATION_DATE_IS_OVERRIDDEN),
                      publicationDate),
                  value(
                      row.get(USER_EDITION_METADATA_OVERRIDE.PAGE_COUNT_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.PAGE_COUNT_VALUE)),
                  value(
                      row.get(USER_EDITION_METADATA_OVERRIDE.LANGUAGE_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.LANGUAGE_VALUE)),
                  value(
                      row.get(USER_EDITION_METADATA_OVERRIDE.DESCRIPTION_IS_OVERRIDDEN),
                      row.get(USER_EDITION_METADATA_OVERRIDE.DESCRIPTION_VALUE)));
            });
  }

  private static <T> OverrideValue<T> value(boolean overridden, T value) {
    return new OverrideValue<>(overridden, value);
  }
}

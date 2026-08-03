package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.COVER_ASSET;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

@ApplicationScoped
class CoverAssetRepository {

  private final DSLContext dsl;

  CoverAssetRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  void save(UUID id, byte[] content, String mimeType) {
    dsl.insertInto(COVER_ASSET)
        .set(COVER_ASSET.ID, id)
        .set(COVER_ASSET.CONTENT, content)
        .set(COVER_ASSET.MIME_TYPE, mimeType)
        .execute();
  }

  Optional<CoverAsset> find(UUID id) {
    return dsl.select(COVER_ASSET.ID, COVER_ASSET.CONTENT, COVER_ASSET.MIME_TYPE)
        .from(COVER_ASSET)
        .where(COVER_ASSET.ID.eq(id))
        .fetchOptional(record -> new CoverAsset(record.value1(), record.value2(), record.value3()));
  }

  boolean delete(UUID id) {
    return dsl.deleteFrom(COVER_ASSET).where(COVER_ASSET.ID.eq(id)).execute() == 1;
  }

  long count() {
    return dsl.fetchCount(COVER_ASSET);
  }

  void deleteAll() {
    dsl.deleteFrom(COVER_ASSET).execute();
  }
}

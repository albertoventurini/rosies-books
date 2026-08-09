package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.COVER_ASSET;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.ZoneOffset;
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

  UUID saveOrFind(
      byte[] content,
      String mimeType,
      int width,
      int height,
      String provenanceUrl,
      Instant fetchedAt) {
    String hash = sha256(content);
    Optional<UUID> existing =
        dsl.select(COVER_ASSET.ID)
            .from(COVER_ASSET)
            .where(COVER_ASSET.SHA256.eq(hash))
            .fetchOptional(COVER_ASSET.ID);
    if (existing.isPresent()) return existing.get();
    UUID id = UUID.randomUUID();
    dsl.insertInto(COVER_ASSET)
        .set(COVER_ASSET.ID, id)
        .set(COVER_ASSET.CONTENT, content)
        .set(COVER_ASSET.MIME_TYPE, mimeType)
        .set(COVER_ASSET.SHA256, hash)
        .set(COVER_ASSET.WIDTH, width)
        .set(COVER_ASSET.HEIGHT, height)
        .set(COVER_ASSET.PROVENANCE_URL, provenanceUrl)
        .set(COVER_ASSET.FETCHED_AT, fetchedAt.atOffset(ZoneOffset.UTC))
        .onConflictDoNothing()
        .execute();
    return dsl.select(COVER_ASSET.ID)
        .from(COVER_ASSET)
        .where(COVER_ASSET.SHA256.eq(hash))
        .fetchSingle(COVER_ASSET.ID);
  }

  Optional<CoverAsset> find(UUID id) {
    return dsl.select(
            COVER_ASSET.ID,
            COVER_ASSET.CONTENT,
            COVER_ASSET.MIME_TYPE,
            COVER_ASSET.SHA256,
            COVER_ASSET.WIDTH,
            COVER_ASSET.HEIGHT,
            COVER_ASSET.PROVENANCE_URL,
            COVER_ASSET.FETCHED_AT)
        .from(COVER_ASSET)
        .where(COVER_ASSET.ID.eq(id))
        .fetchOptional(
            record ->
                new CoverAsset(
                    record.value1(),
                    record.value2(),
                    record.value3(),
                    record.value4(),
                    record.value5(),
                    record.value6(),
                    record.value7(),
                    record.value8() == null ? null : record.value8().toInstant()));
  }

  Optional<CoverAsset> findByHash(String hash) {
    return dsl.select(
            COVER_ASSET.ID,
            COVER_ASSET.CONTENT,
            COVER_ASSET.MIME_TYPE,
            COVER_ASSET.SHA256,
            COVER_ASSET.WIDTH,
            COVER_ASSET.HEIGHT,
            COVER_ASSET.PROVENANCE_URL,
            COVER_ASSET.FETCHED_AT)
        .from(COVER_ASSET)
        .where(COVER_ASSET.SHA256.eq(hash))
        .fetchOptional(
            record ->
                new CoverAsset(
                    record.value1(),
                    record.value2(),
                    record.value3(),
                    record.value4(),
                    record.value5(),
                    record.value6(),
                    record.value7(),
                    record.value8() == null ? null : record.value8().toInstant()));
  }

  private static String sha256(byte[] content) {
    try {
      return java.util.HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
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

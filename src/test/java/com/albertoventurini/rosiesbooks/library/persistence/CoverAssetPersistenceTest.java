package com.albertoventurini.rosiesbooks.library.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.UUID;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CoverAssetPersistenceTest {

  private static final int MAX_CONTENT_BYTES = 5_242_880;

  @Inject CoverAssetRepository repository;
  @Inject CoverAssetTestCoordinator coordinator;

  @AfterEach
  void removeRows() {
    coordinator.deleteAll();
  }

  @Test
  void roundTripsAnExactFiveMebibyteCoverAndMimeType() {
    UUID id = UUID.randomUUID();
    byte[] content = patternedBytes(MAX_CONTENT_BYTES);

    coordinator.store(id, content, "image/avif");

    CoverAsset stored = repository.find(id).orElseThrow();
    assertEquals(id, stored.id());
    assertEquals("image/avif", stored.mimeType());
    assertArrayEquals(content, stored.content());
  }

  @Test
  void databaseRejectsOneByteOverTheLimitWithoutLeavingARow() {
    UUID id = UUID.randomUUID();

    DataAccessException failure =
        assertThrows(
            DataAccessException.class,
            () -> coordinator.store(id, new byte[MAX_CONTENT_BYTES + 1], "image/png"));

    SQLException sqlFailure = failure.getCause(SQLException.class);
    assertEquals("23514", sqlFailure.getSQLState());
    assertTrue(sqlFailure.getMessage().contains("cover_asset_content_max_5_mib"));
    assertTrue(repository.find(id).isEmpty());
  }

  @Test
  void commitsACompletedTransaction() {
    UUID id = UUID.randomUUID();

    coordinator.store(id, new byte[] {3, 1, 4}, "image/jpeg");

    assertEquals(1, repository.count());
  }

  @Test
  void rollsBackEveryRepositoryWriteWhenTheCoordinatorFails() {
    assertThrows(
        CoverAssetTestCoordinator.DeliberateFailure.class,
        () -> coordinator.storeTwoThenFail(UUID.randomUUID(), UUID.randomUUID()));

    assertEquals(0, repository.count());
  }

  private static byte[] patternedBytes(int size) {
    byte[] result = new byte[size];
    for (int index = 0; index < result.length; index++) {
      result[index] = (byte) (index % 251);
    }
    return result;
  }
}

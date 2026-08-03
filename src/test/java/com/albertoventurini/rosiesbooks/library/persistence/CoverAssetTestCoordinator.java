package com.albertoventurini.rosiesbooks.library.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
class CoverAssetTestCoordinator {

  private final CoverAssetRepository repository;

  CoverAssetTestCoordinator(CoverAssetRepository repository) {
    this.repository = repository;
  }

  @Transactional
  void store(UUID id, byte[] content, String mimeType) {
    repository.save(id, content, mimeType);
  }

  @Transactional
  void storeTwoThenFail(UUID firstId, UUID secondId) {
    repository.save(firstId, new byte[] {1}, "image/first");
    repository.save(secondId, new byte[] {2}, "image/second");
    throw new DeliberateFailure();
  }

  @Transactional
  void deleteAll() {
    repository.deleteAll();
  }

  static final class DeliberateFailure extends RuntimeException {}
}

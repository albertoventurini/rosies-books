package com.albertoventurini.rosiesbooks.library.persistence;

import java.time.Instant;
import java.util.UUID;

record CoverAsset(
    UUID id,
    byte[] content,
    String mimeType,
    String sha256,
    Integer width,
    Integer height,
    String provenanceUrl,
    Instant fetchedAt) {
  CoverAsset {
    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }
}

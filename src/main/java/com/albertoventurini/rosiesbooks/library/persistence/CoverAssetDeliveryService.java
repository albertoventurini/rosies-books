package com.albertoventurini.rosiesbooks.library.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class CoverAssetDeliveryService {
  private final CoverAssetRepository assets;

  CoverAssetDeliveryService(CoverAssetRepository assets) {
    this.assets = assets;
  }

  public Optional<DeliveredCover> find(String hash) {
    if (hash == null || !hash.matches("[0-9a-f]{64}")) return Optional.empty();
    return assets
        .findByHash(hash)
        .map(value -> new DeliveredCover(value.content(), value.mimeType()));
  }

  public record DeliveredCover(byte[] content, String mimeType) {
    public DeliveredCover {
      content = content.clone();
    }

    @Override
    public byte[] content() {
      return content.clone();
    }
  }
}

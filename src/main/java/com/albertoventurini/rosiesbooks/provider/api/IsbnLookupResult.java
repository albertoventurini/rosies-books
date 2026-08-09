package com.albertoventurini.rosiesbooks.provider.api;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Provider-neutral outcomes of an exact ISBN edition lookup. */
public sealed interface IsbnLookupResult
    permits IsbnLookupResult.Found,
        IsbnLookupResult.NotFound,
        IsbnLookupResult.RateLimited,
        IsbnLookupResult.Unavailable,
        IsbnLookupResult.MalformedResponse {
  record Found(SelectedEdition edition) implements IsbnLookupResult {
    public Found {
      Objects.requireNonNull(edition, "edition");
    }
  }

  record NotFound() implements IsbnLookupResult {}

  record RateLimited(Optional<Duration> retryAfter) implements IsbnLookupResult {
    public RateLimited {
      Objects.requireNonNull(retryAfter, "retryAfter");
    }
  }

  record Unavailable() implements IsbnLookupResult {}

  record MalformedResponse() implements IsbnLookupResult {}
}

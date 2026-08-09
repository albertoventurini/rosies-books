package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.provider.api.SelectedEdition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Signs the provider result shown to a user before it reaches the review form. */
@ApplicationScoped
class ProviderReviewToken {
  private static final String HMAC = "HmacSHA256";
  private final ObjectMapper json;
  private final Clock clock;
  private final byte[] secret;
  private final Duration lifetime;

  ProviderReviewToken(
      ObjectMapper json,
      Clock clock,
      @ConfigProperty(name = "rosies-books.review-token.secret") String secret,
      @ConfigProperty(name = "rosies-books.review-token.lifetime", defaultValue = "10M")
          Duration lifetime) {
    if (secret == null || secret.isBlank())
      throw new IllegalArgumentException("Review token secret is required");
    this.json = json;
    this.clock = clock;
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.lifetime = lifetime;
  }

  String issue(String lookupIsbn, SelectedEdition edition) {
    try {
      byte[] payload =
          json.writeValueAsBytes(
              new Payload(clock.millis() + lifetime.toMillis(), lookupIsbn, edition));
      String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
      return encoded
          + "."
          + Base64.getUrlEncoder().withoutPadding().encodeToString(signature(encoded));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not create review token", exception);
    }
  }

  java.util.Optional<AcceptedProviderReview> accept(String token) {
    try {
      String[] parts = token == null ? new String[0] : token.split("\\.", -1);
      if (parts.length != 2) return java.util.Optional.empty();
      byte[] supplied = Base64.getUrlDecoder().decode(parts[1]);
      if (!MessageDigest.isEqual(signature(parts[0]), supplied)) return java.util.Optional.empty();
      Payload payload = json.readValue(Base64.getUrlDecoder().decode(parts[0]), Payload.class);
      if (payload.expiresAt() < clock.millis()
          || payload.lookupIsbn() == null
          || payload.edition() == null) return java.util.Optional.empty();
      return java.util.Optional.of(
          new AcceptedProviderReview(payload.lookupIsbn(), payload.edition()));
    } catch (RuntimeException | java.io.IOException exception) {
      return java.util.Optional.empty();
    }
  }

  private byte[] signature(String value) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(secret, HMAC));
      return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("Could not sign review token", exception);
    }
  }

  private record Payload(long expiresAt, String lookupIsbn, SelectedEdition edition) {}
}

record AcceptedProviderReview(String lookupIsbn, SelectedEdition edition) {}

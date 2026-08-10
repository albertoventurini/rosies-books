package com.albertoventurini.rosiesbooks.identity.authentication;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class GoogleIdentityValidator {

  static final String ISSUER = "https://accounts.google.com";
  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  private final Set<String> allowedEmails;

  GoogleIdentityValidator(Iterable<String> allowedEmails) {
    this.allowedEmails = normalizeAllowlist(allowedEmails);
    if (this.allowedEmails.isEmpty()) {
      throw new IllegalStateException(
          "Google OIDC allowlist must contain at least one valid email address");
    }
  }

  Optional<GoogleIdentity> validate(
      Object issuer, Object subject, Object email, Object emailVerified) {
    if (!ISSUER.equals(normalizeText(issuer)) || !Boolean.TRUE.equals(emailVerified)) {
      return Optional.empty();
    }
    String normalizedSubject = normalizeText(subject);
    String normalizedEmail = normalizeEmail(email);
    if (normalizedSubject == null
        || normalizedEmail == null
        || !allowedEmails.contains(normalizedEmail)) {
      return Optional.empty();
    }
    return Optional.of(new GoogleIdentity(ISSUER, normalizedSubject, normalizedEmail));
  }

  private static Set<String> normalizeAllowlist(Iterable<String> emails) {
    return java.util.stream.StreamSupport.stream(emails.spliterator(), false)
        .map(GoogleIdentityValidator::normalizeEmail)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static String normalizeText(Object value) {
    if (!(value instanceof String text)) {
      return null;
    }
    String normalized = text.strip();
    return normalized.isEmpty() ? null : normalized;
  }

  private static String normalizeEmail(Object value) {
    String normalized = normalizeText(value);
    if (normalized == null) {
      return null;
    }
    normalized = normalized.toLowerCase(Locale.ROOT);
    return EMAIL.matcher(normalized).matches() ? normalized : null;
  }
}

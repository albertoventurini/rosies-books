package com.albertoventurini.rosiesbooks.library.internal;

import java.util.Objects;
import java.util.Optional;

/** One private metadata choice: inherit, replace with a value, or explicitly clear. */
public sealed interface MetadataOverride<T>
    permits MetadataOverride.Inherited, MetadataOverride.Value, MetadataOverride.Blank {

  static <T> MetadataOverride<T> inherited() {
    return new Inherited<>();
  }

  static <T> MetadataOverride<T> value(T value) {
    return new Value<>(value);
  }

  static <T> MetadataOverride<T> blank() {
    return new Blank<>();
  }

  default boolean isInherited() {
    return this instanceof Inherited<?>;
  }

  default boolean isOverridden() {
    return !isInherited();
  }

  default boolean isBlank() {
    return this instanceof Blank<?>;
  }

  Optional<T> value();

  record Inherited<T>() implements MetadataOverride<T> {
    @Override
    public Optional<T> value() {
      return Optional.empty();
    }
  }

  record Value<T>(T overriddenValue) implements MetadataOverride<T> {
    public Value {
      Objects.requireNonNull(overriddenValue, "overriddenValue");
    }

    @Override
    public Optional<T> value() {
      return Optional.of(overriddenValue);
    }
  }

  record Blank<T>() implements MetadataOverride<T> {
    @Override
    public Optional<T> value() {
      return Optional.empty();
    }
  }
}

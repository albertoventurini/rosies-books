package com.albertoventurini.rosiesbooks.library.persistence;

final class DuplicateIsbn13Exception extends RuntimeException {

  DuplicateIsbn13Exception(Throwable cause) {
    super("Canonical ISBN-13 is already in use", cause);
  }
}

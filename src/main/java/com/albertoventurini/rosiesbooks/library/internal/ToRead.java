package com.albertoventurini.rosiesbooks.library.internal;

/** A book waiting to be read; it has no recorded reading dates. */
public record ToRead() implements ReadingState {}

package com.albertoventurini.rosiesbooks.library.internal;

/** Clears all recorded reading dates and moves the book to To Read. */
public record MoveToRead() implements ReadingStateTransition {}

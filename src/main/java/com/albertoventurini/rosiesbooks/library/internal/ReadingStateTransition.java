package com.albertoventurini.rosiesbooks.library.internal;

/** A request to move a user edition to another reading shelf. */
public sealed interface ReadingStateTransition permits MoveToRead, MoveToReading, MoveToFinished {}

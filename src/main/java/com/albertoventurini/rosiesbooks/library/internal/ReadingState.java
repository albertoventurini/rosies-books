package com.albertoventurini.rosiesbooks.library.internal;

/** A valid reading shelf together with exactly the dates that shelf permits. */
public sealed interface ReadingState permits ToRead, Reading, Finished {}

package com.albertoventurini.rosiesbooks.library.web;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;

class ManualEntryClockProducer {

  @Produces
  @Singleton
  Clock clock() {
    return Clock.systemUTC();
  }
}

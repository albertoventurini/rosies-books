/**
 * Owns editions, user-library records, shelves, search, notes, and preferences.
 */
@AppModule(
    name = "library",
    allowedDependencies = {"identity.api", "provider.api", "platform.api"})
package com.albertoventurini.rosiesbooks.library;

import com.albertoventurini.rosiesbooks.AppModule;

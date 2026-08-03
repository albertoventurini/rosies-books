package com.albertoventurini.rosiesbooks.identity.persistence;

import com.albertoventurini.rosiesbooks.identity.api.UserId;
import java.time.Instant;

record User(
    UserId id,
    String oidcIssuer,
    String oidcSubject,
    String email,
    Instant createdAt,
    Instant updatedAt) {}

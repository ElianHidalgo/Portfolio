package dev.elianhidalgo.portfolio.auth.application;

import java.time.Instant;

/** A signed admin token and the moment it stops being accepted. */
public record IssuedToken(String token, Instant expiresAt) {}

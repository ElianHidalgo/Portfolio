package dev.elianhidalgo.portfolio.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The admin login form. Generous caps: longer values can never be valid credentials anyway. */
record LoginRequest(@NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 200) String password) {}

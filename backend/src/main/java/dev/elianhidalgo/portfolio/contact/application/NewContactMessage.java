package dev.elianhidalgo.portfolio.contact.application;

/** A validated message, ready to store. */
public record NewContactMessage(String name, String email, String body, String locale) {}

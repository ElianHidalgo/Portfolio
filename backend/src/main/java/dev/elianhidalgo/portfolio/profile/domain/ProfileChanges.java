package dev.elianhidalgo.portfolio.profile.domain;

/**
 * Every field the owner may edit, already trimmed; optional fields are null when empty.
 */
public record ProfileChanges(
        String fullName,
        String headlineEs,
        String headlineEn,
        String aboutEs,
        String aboutEn,
        String location,
        String email,
        String githubUrl,
        String linkedinUrl,
        String avatarUrl,
        String cvUrlEs,
        String cvUrlEn) {
}

package dev.elianhidalgo.portfolio.projects.domain;

import java.util.List;

/** Every field the owner may edit on a project, already trimmed; the links are null when empty. */
public record ProjectChanges(
        String titleEs,
        String titleEn,
        String summaryEs,
        String summaryEn,
        String descriptionEs,
        String descriptionEn,
        List<String> technologies,
        String repoUrl,
        String demoUrl,
        boolean featured,
        boolean published) {
}

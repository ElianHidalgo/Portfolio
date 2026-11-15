package dev.elianhidalgo.portfolio.projects.web;

import dev.elianhidalgo.portfolio.projects.domain.Project;
import dev.elianhidalgo.portfolio.shared.LocalizedText;
import java.util.List;

public record ProjectSummaryResponse(
        String slug,
        LocalizedText title,
        LocalizedText summary,
        List<String> technologies,
        String repoUrl,
        String demoUrl,
        String imageUrl,
        boolean featured) {

    static ProjectSummaryResponse from(Project p) {
        return new ProjectSummaryResponse(
                p.getSlug(),
                new LocalizedText(p.getTitleEs(), p.getTitleEn()),
                new LocalizedText(p.getSummaryEs(), p.getSummaryEn()),
                List.copyOf(p.getTechnologies()),
                p.getRepoUrl(),
                p.getDemoUrl(),
                p.getImageUrl(),
                p.isFeatured());
    }
}

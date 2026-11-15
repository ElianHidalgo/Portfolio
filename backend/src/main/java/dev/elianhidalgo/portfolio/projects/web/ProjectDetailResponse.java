package dev.elianhidalgo.portfolio.projects.web;

import dev.elianhidalgo.portfolio.projects.domain.Project;
import dev.elianhidalgo.portfolio.shared.LocalizedText;
import java.util.List;

public record ProjectDetailResponse(
        String slug,
        LocalizedText title,
        LocalizedText summary,
        LocalizedText description,
        List<String> technologies,
        String repoUrl,
        String demoUrl,
        String imageUrl,
        boolean featured) {

    static ProjectDetailResponse from(Project p) {
        return new ProjectDetailResponse(
                p.getSlug(),
                new LocalizedText(p.getTitleEs(), p.getTitleEn()),
                new LocalizedText(p.getSummaryEs(), p.getSummaryEn()),
                new LocalizedText(p.getDescriptionEs(), p.getDescriptionEn()),
                List.copyOf(p.getTechnologies()),
                p.getRepoUrl(),
                p.getDemoUrl(),
                p.getImageUrl(),
                p.isFeatured());
    }
}

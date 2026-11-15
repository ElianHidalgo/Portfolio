package dev.elianhidalgo.portfolio.projects.web;

import dev.elianhidalgo.portfolio.projects.application.ProjectService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/api/projects")
    public List<ProjectSummaryResponse> list() {
        return projectService.listPublished().stream().map(ProjectSummaryResponse::from).toList();
    }

    @GetMapping("/api/projects/{slug}")
    public ProjectDetailResponse get(@PathVariable String slug) {
        return ProjectDetailResponse.from(projectService.getPublished(slug));
    }
}

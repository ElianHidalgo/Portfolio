package dev.elianhidalgo.portfolio.projects.application;

import dev.elianhidalgo.portfolio.projects.domain.Project;
import dev.elianhidalgo.portfolio.projects.domain.ProjectRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projects;

    public ProjectService(ProjectRepository projects) {
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<Project> listPublished() {
        return projects.findAllByPublishedTrueOrderByDisplayOrderAscSlugAsc();
    }

    @Transactional(readOnly = true)
    public Project getPublished(String slug) {
        return projects.findBySlugAndPublishedTrue(slug).orElseThrow(ProjectNotFoundException::new);
    }
}

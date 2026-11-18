package dev.elianhidalgo.portfolio.projects.application;

import dev.elianhidalgo.portfolio.projects.domain.Project;
import dev.elianhidalgo.portfolio.projects.domain.ProjectChanges;
import dev.elianhidalgo.portfolio.projects.domain.ProjectRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates, edits, hides, removes and reorders the projects shown on the site. */
@Service
public class ProjectEditor {

    private final ProjectRepository projects;

    public ProjectEditor(ProjectRepository projects) {
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<Project> list() {
        return projects.findAllByOrderByDisplayOrderAscSlugAsc();
    }

    @Transactional(readOnly = true)
    public Project get(long id) {
        return projects.findById(id).orElseThrow(ProjectNotFoundException::new);
    }

    @Transactional
    public Project create(String slug, ProjectChanges changes) {
        if (projects.existsBySlug(slug)) {
            throw new DuplicateSlugException();
        }
        int next = projects.findFirstByOrderByDisplayOrderDesc().map(last -> last.getDisplayOrder() + 1).orElse(0);
        try {
            return projects.saveAndFlush(new Project(slug, changes, next, Instant.now()));
        } catch (DataIntegrityViolationException e) {
            // Two tabs may race past the existence check; the unique constraint then decides.
            throw new DuplicateSlugException();
        }
    }

    @Transactional
    public Project update(long id, ProjectChanges changes) {
        var published = projects.findAllByPublishedTrue();
        var project = projects.findById(id).orElseThrow(ProjectNotFoundException::new);
        if (project.isPublished() && !changes.published()) {
            requireAnotherPublished(published, project);
        }
        project.update(changes);
        return projects.saveAndFlush(project);
    }

    @Transactional
    public void delete(long id) {
        var published = projects.findAllByPublishedTrue();
        var project = projects.findById(id).orElseThrow(ProjectNotFoundException::new);
        if (project.isPublished()) {
            requireAnotherPublished(published, project);
        }
        projects.delete(project);
    }

    @Transactional
    public List<Project> reorder(List<Long> ids) {
        List<Project> all = projects.findAll();
        Map<Long, Project> byId = all.stream().collect(Collectors.toMap(Project::getId, Function.identity()));
        if (ids.size() != all.size() || !new HashSet<>(ids).equals(byId.keySet())) {
            throw new InvalidProjectOrderException();
        }
        for (int position = 0; position < ids.size(); position++) {
            byId.get(ids.get(position)).placeAt(position);
        }
        return ids.stream().map(byId::get).toList();
    }

    /** The caller locks the published rows before reading its target, so concurrent requests cannot both remove the last visible one. */
    private void requireAnotherPublished(List<Project> published, Project project) {
        boolean another = published.stream()
                .anyMatch(other -> !other.getId().equals(project.getId()));
        if (!another) {
            throw new LastPublishedProjectException();
        }
    }
}

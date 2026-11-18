package dev.elianhidalgo.portfolio.projects.web;

import dev.elianhidalgo.portfolio.projects.application.ProjectEditor;
import dev.elianhidalgo.portfolio.projects.domain.Project;
import dev.elianhidalgo.portfolio.shared.LocalizedText;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lists, creates, edits, removes and reorders projects. Protected by the {@code /api/admin/**} security rule. */
@RestController
@RequestMapping("/api/admin/projects")
class AdminProjectsController {

    record OrderRequest(@NotNull @Size(max = 500) List<@NotNull Long> ids) {}

    record AdminProjectResponse(
            long id,
            String slug,
            LocalizedText title,
            LocalizedText summary,
            LocalizedText description,
            List<String> technologies,
            String repoUrl,
            String demoUrl,
            String imageUrl,
            boolean featured,
            boolean published,
            int displayOrder) {

        static AdminProjectResponse of(Project p) {
            return new AdminProjectResponse(
                    p.getId(),
                    p.getSlug(),
                    new LocalizedText(p.getTitleEs(), p.getTitleEn()),
                    new LocalizedText(p.getSummaryEs(), p.getSummaryEn()),
                    new LocalizedText(p.getDescriptionEs(), p.getDescriptionEn()),
                    List.copyOf(p.getTechnologies()),
                    p.getRepoUrl(),
                    p.getDemoUrl(),
                    p.getImageUrl(),
                    p.isFeatured(),
                    p.isPublished(),
                    p.getDisplayOrder());
        }
    }

    private final ProjectEditor editor;

    AdminProjectsController(ProjectEditor editor) {
        this.editor = editor;
    }

    @GetMapping
    List<AdminProjectResponse> list() {
        return editor.list().stream().map(AdminProjectResponse::of).toList();
    }

    @GetMapping("/{id}")
    AdminProjectResponse get(@PathVariable long id) {
        return AdminProjectResponse.of(editor.get(id));
    }

    @PostMapping
    ResponseEntity<AdminProjectResponse> create(@Valid @RequestBody ProjectRequest.Create request) {
        var project = editor.create(request.slug(), request.form().toChanges());
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminProjectResponse.of(project));
    }

    @PutMapping("/{id}")
    AdminProjectResponse update(@PathVariable long id, @Valid @RequestBody ProjectRequest request) {
        return AdminProjectResponse.of(editor.update(id, request.toChanges()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable long id) {
        editor.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/order")
    List<AdminProjectResponse> reorder(@Valid @RequestBody OrderRequest request) {
        return editor.reorder(request.ids()).stream().map(AdminProjectResponse::of).toList();
    }
}

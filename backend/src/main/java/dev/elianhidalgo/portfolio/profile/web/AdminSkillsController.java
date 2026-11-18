package dev.elianhidalgo.portfolio.profile.web;

import dev.elianhidalgo.portfolio.profile.application.SkillEditor;
import dev.elianhidalgo.portfolio.profile.domain.Skill;
import dev.elianhidalgo.portfolio.profile.domain.SkillCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Adds, edits, removes and reorders skills. Protected by the {@code /api/admin/**} security rule. */
@RestController
@RequestMapping("/api/admin/skills")
class AdminSkillsController {

    record SkillRequest(
            @NotBlank @Size(max = 60) @Pattern(regexp = ProfileRequest.NO_CONTROL) String name,
            @NotNull SkillCategory category) {

        SkillRequest {
            name = name == null ? null : name.strip();
        }
    }

    record OrderRequest(@NotNull @Size(max = 500) List<@NotNull Long> ids) {}

    record AdminSkillResponse(long id, String name, SkillCategory category, int displayOrder) {

        static AdminSkillResponse of(Skill skill) {
            return new AdminSkillResponse(skill.getId(), skill.getName(), skill.getCategory(), skill.getDisplayOrder());
        }
    }

    private final SkillEditor editor;

    AdminSkillsController(SkillEditor editor) {
        this.editor = editor;
    }

    @PostMapping
    ResponseEntity<AdminSkillResponse> create(@Valid @RequestBody SkillRequest request) {
        var skill = editor.create(request.name(), request.category());
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminSkillResponse.of(skill));
    }

    @PutMapping("/{id}")
    AdminSkillResponse update(@PathVariable long id, @Valid @RequestBody SkillRequest request) {
        return AdminSkillResponse.of(editor.update(id, request.name(), request.category()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable long id) {
        editor.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/order")
    List<AdminSkillResponse> reorder(@Valid @RequestBody OrderRequest request) {
        return editor.reorder(request.ids()).stream().map(AdminSkillResponse::of).toList();
    }
}

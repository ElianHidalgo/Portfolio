package dev.elianhidalgo.portfolio.profile.web;

import dev.elianhidalgo.portfolio.profile.application.ProfileEditor;
import dev.elianhidalgo.portfolio.profile.application.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reads and edits the owner profile. Protected by the {@code /api/admin/**} security rule. */
@RestController
@RequestMapping("/api/admin/profile")
class AdminProfileController {

    private final ProfileService profileService;
    private final ProfileEditor editor;

    AdminProfileController(ProfileService profileService, ProfileEditor editor) {
        this.profileService = profileService;
        this.editor = editor;
    }

    @GetMapping
    ProfileResponse get() {
        return ProfileResponse.from(profileService.getProfile());
    }

    @PutMapping
    ProfileResponse update(@Valid @RequestBody ProfileRequest request) {
        return ProfileResponse.from(editor.update(request.toChanges()));
    }
}

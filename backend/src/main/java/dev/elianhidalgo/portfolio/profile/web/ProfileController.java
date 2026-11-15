package dev.elianhidalgo.portfolio.profile.web;

import dev.elianhidalgo.portfolio.profile.application.ProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/api/profile")
    public ProfileResponse getProfile() {
        return ProfileResponse.from(profileService.getProfile());
    }
}

package dev.elianhidalgo.portfolio.profile.application;

import dev.elianhidalgo.portfolio.profile.domain.ProfileRepository;
import dev.elianhidalgo.portfolio.profile.domain.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final ProfileRepository profiles;
    private final SkillRepository skills;

    public ProfileService(ProfileRepository profiles, SkillRepository skills) {
        this.profiles = profiles;
        this.skills = skills;
    }

    @Transactional(readOnly = true)
    public ProfileWithSkills getProfile() {
        var profile = profiles.findAll().stream()
                .findFirst()
                .orElseThrow(ProfileNotFoundException::new);
        return new ProfileWithSkills(profile, skills.findAllByOrderByDisplayOrderAscNameAsc());
    }
}

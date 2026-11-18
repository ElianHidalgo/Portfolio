package dev.elianhidalgo.portfolio.profile.application;

import dev.elianhidalgo.portfolio.profile.domain.ProfileChanges;
import dev.elianhidalgo.portfolio.profile.domain.ProfileRepository;
import dev.elianhidalgo.portfolio.profile.domain.SkillRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileEditor {

    private final ProfileRepository profiles;
    private final SkillRepository skills;

    public ProfileEditor(ProfileRepository profiles, SkillRepository skills) {
        this.profiles = profiles;
        this.skills = skills;
    }

    @Transactional
    public ProfileWithSkills update(ProfileChanges changes) {
        var profile = profiles.findAll().stream().findFirst().orElseThrow(ProfileNotFoundException::new);
        profile.update(changes, Instant.now());
        return new ProfileWithSkills(profile, skills.findAllByOrderByDisplayOrderAscNameAsc());
    }
}

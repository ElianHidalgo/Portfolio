package dev.elianhidalgo.portfolio.profile.web;

import dev.elianhidalgo.portfolio.profile.application.ProfileWithSkills;
import dev.elianhidalgo.portfolio.shared.LocalizedText;
import java.util.List;

public record ProfileResponse(
        String fullName,
        LocalizedText headline,
        LocalizedText about,
        String location,
        String email,
        String githubUrl,
        String linkedinUrl,
        String avatarUrl,
        LocalizedText cvUrl,
        List<SkillResponse> skills) {

    static ProfileResponse from(ProfileWithSkills source) {
        var p = source.profile();
        return new ProfileResponse(
                p.getFullName(),
                new LocalizedText(p.getHeadlineEs(), p.getHeadlineEn()),
                new LocalizedText(p.getAboutEs(), p.getAboutEn()),
                p.getLocation(),
                p.getEmail(),
                p.getGithubUrl(),
                p.getLinkedinUrl(),
                p.getAvatarUrl(),
                new LocalizedText(p.getCvUrlEs(), p.getCvUrlEn()),
                source.skills().stream().map(SkillResponse::from).toList());
    }
}

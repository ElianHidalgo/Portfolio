package dev.elianhidalgo.portfolio.profile.application;

import dev.elianhidalgo.portfolio.profile.domain.Profile;
import dev.elianhidalgo.portfolio.profile.domain.Skill;
import java.util.List;

public record ProfileWithSkills(Profile profile, List<Skill> skills) {
}

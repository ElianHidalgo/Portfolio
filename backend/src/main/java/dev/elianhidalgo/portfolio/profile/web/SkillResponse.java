package dev.elianhidalgo.portfolio.profile.web;

import dev.elianhidalgo.portfolio.profile.domain.Skill;
import dev.elianhidalgo.portfolio.profile.domain.SkillCategory;

public record SkillResponse(String name, SkillCategory category) {

    static SkillResponse from(Skill skill) {
        return new SkillResponse(skill.getName(), skill.getCategory());
    }
}

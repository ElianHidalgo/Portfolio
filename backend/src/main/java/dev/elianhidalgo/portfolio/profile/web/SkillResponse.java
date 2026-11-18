package dev.elianhidalgo.portfolio.profile.web;

import dev.elianhidalgo.portfolio.profile.domain.Skill;
import dev.elianhidalgo.portfolio.profile.domain.SkillCategory;

public record SkillResponse(Long id, String name, SkillCategory category) {

    static SkillResponse from(Skill skill) {
        return new SkillResponse(skill.getId(), skill.getName(), skill.getCategory());
    }
}

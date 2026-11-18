package dev.elianhidalgo.portfolio.profile.application;

public class SkillNotFoundException extends RuntimeException {

    public SkillNotFoundException() {
        super("Skill not found");
    }
}

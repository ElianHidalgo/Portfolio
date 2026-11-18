package dev.elianhidalgo.portfolio.profile.application;

public class DuplicateSkillNameException extends RuntimeException {

    public DuplicateSkillNameException() {
        super("A skill with that name already exists");
    }
}

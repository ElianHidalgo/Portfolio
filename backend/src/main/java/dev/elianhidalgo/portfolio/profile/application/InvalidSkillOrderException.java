package dev.elianhidalgo.portfolio.profile.application;

public class InvalidSkillOrderException extends RuntimeException {

    public InvalidSkillOrderException() {
        super("The order must list every skill exactly once");
    }
}

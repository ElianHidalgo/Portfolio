package dev.elianhidalgo.portfolio.projects.application;

public class InvalidProjectOrderException extends RuntimeException {

    public InvalidProjectOrderException() {
        super("The order must list every project exactly once");
    }
}

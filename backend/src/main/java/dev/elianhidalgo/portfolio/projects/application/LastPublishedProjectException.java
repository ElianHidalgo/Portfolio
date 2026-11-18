package dev.elianhidalgo.portfolio.projects.application;

public class LastPublishedProjectException extends RuntimeException {

    public LastPublishedProjectException() {
        super("At least one project must stay published");
    }
}

package dev.elianhidalgo.portfolio.projects.application;

public class DuplicateSlugException extends RuntimeException {

    public DuplicateSlugException() {
        super("A project with that slug already exists");
    }
}

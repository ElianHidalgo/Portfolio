package dev.elianhidalgo.portfolio.publishing.application;

/** The provider refused the rebuild or could not be reached. The message never contains the hook URL. */
public class RebuildFailedException extends RuntimeException {

    public RebuildFailedException(String reason) {
        super(reason);
    }
}

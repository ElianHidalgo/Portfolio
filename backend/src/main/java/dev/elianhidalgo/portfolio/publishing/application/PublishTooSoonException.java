package dev.elianhidalgo.portfolio.publishing.application;

/** A publication was accepted less than a minute ago; Cloudflare is still building it. */
public class PublishTooSoonException extends RuntimeException {

    public PublishTooSoonException() {
        super("A publication is already in progress");
    }
}

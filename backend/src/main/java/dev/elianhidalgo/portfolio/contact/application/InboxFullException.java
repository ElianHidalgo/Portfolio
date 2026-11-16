package dev.elianhidalgo.portfolio.contact.application;

/** The daily cap of stored messages was reached; keeps a spam wave from filling the free database. */
public class InboxFullException extends RuntimeException {

    public InboxFullException() {
        super("The inbox is full for today");
    }
}

package dev.elianhidalgo.portfolio.contact.application;

public class MessageNotFoundException extends RuntimeException {

    public MessageNotFoundException() {
        super("Message not found");
    }
}

package dev.elianhidalgo.portfolio.contact.application;

import dev.elianhidalgo.portfolio.contact.domain.ContactMessage;
import dev.elianhidalgo.portfolio.contact.domain.ContactMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactService {

    private final ContactMessageRepository messages;
    private final int dailyCap;

    public ContactService(ContactMessageRepository messages, @Value("${app.contact.daily-cap:50}") int dailyCap) {
        this.messages = messages;
        this.dailyCap = dailyCap;
    }

    /** Stores the message unless the last 24 hours already reached the cap. */
    @Transactional
    public void submit(NewContactMessage message) {
        if (messages.countLast24Hours() >= dailyCap) {
            throw new InboxFullException();
        }
        messages.save(new ContactMessage(message.name(), message.email(), message.body(), message.locale()));
    }
}

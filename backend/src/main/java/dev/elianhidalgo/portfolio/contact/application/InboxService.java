package dev.elianhidalgo.portfolio.contact.application;

import dev.elianhidalgo.portfolio.contact.domain.ContactMessage;
import dev.elianhidalgo.portfolio.contact.domain.ContactMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The admin's view of the contact messages. */
@Service
public class InboxService {

    static final int MAX_PAGE_SIZE = 50;

    private final ContactMessageRepository messages;

    public InboxService(ContactMessageRepository messages) {
        this.messages = messages;
    }

    /** Newest first; the page size is kept between 1 and {@value #MAX_PAGE_SIZE}. */
    @Transactional(readOnly = true)
    public Page<ContactMessage> page(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return messages.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(Math.max(page, 0), safeSize));
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return messages.countByReadFalse();
    }

    @Transactional
    public void setRead(long id, boolean read) {
        messages.findById(id).orElseThrow(MessageNotFoundException::new).markRead(read);
    }

    @Transactional
    public void delete(long id) {
        ContactMessage message = messages.findById(id).orElseThrow(MessageNotFoundException::new);
        messages.delete(message);
    }
}

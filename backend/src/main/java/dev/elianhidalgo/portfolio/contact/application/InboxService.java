package dev.elianhidalgo.portfolio.contact.application;

import dev.elianhidalgo.portfolio.contact.domain.ContactMessage;
import dev.elianhidalgo.portfolio.contact.domain.ContactMessageRepository;
import java.util.List;
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

    /**
     * Newest first, starting after the cursor {@code before} (an id; null for the first page).
     * A cursor, unlike a page number, stays correct when messages are deleted or arrive while
     * the admin is reading. The size is kept between 1 and {@value #MAX_PAGE_SIZE}.
     */
    @Transactional(readOnly = true)
    public List<ContactMessage> page(Long before, int size) {
        var limit = PageRequest.ofSize(Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return before == null ? messages.findAllByOrderByIdDesc(limit) : messages.findByIdLessThanOrderByIdDesc(before, limit);
    }

    @Transactional(readOnly = true)
    public long total() {
        return messages.count();
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

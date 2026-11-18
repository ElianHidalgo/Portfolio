package dev.elianhidalgo.portfolio.contact.web;

import dev.elianhidalgo.portfolio.contact.application.InboxService;
import dev.elianhidalgo.portfolio.contact.application.MessageNotFoundException;
import dev.elianhidalgo.portfolio.contact.domain.ContactMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin inbox. Protected by the {@code /api/admin/**} security rule; this module only
 * decides what the inbox shows and allows.
 */
@RestController
@RequestMapping("/api/admin/messages")
class AdminMessagesController {

    record MessageResponse(long id, String name, String email, String message, String locale, Instant createdAt, boolean read) {

        static MessageResponse of(ContactMessage message) {
            return new MessageResponse(message.getId(), message.getName(), message.getEmail(), message.getBody(),
                    message.getLocale(), message.getCreatedAt(), message.isRead());
        }
    }

    record MessagePageResponse(List<MessageResponse> items, long total, long unread) {}

    record ReadRequest(@NotNull Boolean read) {}

    private final InboxService inbox;

    AdminMessagesController(InboxService inbox) {
        this.inbox = inbox;
    }

    @GetMapping
    MessagePageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<ContactMessage> messages = inbox.page(page, size);
        return new MessagePageResponse(messages.map(MessageResponse::of).getContent(), messages.getTotalElements(),
                inbox.unreadCount());
    }

    @PatchMapping("/{id}/read")
    ResponseEntity<Void> setRead(@PathVariable long id, @Valid @RequestBody ReadRequest request) {
        inbox.setRead(id, request.read());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable long id) {
        inbox.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(MessageNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(MessageNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage()));
    }
}

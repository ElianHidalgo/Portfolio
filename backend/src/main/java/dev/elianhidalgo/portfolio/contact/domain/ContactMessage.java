package dev.elianhidalgo.portfolio.contact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A message a visitor sent from the public contact form. */
@Entity
@Table(schema = "contact", name = "message")
public class ContactMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String body;
    private String locale;

    @Column(name = "created_at")
    private Instant createdAt;

    private boolean read;

    protected ContactMessage() {}

    public ContactMessage(String name, String email, String body, String locale) {
        this.name = name;
        this.email = email;
        this.body = body;
        this.locale = locale;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getBody() { return body; }
    public String getLocale() { return locale; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isRead() { return read; }
}
